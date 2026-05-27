package com.api.bizplay_chatbot.bot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemPromptGenerationRequest {

    @Schema(description = "Bot name — used to anchor the prompt's subject", example = "Travel Expense Bot")
    @NotBlank
    @Size(max = 255)
    private String name;

    @Schema(description = "Free-form description of what the bot is for. The richer the description, "
            + "the more tailored the generated prompt.",
            example = "Helps employees understand and apply the company's domestic and international travel "
                    + "expense reimbursement policy.")
    @Size(max = 2000)
    private String description;

    @Schema(description = "Optional LLM registry name to use for generation. Defaults to the configured "
            + "default model when omitted.",
            example = "exaone-3.5-7.8b")
    @Size(max = 100)
    private String llmModel;
}
