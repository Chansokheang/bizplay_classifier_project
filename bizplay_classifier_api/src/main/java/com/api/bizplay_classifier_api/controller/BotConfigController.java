package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.PromptEnhancementResponse;
import com.api.bizplay_classifier_api.service.botConfigService.BotConfigService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/bot-configs")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class BotConfigController {

    private final BotConfigService botConfigService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<?>> createBotConfig(@Valid @RequestBody BotConfigRequest botConfigRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<BotConfigDTO>builder()
                        .payload(botConfigService.createBotConfig(botConfigRequest))
                        .message("Bot config was created successfully.")
                        .status(HttpStatus.CREATED)
                        .code(HttpStatus.CREATED.value())
                .build()
        );
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<?>> getLatestBotConfigByCompanyId(@PathVariable UUID companyId) {
        return ResponseEntity.ok(
                ApiResponse.<BotConfigDTO>builder()
                        .payload(botConfigService.getLatestBotConfigByCompanyId(companyId))
                        .message("Bot config was retrieved successfully.")
                        .status(HttpStatus.OK)
                        .code(HttpStatus.OK.value())
                        .build()
        );
    }

    @PutMapping("/{companyId}")
    public ResponseEntity<ApiResponse<?>> upsertBotConfig(
            @PathVariable UUID companyId,
            @RequestBody @Valid BotConfigRequest.Config config
    ) {
        BotConfigDTO payload = botConfigService.upsertBotConfig(companyId, config);
        return ResponseEntity.ok(
                ApiResponse.<BotConfigDTO>builder()
                        .payload(payload)
                        .message("Bot config was saved successfully.")
                        .status(HttpStatus.OK)
                        .code(HttpStatus.OK.value())
                        .build()
        );
    }

    @GetMapping("/prompt-enhancement")
    public ResponseEntity<ApiResponse<PromptEnhancementResponse>> generatePromptEnhancementPreview(
            @RequestParam("companyId") UUID companyId,
            @RequestParam(value = "sampleRows", required = false) Integer sampleRows
    ) {
        // Call the synchronous version - it's already fast enough with the AI optimizations
        PromptEnhancementResponse result = botConfigService.generatePromptEnhancementPreview(companyId, sampleRows);
        return ResponseEntity.ok(
                ApiResponse.<PromptEnhancementResponse>builder()
                        .payload(result)
                        .message("Bot prompt preview was generated successfully from training data.")
                        .status(HttpStatus.OK)
                        .code(HttpStatus.OK.value())
                        .build()
        );
    }
}
