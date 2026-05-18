package com.api.bizplay_chatbot.rag.chat.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_chatbot.config.LlmProperties;
import com.api.bizplay_chatbot.rag.chat.dto.ChatRequest;
import com.api.bizplay_chatbot.rag.chat.dto.ChatResponse;
import com.api.bizplay_chatbot.rag.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Tag(name = "RAG Chat", description = "Chat with the RAG-powered assistant and view conversation history")
@RestController
@RequestMapping("/chatbot/api/v1/rag/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final LlmProperties llmProperties;

    @Operation(summary = "Send a chat query to a specific bot",
            description = "Submit a question to one bot. The bot's documents are the only chunks "
                    + "considered (vector search is filtered by bot_id), and the bot's own LLM model, "
                    + "temperature, max-tokens, history-turns, top-K, system prompt, and source-expose "
                    + "settings are applied — request body cannot override them. " 
                    + "Session ID must be blank or omitted to start a new conversation; if supplied, it continues the existing conversation within that session.")
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        log.info("POST /chatbot/api/v1/rag/chat - query={}, sessionId={}", request.getQuery(), request.getSessionId());
        ChatResponse response = chatService.chat(request);
        log.info("POST /chatbot/api/v1/rag/chat - completed, sessionId={}, sources={}",
                response.getSessionId(), response.getSources().size());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "List available LLM models",
            description = "Returns the list of configured LLM models with a 'default' flag. "
                    + "Use the 'name' value when creating or updating a bot (the 'llmModel' field on "
                    + "BotCreateRequest / BotUpdateRequest). Chat itself does not take a model — each "
                    + "bot stores its own choice.")
    @GetMapping("/models")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> models() {
        String defaultModel = llmProperties.getDefaultModel();
        List<Map<String, Object>> models = llmProperties.getModels().stream()
                .filter(m -> m.getName() != null && !m.getName().isBlank()
                        && m.getBaseUrl() != null && !m.getBaseUrl().isBlank())
                .map(m -> Map.<String, Object>of(
                        "name", m.getName(),
                        "label", m.getLabel() != null && !m.getLabel().isBlank() ? m.getLabel() : m.getName(),
                        "isDefault", m.getName().equals(defaultModel)))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(models));
    }

    @Operation(summary = "Get chat history",
            description = "Retrieve all messages in a chat session. The session is bot-scoped — "
                    + "callers can read any session by ID, but the messages it returns are only "
                    + "the ones from that bot's conversation.")
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(
            @Parameter(description = "Chat session ID") @PathVariable UUID sessionId) {
        log.info("GET /chatbot/api/v1/rag/chat/history/{}", sessionId);
        List<Map<String, Object>> history = chatService.getHistory(sessionId);
        log.info("GET /chatbot/api/v1/rag/chat/history/{} - returning {} messages", sessionId, history.size());
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
