package com.api.bizplay_chatbot.bot.service;

import com.api.bizplay_chatbot.bot.dto.KeywordStat;
import com.api.bizplay_chatbot.domain.entity.Bot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LLM-based topic extractor for the analytics dashboard's "Popular keywords"
 * widget.
 *
 * <p>Why LLM and not naive frequency counting (the previous v1 approach):
 * naive whitespace tokenization can't produce multi-word phrases like
 * <em>"business trip"</em> or <em>"travel expense"</em>, and however large the
 * stopword list grows it still can't reliably distinguish a domain-specific
 * topic from a generic verb. The model handles both — it understands phrase
 * structure and what counts as a topic in this domain.
 *
 * <p><b>One LLM call per request.</b> The model selected is the bot's own
 * {@link Bot#getLlmModel()} (so the dashboard inherits the bot's chat client
 * configuration without a separate plumbing path). Returns an empty list on
 * any failure — invalid JSON, model timeout, missing client — so the
 * dashboard degrades gracefully rather than 500-ing.
 *
 * <p><b>Corpus size cap.</b> User messages in the window are concatenated up
 * to ~30k characters (~7k tokens) to stay well inside EXAONE's 32k context
 * window with room for the prompt and response. If the window has more
 * traffic, the most recent messages are kept (the {@code ORDER BY createdAt
 * DESC} on the projection query feeds them in newest-first).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordExtractor {

    private final Map<String, ChatClient> chatClientRegistry;
    private final ObjectMapper objectMapper;

    /** Conservative cap on the corpus we pass to the LLM. ~30k chars ≈ 7k
     *  tokens for mixed Korean/English text, leaving room for the prompt
     *  (~500 tokens) and output (~500 tokens) inside an 8k+ context window. */
    private static final int MAX_CORPUS_CHARS = 30_000;

    private static final String SYSTEM_PROMPT = """
            You are a strict topic-extraction tool for a chatbot analytics dashboard.

            Your ONLY job: read the user chat messages provided in the next turn and \
            list the most frequent topics that LITERALLY appear in those messages.

            HARD RULES (no exceptions):
            1. Only output topics that appear, verbatim or as a near-synonym/inflection, \
               in the messages provided below. NEVER output a topic from your training \
               data, general knowledge, or what the bot "should" be about. If the \
               messages don't talk about it, it is not a topic.
            2. If the messages contain fewer distinct topics than the requested limit, \
               return the smaller list. Do NOT pad the array to hit the limit. Quality \
               over quantity.
            3. EXCLUDE: generic verbs (give/take/want/need/get/have/do/make/tell/ask/ \
               show/use/let/know), courtesy words (please/thanks/hello/sorry), pronouns, \
               conversational filler (how/what/why/어떻게/뭐), single common adjectives, \
               and Korean particles or postpositions on their own.
            4. Each topic must be a 1-3 word noun or noun phrase.
            5. Return topics in the SAME language they appear in. Korean messages → \
               Korean topics. English messages → English topics. Don't translate.
            6. Output ONLY a JSON array. No markdown fences, no prose, no commentary.
            """;

    /**
     * Note the {@code ##CORPUS##} / {@code ##LIMIT##} sentinels rather than
     * Spring AI's standard {@code {corpus}} / {@code {limit}} placeholders.
     * We do the substitution ourselves and bypass {@code PromptTemplate}'s
     * StringTemplate engine (which uses {@code { }} as delimiters) so the
     * literal JSON example below — {@code [{"keyword": "string", ...}]} —
     * isn't misparsed as an invalid template parameter named
     * {@code "keyword": "string", ...}. Without this workaround, Spring AI
     * raises {@code IllegalArgumentException: "The template string is not valid."}
     * before the LLM is ever called.
     */
    private static final String USER_PROMPT_TEMPLATE = """
            User messages from a chatbot (separated by `---`):

            ##CORPUS##

            ---

            Task: list the topics that ACTUALLY APPEAR in the messages above, up to \
            ##LIMIT## entries. For each, set `count` to the number of distinct messages \
            that mention it (a synonym or inflection in the same language counts — \
            e.g. "비용을", "비용이", "비용은" all roll up to "비용").

            FORMAT-ONLY EXAMPLES (these illustrate the output shape only — do NOT \
            copy these strings into your answer unless those exact words appear in \
            the messages above):
              English: "API key", "login flow", "invoice"
              Korean:  "결제 방법", "주문 내역", "환불 신청"

            REMINDERS:
              - Topics must come from the messages above. If you can't quote it, \
                don't list it.
              - If fewer than ##LIMIT## topics genuinely appear, return fewer. Do not \
                pad with invented or generic topics.
              - Keep the language of each topic the same as in the source message.

            Output: a JSON array sorted by count descending. Shape:
            [{"keyword": "string", "count": integer}, ...]
            """;

    /**
     * Extract the top-{@code limit} domain-specific keywords from the bot's
     * recent user messages. Returns an empty list rather than throwing when
     * the LLM call or response parse fails — keyword extraction is a
     * dashboard nicety, not a critical-path operation.
     */
    public List<KeywordStat> extractTopKeywords(Bot bot, List<String> userMessages, int limit) {
        if (userMessages == null || userMessages.isEmpty()) return List.of();

        ChatClient client = chatClientRegistry.get(bot.getLlmModel());
        if (client == null) {
            // Fall back to whichever client is registered. The map is non-empty
            // at boot time (SpringAiConfig validates that), so .iterator().next()
            // is safe.
            log.warn("No ChatClient registered for model '{}', falling back to default",
                    bot.getLlmModel());
            client = chatClientRegistry.values().iterator().next();
        }

        String corpus = buildCorpus(userMessages);
        if (corpus.isBlank()) return List.of();

        // Substitute ourselves so the engine never sees the literal JSON example.
        String userText = USER_PROMPT_TEMPLATE
                .replace("##CORPUS##", corpus)
                .replace("##LIMIT##", String.valueOf(limit));

        // Use the lower-level Prompt(messages) API rather than the fluent
        // .system()/.user() builders. The builders route through PromptTemplate
        // (StringTemplate-based, {} delimited) which would still re-parse our
        // pre-rendered text and choke on the literal `[{"keyword": ...}]`.
        // A Prompt holding pre-built Message objects skips that pass entirely.
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(userText)
        ));

        // Pin temperature to 0 for keyword extraction — without this we inherit
        // the bot's chat-time temperature, which can be 0.7+ on bots tuned for
        // creative answers. Non-zero temperature here causes the model to
        // hallucinate plausible-but-absent topics from training data ("travel",
        // "policy", etc.) when the actual corpus is small or narrow.
        ChatOptions extractionOptions = ChatOptions.builder()
                .temperature(0.0)
                .build();

        String response;
        try {
            response = client.prompt(prompt)
                    .options(extractionOptions)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Keyword extraction LLM call failed for bot {}: {}", bot.getId(), e.getMessage());
            return List.of();
        }

        return parseKeywords(response, limit);
    }

    /** Concatenate user messages into a single newline-separated corpus,
     *  truncating once we exceed {@link #MAX_CORPUS_CHARS}. The repository
     *  query orders by {@code createdAt DESC} so truncation drops the oldest
     *  content first. */
    private static String buildCorpus(List<String> messages) {
        StringBuilder sb = new StringBuilder();
        for (String m : messages) {
            if (m == null || m.isBlank()) continue;
            String trimmed = m.trim();
            if (sb.length() + trimmed.length() + 5 > MAX_CORPUS_CHARS) break;
            sb.append(trimmed).append("\n---\n");
        }
        return sb.toString();
    }

    /** Parse the LLM's JSON output. Handles markdown code fences and stray
     *  prose around the array — both common in real-world LLM outputs even
     *  with explicit "JSON only" instructions. */
    private List<KeywordStat> parseKeywords(String response, int limit) {
        if (response == null || response.isBlank()) return List.of();

        String json = response.trim();

        // Strip markdown code fences (```json … ``` or ``` … ```)
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // If there's prose before/after the array, scope to the bracket span.
        int arrayStart = json.indexOf('[');
        int arrayEnd   = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            log.warn("LLM keyword response had no JSON array. Raw: {}",
                    response.length() > 300 ? response.substring(0, 300) + "…" : response);
            return List.of();
        }
        json = json.substring(arrayStart, arrayEnd + 1);

        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    json, new TypeReference<>() {});
            return raw.stream()
                    .filter(m -> m.get("keyword") != null && m.get("count") != null)
                    .map(KeywordExtractor::toStat)
                    .filter(k -> !k.getKeyword().isBlank())
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse keyword JSON: {}. Raw: {}", e.getMessage(),
                    response.length() > 300 ? response.substring(0, 300) + "…" : response);
            return List.of();
        }
    }

    private static KeywordStat toStat(Map<String, Object> m) {
        Object keyword = m.get("keyword");
        Object count = m.get("count");
        long c = (count instanceof Number num) ? num.longValue() : 0L;
        return KeywordStat.builder()
                .keyword(String.valueOf(keyword).trim())
                .count(Math.max(c, 0L))
                .build();
    }
}
