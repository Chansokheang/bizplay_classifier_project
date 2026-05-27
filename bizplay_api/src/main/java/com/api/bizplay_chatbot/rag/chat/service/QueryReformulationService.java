package com.api.bizplay_chatbot.rag.chat.service;

import com.api.bizplay_chatbot.domain.entity.ChatMessage;
import com.api.bizplay_chatbot.domain.entity.ChatSession;
import com.api.bizplay_chatbot.domain.repository.ChatSessionRepository;
import com.api.bizplay_chatbot.rag.chat.dto.QueryIntent;
import com.api.bizplay_chatbot.rag.chat.dto.ReformulationResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Classifies follow-up queries as either needing document retrieval or
 * answerable from conversation history alone.
 *
 * HISTORY_ONLY is reserved for pure transformations of the previous answer
 * (translate, summarize, reformat, simplify, shorten, repeat). These never
 * need document retrieval — the LLM only operates on what it already said.
 *
 * Everything else routes through RETRIEVE, which includes both document
 * context AND conversation history in the generation prompt. This means
 * referential queries ("explain point 2", "what does X mean") get the best
 * of both worlds — document depth for elaboration plus history for context.
 *
 * Two-tier classification (no LLM call):
 * 1. Regex patterns — catches obvious pure-transform queries
 * 2. Embedding similarity — catches paraphrases of pure-transform queries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryReformulationService {

    private final ChatSessionRepository sessionRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${app.intent.similarity-threshold:0.75}")
    private double similarityThreshold;

    @Value("${app.intent.embedding-classification-enabled:false}")
    private boolean embeddingClassificationEnabled;

    // ─── Tier 1: Pattern matching — pure transforms only ───

    /**
     * Patterns for pure transformations of the previous answer.
     * These NEVER need document retrieval — the LLM only reformats/translates
     * what it already said. Must be self-contained with no specific topic.
     */
    private static final List<Pattern> HISTORY_ONLY_PATTERNS = List.of(
            // ── Pure transforms (translate, summarize, reformat, repeat) ──
            // Referential queries ("explain point 2", "tell me more about X",
            // "위에서 말한 …") are NOT matched here. They route through RETRIEVE
            // so the LLM has both document chunks and conversation history —
            // history alone causes wrong-topic answers when the referential
            // phrasing collides with a new document query.
            // English — translation.
            // \b at the end of the alternation prevents partial matches like
            // "translate its X" matching "translate it" (without the anchor,
            // "it" absorbs the first two chars of "its" and .* eats the rest).
            Pattern.compile("^(please\\s+)?translate\\s+(that|this|it|the above|to\\s+\\w+)\\b.*", Pattern.CASE_INSENSITIVE),
            // English — summarize/shorten
            Pattern.compile("^(please\\s+)?(summarize|summarise|summary)\\s*(that|this|it|the above)?.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(please\\s+)?(make it|make that)\\s+(shorter|simpler|more concise|brief).*", Pattern.CASE_INSENSITIVE),
            // English — simplify. \b for the same reason as translate above —
            // "explain its X" must not be matched as "explain it".
            Pattern.compile("^(please\\s+)?(simplify|explain)\\s+(that|this|it|the above|in simpler|it simpler|more simply)\\b.*", Pattern.CASE_INSENSITIVE),
            // English — reformat
            Pattern.compile("^(please\\s+)?(format|reformat|convert)\\s+(that|this|it|the above)\\s+(as|into|to)\\s+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(please\\s+)?(list|show)\\s+(the\\s+)?(key points|bullet points|main points).*", Pattern.CASE_INSENSITIVE),
            // English — repeat/rephrase
            Pattern.compile("^(please\\s+)?(rephrase|rewrite|say)\\s+(that|this|it)\\s+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(please\\s+)?repeat\\s+(that|this|the above).*", Pattern.CASE_INSENSITIVE),
            // Korean — translation
            Pattern.compile("^(위|위의|이전|이걸|그걸)?\\s*(내용)?\\s*(영어로|한국어로|한글로|일본어로|중국어로)\\s*(번역|변환)?.*"),
            // Korean — summarize/shorten/simplify
            Pattern.compile("^(위|위의|이전)?\\s*(내용)?\\s*(요약|정리)\\s*(해|해줘|해 줘|해주세요)?.*"),
            Pattern.compile("^(더\\s+)?(쉽게|간단하게|간단히|짧게)\\s*(설명|정리|말해|해줘)?.*"),
            // Korean — reformat
            Pattern.compile("^(표로|목록으로|리스트로)\\s*(정리|변환|만들어|바꿔)?.*"),
            Pattern.compile("^(핵심만|요점만|요점\\s*정리).*"),
            Pattern.compile("^(번호\\s*매겨|번호를\\s*매겨).*"),
            // Korean — repeat
            Pattern.compile("^(다시\\s+설명|다시\\s+말해).*")
    );

    // ─── Tier 2: Embedding similarity ───

    /**
     * Canonical HISTORY_ONLY examples. At startup, these are embedded and cached.
     * Pure transforms only — catches paraphrases like "Can you say that in English?".
     * Referential elaboration queries are deliberately excluded so they route
     * through RETRIEVE (see class Javadoc).
     */
    private static final List<String> CANONICAL_HISTORY_ONLY = List.of(
            // English — translation
            "translate that to English",
            "translate this to Korean",
            "can you say that in English",
            "convert that to English please",
            // English — summarize/shorten
            "summarize the above",
            "give me a summary",
            "can you summarize what you said",
            "make it shorter",
            "shorten that",
            "more concise please",
            "same thing but shorter",
            // English — simplify
            "explain that in simpler terms",
            "make it easier to understand",
            "say that again but simpler",
            // English — reformat
            "put that in a table",
            "format as a table",
            "show that as bullet points",
            "list the key points",
            "number those points",
            // English — repeat/rephrase
            "what did you just say",
            "repeat that",
            "say that again",
            "rephrase that",
            "rewrite it",
            // Korean — translation
            "영어로 번역해줘",
            "한국어로 번역해줘",
            "영어로 말해줘",
            // Korean — summarize/shorten/simplify
            "위 내용 요약해줘",
            "요약 정리해줘",
            "더 쉽게 설명해줘",
            "간단하게 정리해줘",
            "더 짧게 해줘",
            // Korean — reformat
            "표로 정리해줘",
            "핵심만 정리해줘",
            "번호 매겨서 정리해줘",
            "목록으로 만들어줘",
            // Korean — repeat
            "다시 설명해줘",
            "위에 거 다시"
    );

    /** Pre-computed embeddings for canonical examples (populated at startup) */
    private float[][] canonicalEmbeddings;

    @PostConstruct
    void initCanonicalEmbeddings() {
        if (!embeddingClassificationEnabled) {
            canonicalEmbeddings = null;
            log.info("Intent embedding classification disabled; using pattern matching only");
            return;
        }

        try {
            List<float[]> embeddings = embeddingModel.embed(CANONICAL_HISTORY_ONLY);
            canonicalEmbeddings = embeddings.toArray(new float[0][]);
            log.info("Initialized {} canonical HISTORY_ONLY embeddings (threshold={})",
                    canonicalEmbeddings.length, similarityThreshold);
        } catch (Exception e) {
            log.error("Failed to initialize canonical embeddings, " +
                    "falling back to pattern matching only: {}", e.getMessage());
            canonicalEmbeddings = null;
        }
    }

    /**
     * Classifies the user's query:
     * 1. Pattern match → catches obvious pure-transform queries (translate, summarize, etc.)
     * 2. Embedding similarity → catches paraphrases of pure-transform queries
     * 3. Default → RETRIEVE (includes both document context AND conversation history)
     *
     * Referential queries ("explain point 2", "what does X mean", "더 자세히 설명해줘")
     * are intentionally routed through RETRIEVE so the LLM has document context for
     * deeper, more accurate answers.
     */
    public ReformulationResult reformulate(UUID sessionId, String query) {
        // No session or empty session → always retrieve
        if (sessionId == null) {
            return new ReformulationResult(QueryIntent.RETRIEVE, query);
        }

        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getMessages().isEmpty()) {
            return new ReformulationResult(QueryIntent.RETRIEVE, query);
        }

        // Tier 1: Pattern match for pure transforms
        if (isPatternMatch(query)) {
            log.info("Query classified (pattern): intent=HISTORY_ONLY, query=\"{}\"", query);
            return new ReformulationResult(QueryIntent.HISTORY_ONLY, query);
        }

        // Tier 2: Embedding similarity for paraphrases of pure transforms
        if (isEmbeddingSimilar(query)) {
            log.info("Query classified (embedding): intent=HISTORY_ONLY, query=\"{}\"", query);
            return new ReformulationResult(QueryIntent.HISTORY_ONLY, query);
        }

        // Default: RETRIEVE — the generation prompt includes conversation history,
        // so the LLM can still reference previous answers when needed.
        log.info("Query classified: intent=RETRIEVE, query=\"{}\"", query);
        return new ReformulationResult(QueryIntent.RETRIEVE, query);
    }

    // ─── Query reformulation for search (LLM-based) ─────────────────────────

    /**
     * Rewrite a referential follow-up into a standalone search query using
     * the session's conversation history. Intended for queries that
     * {@code isReferentialFollowup()} in ChatService has already flagged —
     * calling this on standalone queries wastes an LLM round-trip.
     *
     * Returns {@code null} when:
     *   - there is no session or history to reformulate against
     *   - the LLM returned blank, refused, or produced output that looks like
     *     an answer rather than a query (see {@link #isValidReformulation}).
     * The caller must fall back to a safer strategy (e.g. prepending).
     */
    public String reformulateForSearch(UUID sessionId, String query, ChatClient client) {
        if (sessionId == null || client == null || query == null || query.isBlank()) {
            return null;
        }
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getMessages().isEmpty()) {
            return null;
        }
        String history = buildReformulationHistory(session);
        if (history.isBlank()) {
            return null;
        }

        String raw;
        try {
            // Few-shot examples are load-bearing — they disambiguate "point N"
            // from "제N조" for EXAONE-7.8B. Without them the model interprets
            // "point 8" as "Article 8" and refuses because Article 8 isn't
            // in the conversation.
            raw = client.prompt()
                    .user(u -> u.text("""
                            You rewrite a follow-up question into a standalone search query.

                            Rules:
                            - When the follow-up refers to a numbered item ("point N", "N번째", "the N-th"), look at the ASSISTANT's last answer, count to item N in its list, and include that item's TITLE/NAME in the rewrite. "point N" means the N-th bullet in the prior answer, NOT "제N조" or "Article N".
                            - Include the main topic of the original question (article number, policy name, etc.).
                            - Keep the user's original language (Korean stays Korean; English stays English).
                            - Output ONE short sentence — the rewritten query only. Do NOT output refusals, explanations, preamble, or labels.

                            Example:
                            Conversation history:
                            User: 제3조 용어의 정의에 대해 말씀해 주세요
                            Assistant: 제3조 용어의 정의는 다음과 같습니다:
                            1. 트리플러스(TRIP+): ...
                            2. 마이데이터: ...
                            3. 기타증빙: ...
                            4. 예외거리 허용구간: ...
                            5. 정액 요금 구간: ...
                            6. 일비: ...
                            7. 인보이스: ...
                            8. E-티켓(전자항공권): ...

                            Follow-up question: Please explain point 8 from previous answer
                            Rewritten search query: 제3조 E-티켓(전자항공권) 정의

                            Now rewrite:

                            Conversation history:
                            {history}

                            Follow-up question: {query}

                            Rewritten search query:""")
                            .param("history", history)
                            .param("query", query))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Reformulation LLM call failed, caller should fall back: {}", e.getMessage());
            return null;
        }

        String cleaned = cleanReformulatorOutput(raw);
        if (!isValidReformulation(cleaned, query)) {
            log.info("Reformulation rejected as malformed, caller should fall back. raw=\"{}\"", raw);
            return null;
        }
        log.info("Reformulated \"{}\" → \"{}\"", query, cleaned);
        return cleaned;
    }

    /**
     * Build a compact history block for the reformulator prompt. Shorter than
     * {@code ChatService.getRecentHistory()} — we don't need 4000 chars/msg
     * for a rewrite, and padding bloats the prompt and adds latency.
     */
    private String buildReformulationHistory(ChatSession session) {
        List<ChatMessage> messages = session.getMessages();
        // Last 3 turns = 6 messages (user + assistant).
        int maxMessages = 6;
        if (messages.size() > maxMessages) {
            messages = messages.subList(messages.size() - maxMessages, messages.size());
        }
        // 2000 chars/msg — enough for multi-point answers (e.g. 8 Korean bullets
        // with definitions) to fit. Previously 500, which truncated past item ~4
        // on a long answer and made "point N" references unresolvable.
        int maxCharsPerMessage = 2000;
        return messages.stream()
                .map(m -> {
                    String content = m.getContent() == null ? "" : m.getContent();
                    if (content.length() > maxCharsPerMessage) {
                        content = content.substring(0, maxCharsPerMessage) + "...";
                    }
                    return ("user".equals(m.getRole()) ? "User" : "Assistant") + ": " + content;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Post-process the LLM's raw rewrite output. Small models often echo the
     * label from the prompt ("Rewritten search query: ...") or wrap the output
     * in quotes. Strip those before validation.
     */
    private String cleanReformulatorOutput(String raw) {
        if (raw == null) return null;
        String cleaned = raw.strip();
        // Take only the first non-blank line — models sometimes add an
        // explanation on subsequent lines despite instructions not to.
        int nl = cleaned.indexOf('\n');
        if (nl >= 0) {
            cleaned = cleaned.substring(0, nl).strip();
        }
        // Strip echoed labels the model may prepend.
        cleaned = cleaned.replaceFirst(
                "(?i)^(rewritten\\s+search\\s+query|standalone\\s+search\\s+query|search\\s+query|rewritten\\s+query|query)\\s*[:\\-]\\s*",
                "");
        // Strip matching surrounding ASCII or curly quotes.
        if (cleaned.length() >= 2) {
            char first = cleaned.charAt(0);
            char last = cleaned.charAt(cleaned.length() - 1);
            if ((first == '"' && last == '"')
                    || (first == '\'' && last == '\'')
                    || (first == '“' && last == '”')
                    || (first == '‘' && last == '’')) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
        }
        return cleaned.strip();
    }

    /**
     * Reject rewrites that look like an answer rather than a query, refusals,
     * or obviously broken output. The caller falls back when this returns false.
     */
    private boolean isValidReformulation(String cleaned, String original) {
        if (cleaned == null || cleaned.isBlank()) return false;
        // Rewrite that's 5× longer than the original probably means the LLM
        // answered the question instead of rewriting.
        if (cleaned.length() > Math.max(original.length() * 5, 400)) return false;
        String lower = cleaned.toLowerCase();
        if (lower.startsWith("i cannot") || lower.startsWith("i can't")
                || lower.startsWith("i don't") || lower.startsWith("i'm sorry")
                || lower.startsWith("sorry")
                || cleaned.startsWith("죄송") || cleaned.startsWith("답변")) {
            return false;
        }
        // Reject declarative refusals and meta-statements about the conversation,
        // which EXAONE sometimes emits instead of a rewritten query. These occur
        // mid-string, not as prefixes, so we scan the whole output.
        //   EN: "cannot be found", "not specified", "not mentioned", "not in the context"
        //   KO: "명시되지 않", "언급되지 않", "찾을 수 없", "제공되지 않", "확인되지 않",
        //       "나와 있지 않", "포함되지 않", "정보가 없"
        if (lower.contains("cannot be found") || lower.contains("not specified")
                || lower.contains("not mentioned") || lower.contains("not in the context")
                || lower.contains("not provided") || lower.contains("no information")
                || cleaned.contains("명시되지 않") || cleaned.contains("언급되지 않")
                || cleaned.contains("찾을 수 없") || cleaned.contains("제공되지 않")
                || cleaned.contains("확인되지 않") || cleaned.contains("나와 있지 않")
                || cleaned.contains("포함되지 않") || cleaned.contains("정보가 없")) {
            return false;
        }
        return true;
    }

    private boolean isPatternMatch(String query) {
        String trimmed = query.trim();
        return HISTORY_ONLY_PATTERNS.stream().anyMatch(p -> p.matcher(trimmed).matches());
    }

    /**
     * Embeds the query and compares against cached canonical embeddings.
     * Returns true if cosine similarity with any canonical example exceeds the threshold.
     */
    private boolean isEmbeddingSimilar(String query) {
        if (canonicalEmbeddings == null) {
            return false;
        }

        try {
            float[] queryEmbedding = embeddingModel.embed(query);

            double maxSimilarity = 0.0;
            int bestIdx = -1;
            for (int i = 0; i < canonicalEmbeddings.length; i++) {
                double sim = cosineSimilarity(queryEmbedding, canonicalEmbeddings[i]);
                if (sim > maxSimilarity) {
                    maxSimilarity = sim;
                    bestIdx = i;
                }
            }

            if (maxSimilarity >= similarityThreshold) {
                log.info("Embedding match: query=\"{}\" ≈ \"{}\" (similarity={})",
                        query, CANONICAL_HISTORY_ONLY.get(bestIdx),
                        String.format("%.3f", maxSimilarity));
                return true;
            }
        } catch (Exception e) {
            log.warn("Embedding similarity check failed: {}", e.getMessage());
        }
        return false;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
