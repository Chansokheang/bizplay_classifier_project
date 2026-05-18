package com.api.bizplay_chatbot.telegram.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelegramConfigureRequest {

    @Schema(description = "Telegram bot token issued by @BotFather. Format is "
            + "`<numeric-id>:<35-char-string>`. Stored verbatim and used to "
            + "authenticate every outbound call to Telegram on behalf of this "
            + "bot.",
            example = "1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ-1234567890")
    @NotBlank(message = "token is required")
    @Size(max = 128, message = "token must be at most 128 characters")
    private String token;
}
