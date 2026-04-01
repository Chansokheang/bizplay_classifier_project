package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.dto.BotConfigDTO;
import com.api.bizplay_classifier_api.model.request.BotConfigRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.service.botConfigService.BotConfigService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bot-configs")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = {"http://localhost:3000"})
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
}

