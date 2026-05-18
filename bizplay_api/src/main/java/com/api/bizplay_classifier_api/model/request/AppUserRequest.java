package com.api.bizplay_classifier_api.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppUserRequest {
    @Schema(example = "Sokheang")
    @NotBlank(message = "Username must not be blank")
    private String username = "Sokheang";

    @Schema(example = "Sokheang")
    @NotBlank(message = "Firstname must not be blank")
    @Pattern(regexp = "^[^\\d]+$", message = "Firstname must not contain numbers")
    private String firstname = "Sokheang";

    @Schema(example = "Chan")
    @NotBlank(message = "Lastname must not be blank")
    @Pattern(regexp = "^[^\\d]+$", message = "Lastname must not contain numbers")
    private String lastname = "Chan";

    @Schema(example = "M")
    @NotBlank(message = "Gender must not be blank")
    @Pattern(regexp = "[MF]", message = "Gender must be M or F")
    private String gender = "M";

    @Schema(example = "2001-08-08")
    @NotNull(message = "Date of birth must not be null")
    private LocalDate dob = LocalDate.parse("2001-08-08");

    @Schema(example = "example@gmail.com")
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Invalid Email")
    private String email = "example@gmail.com";

    @Schema(example = "String12345")
    @NotNull(message = "Password must not be null")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$", message = "Password must be at least 8 characters long and contain letters and numbers")
    private String password = "String12345";

    @Schema(example = "String12345")
    private String confirmPassword = "String12345";
}
