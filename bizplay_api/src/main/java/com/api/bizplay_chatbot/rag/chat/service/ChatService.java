package com.api.bizplay_chatbot.rag.chat.service;

import com.api.bizplay_chatbot.bot.service.BotService;
import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.config.BotDefaults;
import com.api.bizplay_chatbot.domain.entity.Bot;
import com.api.bizplay_chatbot.domain.entity.ChatMessage;
import com.api.bizplay_chatbot.domain.entity.ChatSession;
import com.api.bizplay_chatbot.domain.repository.ChatSessionRepository;
import com.api.bizplay_chatbot.rag.chat.dto.ChatRequest;
import com.api.bizplay_chatbot.rag.chat.dto.ChatResponse;
import com.api.bizplay_chatbot.rag.chat.dto.ChatSource;
import com.api.bizplay_chatbot.rag.chat.dto.QueryIntent;
import com.api.bizplay_chatbot.rag.chat.dto.ReformulationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final VectorStore vectorStore;
    private final Map<String, ChatClient> chatClientRegistry;
    private final RerankerService rerankerService;
    private final QueryReformulationService queryReformulationService;
    private final ChatSessionRepository sessionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final BotService botService;
    private final LanguageDetector languageDetector;

    /** Carrier for what we need from the LLM: the answer text plus optional
     *  token-usage figures. Tokens are nullable because vLLM-served models
     *  don't always populate the OpenAI-compat usage block. */
    private record LlmResult(String answer, Integer inputTokens, Integer outputTokens) {}

    @Value("${app.reranker.candidates:20}")
    private int rerankerCandidates;

    @Transactional
    public ChatResponse chat(ChatRequest request) {
        // 0. Bot lookup. Settings (model, temperature, max-tokens, history-turns,
        //    top-K, system prompt, source-expose) are owned by the bot and
        //    cannot be overridden by the request — hence resolved first.
        Bot bot = botService.loadActiveBot(request.getBotId());
        if (bot.isDisabled()) {
            throw new BusinessException("Bot is disabled", HttpStatus.CONFLICT);
        }
        // Validate that any supplied session belongs to THIS bot. Stops a client
        // from smuggling another bot's session into this bot's chat.
        if (request.getSessionId() != null) {
            sessionRepository.findByIdAndBotId(request.getSessionId(), bot.getId())
                    .orElseThrow(() -> new BusinessException(
                            "Session does not belong to bot " + bot.getId(), HttpStatus.BAD_REQUEST));
        }

        ChatClient client = resolveClient(bot);

        // 1. Intent classification: RETRIEVE (needs documents) or HISTORY_ONLY
        //    (translate, summarize, etc.)
        ReformulationResult classification = queryReformulationService.reformulate(
                request.getSessionId(), request.getQuery());

        log.info("Bot: {} | Intent: {} | Query: \"{}\"",
                bot.getId(), classification.getIntent(), request.getQuery());

        if (classification.getIntent() == QueryIntent.HISTORY_ONLY) {
            return handleHistoryOnlyQuery(request, bot, client);
        }

        // 2. Build the search query. For referential follow-ups ("explain
        //    point 2", "위에서 말한 거 더 자세히") rewrite via the LLM (with
        //    prepending fallback) so retrieval gets meaningful topic words.
        String searchQuery;
        if (isReferentialFollowup(request.getQuery())) {
            String rewritten = queryReformulationService.reformulateForSearch(
                    request.getSessionId(), request.getQuery(), client);
            if (rewritten != null) {
                searchQuery = rewritten;
            } else {
                searchQuery = prependLastMeaningfulQuestion(request.getSessionId(), request.getQuery());
                log.info("Reformulation unavailable, using prepending fallback");
            }
        } else {
            searchQuery = request.getQuery();
        }
        log.info("Search query: \"{}\"", searchQuery);

        // 3. Vector search — bot-scoped via filterExpression on metadata.bot_id.
        //    Both ingestion and retrieval use the same string form of the UUID.
        int fetchCount = rerankerService.isEnabled()
                ? Math.max(rerankerCandidates, bot.getTopK())
                : bot.getTopK();

        List<Document> chunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(searchQuery)
                        .topK(fetchCount)
                        .filterExpression("bot_id == '" + bot.getId() + "'")
                        .build());

        if (chunks.isEmpty()) {
            return handleNoDocumentsQuery(request, bot, client);
        }

        // 4. Optional rerank
        if (rerankerService.isEnabled()) {
            log.info("Reranking {} candidates down to top {}", chunks.size(), bot.getTopK());
            chunks = rerankerService.rerank(searchQuery, chunks, bot.getTopK());
        }

        // 5. Build context + history (history must precede persistMessages).
        String context = chunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        String history = getRecentHistory(request.getSessionId(), bot.getHistoryTurns());

        // 6. LLM generation. System prompt + LLM options come from the bot —
        //    these override any defaults baked into the ChatClient bean.
        log.info("Using model: {}", bot.getLlmModel());
        // chatResponse() (instead of .content()) so we can extract token
        // usage from the metadata block for the analytics dashboard.
        String langLine = containsKorean(request.getQuery())
                ? "반드시 한국어로 답변하세요."
                : "Respond in English.";

        org.springframework.ai.chat.model.ChatResponse response = client.prompt()
                .system(systemPromptOf(bot))
                .options(buildOptions(bot))
                .user(u -> u.text("""
                        Context documents (from the knowledge base):
                        {context}

                        Conversation history (prior turns in this chat):
                        {history}

                        {lang_line}
                        Question: {question}
                        """)
                        .param("context", context)
                        .param("history", history.isEmpty() ? "(none)" : history)
                        .param("lang_line", langLine)
                        .param("question", request.getQuery()))
                .call()
                .chatResponse();

        LlmResult llm = toLlmResult(response, request.getQuery());

        // Suppress sources when the LLM couldn't answer
        boolean llmCannotAnswer = llm.answer().contains("I don't have enough information to answer")
                || llm.answer().equals("I was unable to generate a response. Please try again.");

        // 7. Persist + audit
        UUID sessionId = persistMessages(request, bot, llm);
        String answer = llm.answer();

        // 8. Sources — only if the bot exposes them AND the LLM actually answered.
        // Cap at the single highest-relevance chunk regardless of bot.topK:
        // topK still drives how many chunks the LLM gets in its context block,
        // we only narrow the user-visible sources list to one row.
        List<ChatSource> sources = (llmCannotAnswer || !bot.isSourceExpose())
                ? List.of()
                : chunks.stream().limit(1).map(this::toSource).toList();

        logAudit(sessionId, bot.getId(), request.getQuery(), chunks);

        return ChatResponse.builder()
                .answer(answer)
                .sessionId(sessionId)
                .sources(sources)
                .build();
    }

    /**
     * Empty-retrieval path — vector search returned no chunks for the bot
     * (either the bot has no documents at all, or none are remotely relevant
     * to this query). The LLM is still invoked so it can produce a refusal
     * shaped by the bot's own system prompt — operators who customise the
     * system prompt with rules like "always reply in formal Korean with
     * bullet points" or "always sign off with [회사명]" expect those rules
     * to apply here too, not just on docs-grounded answers.
     *
     * The user template below is intentionally minimal and informational:
     * state the situation, defer to the system prompt for HOW to respond,
     * and add only the universal anti-hallucination guardrail. Tone,
     * language, format, and any custom commands all come from the bot's
     * configured system prompt.
     *
     * The user message and assistant response are persisted and audited like
     * any other turn so multi-turn history stays coherent (e.g. the user's
     * follow-up "translate that to Korean" still works after a no-docs turn).
     */
    private ChatResponse handleNoDocumentsQuery(ChatRequest request, Bot bot, ChatClient client) {
        // No langLine here. When operators configure a system prompt with both
        // a fixed refusal phrase ("답변이 불가한 질문에 대해서는 \"X\"라고 답변하세요")
        // and a language rule ("Korean by default, English when asked in
        // English"), our own language directive dilutes the specific refusal
        // phrase — the LLM ends up generating a generic polite apology
        // instead of emitting the exact configured string. By staying silent
        // on language we let the system prompt's rules apply as written.
        // Default-prompt bots fall back to whatever DEFAULT_SYSTEM_PROMPT
        // dictates (currently the canonical "I don't have enough information"
        // phrase); operators who want different default behaviour edit that
        // prompt.
        org.springframework.ai.chat.model.ChatResponse response = client.prompt()
                .system(systemPromptOf(bot))
                .options(buildOptions(bot))
                .user(u -> u.text("""
                        The question below cannot be answered from the \
                        knowledge base — no relevant documents were found. \
                        Treat this as an unanswerable question and apply \
                        your role's instructions for that case, using any \
                        specific refusal phrasing and language rules \
                        exactly as they were given to you. Do not invent \
                        facts, do not speculate, and do not answer from \
                        general knowledge outside the provided context.

                        Question: {question}
                        """)
                        .param("question", request.getQuery()))
                .call()
                .chatResponse();

        LlmResult llm = toLlmResult(response, request.getQuery());

        UUID sessionId = persistMessages(request, bot, llm);
        // accessed_doc_ids is empty by definition on this path.
        logAudit(sessionId, bot.getId(), request.getQuery(), List.of());

        return ChatResponse.builder()
                .answer(llm.answer())
                .sessionId(sessionId)
                .sources(List.of())
                .build();
    }

    /**
     * Pure-transform path (translate / summarize / reformat / shorten / repeat).
     * Skips retrieval; uses only conversation history. The prompt frames the
     * user's input as a Request to apply to the prior assistant answer.
     */
    private ChatResponse handleHistoryOnlyQuery(ChatRequest request, Bot bot, ChatClient client) {
        String history = getRecentHistory(request.getSessionId(), bot.getHistoryTurns());

        if (history.isEmpty()) {
            log.warn("HISTORY_ONLY intent but no history available");
            UUID sessionId = resolveSessionId(request.getSessionId(), bot);
            return ChatResponse.builder()
                    .answer("I don't have any previous conversation to reference. Please ask a question about the documents.")
                    .sessionId(sessionId)
                    .sources(List.of())
                    .build();
        }

        // Dedicated framing: this is an operation on the prior answer, not a
        // question to answer literally. No langLine — translation requests set
        // their own target language.
        org.springframework.ai.chat.model.ChatResponse response = client.prompt()
                .system(systemPromptOf(bot))
                .options(buildOptions(bot))
                .user(u -> u.text("""
                        Prior conversation (the Assistant's last answer is the material to operate on):
                        {history}

                        The user's Request below is a transformation to apply to the Assistant's \
                        previous answer above — for example translate, summarize, shorten, \
                        reformat, explain a specific part. Carry out the Request using the \
                        prior answer as your source material. Do NOT echo the Request back; \
                        execute it.

                        Request: {question}
                        """)
                        .param("history", history)
                        .param("question", request.getQuery()))
                .call()
                .chatResponse();

        LlmResult llm = toLlmResult(response, request.getQuery());

        UUID sessionId = persistMessages(request, bot, llm);
        logAudit(sessionId, bot.getId(), request.getQuery(), List.of());

        return ChatResponse.builder()
                .answer(llm.answer())
                .sessionId(sessionId)
                .sources(List.of())
                .build();
    }

    /** Resolve the system prompt for a chat call. The bot's column allows
     *  nulls, so we fall back to {@link BotDefaults#DEFAULT_SYSTEM_PROMPT}
     *  when the bot has none configured — every chat call still gets a
     *  reasonable system instruction. */
    private String systemPromptOf(Bot bot) {
        String prompt = bot.getSystemPrompt();
        return (prompt == null || prompt.isBlank()) ? BotDefaults.DEFAULT_SYSTEM_PROMPT : prompt;
    }

    /** Per-call LLM options sourced from the bot. Overrides any defaults baked
     *  into the ChatClient bean (temperature was previously fixed at 0). */
    private ChatOptions buildOptions(Bot bot) {
        return ChatOptions.builder()
                .temperature(bot.getLlmTemperature().doubleValue())
                .maxTokens(bot.getMaxAnswerLength())
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(UUID sessionId) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return List.of();
        }
        return session.getMessages().stream()
                .map(msg -> Map.<String, Object>of(
                        "role", msg.getRole(),
                        "content", msg.getContent(),
                        "createdAt", msg.getCreatedAt().toString()))
                .toList();
    }

    /**
     * Resolve or create a session bound to the bot, then persist the user query
     * and assistant answer. Carries through the analytics-dashboard fields
     * (channel on the session, lang on each message, token counts on the
     * assistant message).
     *
     * Channel is taken from the request only when CREATING a session; an
     * existing session keeps the channel it was originally created with so a
     * later message from a different channel can't rewrite history.
     */
    private UUID persistMessages(ChatRequest request, Bot bot, LlmResult llm) {
        String channel = (request.getChannel() != null && !request.getChannel().isBlank())
                ? request.getChannel()
                : "web";

        ChatSession session;
        if (request.getSessionId() != null) {
            // Existing session was already bot-validated at the top of chat().
            session = sessionRepository.findById(request.getSessionId())
                    .orElseGet(() -> createSession(bot, channel));
        } else {
            session = createSession(bot, channel);
        }

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSession(session);
        userMsg.setRole("user");
        userMsg.setContent(request.getQuery());
        userMsg.setLang(languageDetector.detect(request.getQuery()));
        session.getMessages().add(userMsg);

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSession(session);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(llm.answer());
        // Assistant answer is usually in the user's language for this bot —
        // detect from the actual content to be safe.
        assistantMsg.setLang(languageDetector.detect(llm.answer()));
        assistantMsg.setInputTokens(llm.inputTokens());
        assistantMsg.setOutputTokens(llm.outputTokens());
        session.getMessages().add(assistantMsg);

        sessionRepository.save(session);
        return session.getId();
    }

    private UUID resolveSessionId(UUID requestSessionId, Bot bot) {
        if (requestSessionId != null && sessionRepository.existsById(requestSessionId)) {
            return requestSessionId;
        }
        return createSession(bot, "web").getId();
    }

    private ChatSession createSession(Bot bot, String channel) {
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setBot(bot);
        session.setChannel(channel);
        return sessionRepository.save(session);
    }

    /**
     * Pull the answer text + token counts out of a Spring AI ChatResponse,
     * defensive against a null/empty response or a model that doesn't
     * populate the usage block (vLLM omits it for some configurations).
     * The returned answer is never null/blank — falls back to a fixed string
     * so persistence and downstream "I couldn't answer" detection still work.
     */
    private static LlmResult toLlmResult(org.springframework.ai.chat.model.ChatResponse response, String query) {
        String answer = null;
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            answer = response.getResult().getOutput().getText();
        }
        if (answer == null || answer.isBlank()) {
            log.warn("LLM returned null/empty content for query: {}", query);
            answer = "I was unable to generate a response. Please try again.";
        }

        Integer inputTokens = null;
        Integer outputTokens = null;
        if (response != null && response.getMetadata() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                inputTokens  = toInt(usage.getPromptTokens());
                outputTokens = toInt(usage.getCompletionTokens());
            }
        }
        return new LlmResult(answer, inputTokens, outputTokens);
    }

    /** Defensive Number → Integer narrow. Spring AI's Usage returns Integer
     *  in 1.0, but other provider-specific subclasses sometimes use Long. */
    private static Integer toInt(Object n) {
        if (n instanceof Number num) return num.intValue();
        return null;
    }

    /**
     * Load recent conversation turns for the LLM generation prompt.
     * Each message is truncated so a long answer doesn't blow up the
     * context window on smaller models.
     */
    private String getRecentHistory(UUID sessionId, int maxTurns) {
        if (sessionId == null) {
            return "";
        }
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getMessages().isEmpty()) {
            return "";
        }

        List<ChatMessage> messages = session.getMessages();
        int maxMessages = maxTurns * 2; // each turn = user + assistant
        if (messages.size() > maxMessages) {
            messages = messages.subList(messages.size() - maxMessages, messages.size());
        }

        // Allow enough chars per message so the LLM sees full previous answers.
        // Truncated history causes incomplete translations and missed context.
        int maxCharsPerMessage = 4000;
        return messages.stream()
                .map(m -> {
                    String content = m.getContent();
                    if (content.length() > maxCharsPerMessage) {
                        content = content.substring(0, maxCharsPerMessage) + "...";
                    }
                    return (m.getRole().equals("user") ? "User" : "Assistant") + ": " + content;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Resolve the ChatClient for the bot's configured llm_model. The model is
     * validated at bot create/update time, so a missing entry here is a
     * configuration drift (model removed from .env after the bot was made).
     */
    private ChatClient resolveClient(Bot bot) {
        ChatClient client = chatClientRegistry.get(bot.getLlmModel());
        if (client == null) {
            throw new BusinessException(
                    "Bot " + bot.getId() + " is configured with unknown LLM model '"
                            + bot.getLlmModel() + "'. Available: " + chatClientRegistry.keySet(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return client;
    }

    /**
     * Detect queries that depend on the previous turn for their topic — e.g.
     * "explain point 2", "위에서 말한 거 더 자세히". These need topic context
     * pulled from history, otherwise retrieval returns unrelated chunks.
     */
    private boolean isReferentialFollowup(String query) {
        String q = query.trim();
        String lower = q.toLowerCase();

        // Strong back-references — match at any length.
        if (lower.matches(".*\\b(previous|last|above|prior|earlier)\\s+(answer|response|reply|explanation|message|turn)\\b.*")
                || lower.matches(".*\\byou\\s+(said|mentioned|stated|explained|listed|wrote|described)\\b.*")
                || lower.matches(".*\\bwhat\\s+you\\s+(just\\s+)?said\\b.*")
                || q.matches(".*(위에서|아까|이전에|방금|앞에서)\\s*(말한|언급한|설명한|나온|말씀한).*")
                || q.matches(".*(이전|앞의|위의|전)\\s*(답변|답|응답|대답).*")) {
            return true;
        }

        // Possessive pronouns + noun ("its X", "그것의 특징") — implicit
        // back-reference to the prior turn's subject. Strong (no length gate).
        if (lower.matches(".*\\b(its|their|his|her|theirs|hers)\\s+\\S+.*")
                || q.matches(".*(그의|그것의|그녀의|이것의|저것의|그들의)\\s+\\S+.*")) {
            return true;
        }

        // Weaker markers count only on short queries — standalone new-topic
        // queries tend to be substantially longer.
        if (q.length() < 40) {
            if (lower.matches(".*\\b(point|item|bullet|step|option|reason|part)\\s*(#|number|no\\.?)?\\s*\\d+.*")) return true;
            if (lower.matches(".*\\b(first|second|third|fourth|fifth|last)\\s+(point|item|one|bullet|step|option|reason|part|thing)\\b.*")) return true;
            if (lower.matches(".*\\b\\d+(st|nd|rd|th)\\s+(point|item|one|bullet|step|option|reason|part|thing)\\b.*")) return true;
            if (lower.matches(".*\\b(elaborate|expand|tell me more|go deeper)\\b.*")) return true;
            if (lower.matches(".*\\b(it|them|that|this|those|these)\\b.*")) return true;
            if (q.matches(".*(포인트|단계|항목|옵션|이유|부분)\\s*\\d+.*")) return true;
            if (q.matches(".*\\d+\\s*(포인트|단계|항목|옵션|이유|부분)\\b.*")) return true;
            if (q.matches(".*\\d+번째.*")) return true;
            if (q.matches(".*(첫|둘|두|세|네|다섯|여섯|마지막)\\s*번째?.*")) return true;
            if (q.matches(".*(더|좀더|좀 더)\\s*(자세히|자세하게|상세히|구체적으로).*")) return true;
        }
        return false;
    }

    /**
     * Walk back through the session to find the last user message that wasn't
     * itself a referential follow-up, then prepend it (with a sentence
     * terminator) to the current query. Truncate to 200 chars so the prepended
     * text doesn't dilute the embedding signal.
     */
    private String prependLastMeaningfulQuestion(UUID sessionId, String currentQuery) {
        if (sessionId == null) return currentQuery;
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getMessages().isEmpty()) return currentQuery;

        List<ChatMessage> messages = session.getMessages();
        String lastMeaningful = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (!"user".equals(m.getRole())) continue;
            String content = m.getContent();
            if (content == null || content.isBlank()) continue;
            if (isReferentialFollowup(content)) continue;
            lastMeaningful = content;
            break;
        }

        if (lastMeaningful == null) return currentQuery;
        if (lastMeaningful.length() > 200) {
            lastMeaningful = lastMeaningful.substring(0, 200);
        }
        // Ensure a sentence terminator so the combined query reads as two
        // sentences to the embedding model, not a run-on. Truncation may have
        // removed the original; re-check.
        lastMeaningful = lastMeaningful.stripTrailing();
        char last = lastMeaningful.isEmpty() ? '\0' : lastMeaningful.charAt(lastMeaningful.length() - 1);
        if (last != '.' && last != '?' && last != '!' && last != '。' && last != '？' && last != '！') {
            lastMeaningful = lastMeaningful + ".";
        }
        return lastMeaningful + " " + currentQuery;
    }

    /** Korean character detection for the inline language directive. */
    private boolean containsKorean(String text) {
        return text.codePoints().anyMatch(cp ->
                (cp >= 0xAC00 && cp <= 0xD7AF)
             || (cp >= 0x1100 && cp <= 0x11FF)
             || (cp >= 0x3130 && cp <= 0x318F));
    }

    private ChatSource toSource(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String docId = meta.get("doc_id") instanceof String s ? s : "";
        String snippet = doc.getText();
        if (snippet != null && snippet.length() > 300) {
            snippet = snippet.substring(0, 300) + "...";
        }

        // Reranker score (0–1, higher = more relevant) when available, else
        // 1 - cosine distance.
        double score;
        if (meta.get("rerank_score") instanceof Number n) {
            score = n.doubleValue();
        } else if (meta.get("distance") instanceof Number n) {
            score = 1.0 - n.doubleValue();
        } else {
            score = 0.0;
        }

        int chunkIndex = meta.get("chunk_index") instanceof Number n ? n.intValue() : 0;

        return ChatSource.builder()
                .docId(docId)
                .title(meta.get("title") instanceof String s ? s : "")
                .fileName(meta.get("file_name") instanceof String s ? s : "")
                .snippet(snippet)
                .score(score)
                .chunkIndex(chunkIndex)
                .documentUrl(!docId.isEmpty()
                        ? "/chatbot/api/v1/rag/documents/" + docId + "/download"
                        : null)
                .build();
    }

    /** Append-only audit log; failures never block the chat response. */
    private void logAudit(UUID sessionId, UUID botId, String query, List<Document> chunks) {
        try {
            String docIds = chunks.stream()
                    .map(c -> (String) c.getMetadata().getOrDefault("doc_id", ""))
                    .distinct()
                    .collect(Collectors.joining(","));

            jdbcTemplate.update(
                    "INSERT INTO audit_logs (session_id, bot_id, query, accessed_doc_ids, created_at) " +
                            "VALUES (?, ?, ?, ?, NOW())",
                    sessionId, botId, query, docIds);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }
}
