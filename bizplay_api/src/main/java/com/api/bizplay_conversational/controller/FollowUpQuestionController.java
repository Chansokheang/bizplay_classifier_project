package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext;
import com.api.bizplay_conversational.service.formFollowUpAgentService.FormFollowUpAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Composes ONE natural follow-up question for the chat wizard's local steps (dates, title,
 * travellers…), so the questions the user sees are LLM-phrased — never fixed template strings.
 * Uses the same Follow-up sub-agent (and any per-corp custom prompt) as the plan flow.
 */
@Slf4j
@Tag(name = "Conversational Follow-up Question", description = "Compose one natural follow-up question.")
@RestController
@RequestMapping("/api/v1/agent-conversations/follow-up-question")
@RequiredArgsConstructor
public class FollowUpQuestionController {

    private final FormFollowUpAgentService formFollowUpAgentService;

    @Getter
    @Setter
    public static class FollowUpQuestionRequest {
        private String corpNo;
        private String paperName;
        private List<String> missing;
        private Boolean korean;
    }

    @Operation(summary = "Compose one natural question asking for the given missing fields")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> compose(@RequestBody FollowUpQuestionRequest request) {
        if (request == null || request.getMissing() == null || request.getMissing().isEmpty()) {
            throw new IllegalArgumentException("missing[] is required.");
        }
        AgentTenantContext.set(request.getCorpNo());
        try {
            String question = formFollowUpAgentService.composeFollowUp(
                    request.getPaperName(), request.getMissing(),
                    request.getKorean() != null && request.getKorean());
            return ResponseEntity.ok(ApiResponse.ok(Map.of("question", question == null ? "" : question)));
        } finally {
            AgentTenantContext.clear();
        }
    }
}
