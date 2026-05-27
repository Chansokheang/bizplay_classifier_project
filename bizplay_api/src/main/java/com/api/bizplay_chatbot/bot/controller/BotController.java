package com.api.bizplay_chatbot.bot.controller;

import com.api.bizplay_chatbot.bot.dto.*;
import com.api.bizplay_chatbot.bot.service.BotService;
import com.api.bizplay_chatbot.bot.service.SystemPromptGenerator;
import com.api.bizplay_chatbot.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "Bots", description = "Manage chatbots — each bot has its own purpose, documents, prompts and LLM settings")
@RestController
@RequestMapping("/api/v1/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotService botService;
    private final SystemPromptGenerator systemPromptGenerator;

    @Operation(summary = "Generate a system prompt from a bot's name and description",
            description = "Stateless utility — does NOT create a bot. Calls the configured (or supplied) "
                    + "LLM with a meta-prompt that produces a tailored system prompt following the project's "
                    + "three-step source priority (context → history → refuse). Use this as a 'Generate' "
                    + "button on a bot-creation form; the returned text can be edited before being sent to "
                    + "POST /chatbot/api/v1/bots in the systemPrompt field. Falls back to the static default prompt "
                    + "if the LLM call fails or returns blank.")
    @PostMapping("/generate-system-prompt")
    public ResponseEntity<ApiResponse<SystemPromptGenerationResponse>> generateSystemPrompt(
            @Valid @RequestBody SystemPromptGenerationRequest req) {
        log.info("POST /chatbot/api/v1/bots/generate-system-prompt - name=\"{}\", model={}",
                req.getName(), req.getLlmModel());
        return ResponseEntity.ok(ApiResponse.ok(systemPromptGenerator.generate(req)));
    }

    @Operation(summary = "Create a new bot",
            description = "Creates a bot. corpNo is the corp's natural business code, a soft reference to "
                    + "a corporation managed by the external login service — any value is accepted (no "
                    + "local existence check). When omitted, corpNo defaults to the configured default "
                    + "tenant (DEFAULT). llmModel must match a name from GET /chatbot/api/v1/rag/chat/models. "
                    + "llmTemperature is 0.0–1.0 (default 0); historyTurns and topK default to 5; "
                    + "maxAnswerLength is the LLM's maxTokens (default 1024). systemPrompt is optional — "
                    + "when omitted, the static default prompt is applied. recommendedQuestions[] can be "
                    + "seeded inline. "
                    + "Newly-created bots start disabled — call PATCH /chatbot/api/v1/bots/{id}/enable once setup "
                    + "(documents, system prompt, Telegram link, etc.) is complete.")
    @PostMapping
    public ResponseEntity<ApiResponse<BotResponse>> create(@Valid @RequestBody BotCreateRequest req) {
        log.info("POST /chatbot/api/v1/bots - name=\"{}\", model={}", req.getName(), req.getLlmModel());
        return ResponseEntity.ok(ApiResponse.ok(botService.create(req)));
    }

    @Operation(summary = "List bots",
            description = "Returns compact summaries for every bot in the system, regardless of corp_id, "
                    + "including disabled bots (with isDisabled=true). Use GET /chatbot/api/v1/bots/by-corp/{corpId} "
                    + "to filter by a specific corporation.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BotSummary>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(botService.list()));
    }

    @Operation(summary = "List bots for a specific corporation",
            description = "Returns compact summaries for every bot referencing the given corp_no (including "
                    + "disabled bots). Use this when an admin / cross-tenant view needs to filter the full "
                    + "bot list by a single corporation rather than calling GET /chatbot/api/v1/bots and filtering "
                    + "client-side. corp_no is a soft reference to data owned by the external login "
                    + "service, so unknown codes return an empty list rather than 404.")
    @GetMapping("/by-corp/{corpNo}")
    public ResponseEntity<ApiResponse<List<BotSummary>>> listByCorp(
            @Parameter(description = "Corporation natural business code (corp_no)") @PathVariable String corpNo) {
        return ResponseEntity.ok(ApiResponse.ok(botService.listByCorpNo(corpNo)));
    }

    @Operation(summary = "Get a bot's full configuration",
            description = "Returns every field plus the bot's recommended questions.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BotResponse>> get(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(botService.get(id)));
    }

    @Operation(summary = "Get aggregate statistics for a bot",
            description = "Returns counters covering ingested documents (total + per embedding status), "
                    + "chat sessions, persisted messages, conversation turns (user-side messages), "
                    + "recommended questions, and the timestamp of the most recent chat activity. "
                    + "Pass `?windowDays=7` (or 30, 90, etc.) to additionally receive `windowConversationCount`, "
                    + "`windowMessageCount`, and the same metrics for the previous comparable window — useful "
                    + "for the analytics dashboard's KPI cards with their \"Up X% compared to the previous "
                    + "period\" deltas. Without `windowDays`, only lifetime totals are returned.")
    @GetMapping("/{id}/statistics")
    public ResponseEntity<ApiResponse<BotStatistics>> statistics(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Parameter(description = "Optional window size in days (e.g. 7, 30, 90). When provided, the "
                    + "response includes per-window counts and previous-window counts.")
            @RequestParam(value = "windowDays", required = false) Integer windowDays) {
        return ResponseEntity.ok(ApiResponse.ok(botService.getStatistics(id, windowDays)));
    }

    @Operation(summary = "Daily activity series for a bot's analytics dashboard",
            description = "Returns one bucket per calendar day inside the requested window "
                    + "(zero-filled — days with no activity still appear with `conversationCount: 0`, "
                    + "`messageCount: 0`). Drives the \"Daily conversation count\" bar chart. The window "
                    + "is [now − windowDays, now). Returns 404 if the bot does not exist; 400 if "
                    + "windowDays is missing or non-positive.")
    @GetMapping("/{id}/statistics/daily")
    public ResponseEntity<ApiResponse<List<DailyStat>>> statisticsDaily(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Parameter(description = "Window size in days (e.g. 7, 30, 90). Required.")
            @RequestParam("windowDays") int windowDays) {
        return ResponseEntity.ok(ApiResponse.ok(botService.getDailyStatistics(id, windowDays)));
    }

    @Operation(summary = "Popular keywords for a bot's analytics dashboard",
            description = "Returns the top-`limit` most frequent keywords across user messages whose "
                    + "`createdAt` falls in [now − windowDays, now). Sorted by count desc, then "
                    + "alphabetically for tie-breaking. Tokens with frequency < 2 are dropped — a single "
                    + "mention is noise, not a \"popular\" keyword. v1 produces single-word keywords "
                    + "only; multi-word phrases (e.g. \"Business trip request\") need an LLM "
                    + "topic-extraction pass which is on the roadmap. Returns 404 if the bot does not "
                    + "exist; 400 if windowDays ≤ 0 or limit not in [1, 100].")
    @GetMapping("/{id}/statistics/keywords")
    public ResponseEntity<ApiResponse<List<KeywordStat>>> statisticsKeywords(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Parameter(description = "Window size in days (e.g. 7, 30, 90). Required.")
            @RequestParam("windowDays") int windowDays,
            @Parameter(description = "How many keywords to return (1-100). Defaults to 10.")
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(botService.getKeywordStatistics(id, windowDays, limit)));
    }

    @Operation(summary = "List all chat sessions for a bot",
            description = "Returns every chat session belonging to the given bot, ordered newest first. "
                    + "Each entry carries the session ID, when it was created, the timestamp of the most "
                    + "recent message, and total message count (user + assistant). To inspect a single "
                    + "session's full transcript, follow up with GET /chatbot/api/v1/rag/chat/history/{sessionId}. "
                    + "Returns 404 if the bot does not exist; an empty array (200) if the bot exists but "
                    + "has no sessions yet.")
    @GetMapping("/{id}/sessions")
    public ResponseEntity<ApiResponse<List<ChatSessionSummary>>> listSessions(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(botService.listSessions(id)));
    }

    @Operation(summary = "Update a bot",
            description = "PATCH semantics: omitted fields are left unchanged. When recommendedQuestions is "
                    + "provided (even as an empty list) the entire collection is replaced atomically; pass null to "
                    + "leave existing questions untouched.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BotResponse>> update(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Valid @RequestBody BotUpdateRequest req) {
        log.info("PUT /chatbot/api/v1/bots/{}", id);
        return ResponseEntity.ok(ApiResponse.ok(botService.update(id, req)));
    }

    @Operation(summary = "Disable a bot",
            description = "Bot is preserved but new chat calls return 409. Documents and history remain.")
    @PatchMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        botService.disable(id);
        return ResponseEntity.ok(ApiResponse.ok("Bot disabled", null));
    }

    @Operation(summary = "Enable a previously-disabled bot")
    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<Void>> enable(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        botService.enable(id);
        return ResponseEntity.ok(ApiResponse.ok("Bot enabled", null));
    }

    @Operation(summary = "Hard-delete a bot",
            description = "Removes the bot, its documents (incl. vector chunks and uploaded files), recommended "
                    + "questions, and all chat sessions/messages. Irreversible.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        log.info("DELETE /chatbot/api/v1/bots/{}", id);
        botService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Bot deleted", null));
    }

    @Operation(summary = "List a bot's recommended questions",
            description = "Convenience endpoint for chat UIs that only need the canned starter prompts.")
    @GetMapping("/{id}/recommended-questions")
    public ResponseEntity<ApiResponse<List<RecommendedQuestionDto>>> listRecommendedQuestions(
            @Parameter(description = "Bot ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(botService.listRecommendedQuestions(id)));
    }

    @Operation(summary = "Add a recommended question to a bot",
            description = "Append a single canned starter prompt to the bot's list. The new question is "
                    + "ordered last (creation-time order). Returns the persisted question with its server-assigned ID.")
    @PostMapping("/{id}/recommended-questions")
    public ResponseEntity<ApiResponse<RecommendedQuestionDto>> addRecommendedQuestion(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Valid @RequestBody RecommendedQuestionDto req) {
        log.info("POST /chatbot/api/v1/bots/{}/recommended-questions", id);
        return ResponseEntity.ok(ApiResponse.ok(botService.addRecommendedQuestion(id, req)));
    }

    @Operation(summary = "Delete a recommended question",
            description = "Remove a single recommended question. The question must belong to the bot in the path; "
                    + "otherwise 404 is returned.")
    @DeleteMapping("/{id}/recommended-questions/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteRecommendedQuestion(
            @Parameter(description = "Bot ID") @PathVariable UUID id,
            @Parameter(description = "Recommended question ID") @PathVariable UUID questionId) {
        log.info("DELETE /chatbot/api/v1/bots/{}/recommended-questions/{}", id, questionId);
        botService.deleteRecommendedQuestion(id, questionId);
        return ResponseEntity.ok(ApiResponse.ok("Recommended question deleted", null));
    }
}
