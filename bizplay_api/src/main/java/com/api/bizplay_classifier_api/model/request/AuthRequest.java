package com.api.bizplay_classifier_api.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AuthRequest {
    @NotBlank(message = "Email must be not blank")
    @NotNull(message = "Email must be not null")
    @NotEmpty(message = "Email must be not empty")
    @Email(message = "Invalid Email")
    @Schema(example = "example@gmail.com")
    private String email;
    @Schema(example = "String12345")
    private String password;
}
