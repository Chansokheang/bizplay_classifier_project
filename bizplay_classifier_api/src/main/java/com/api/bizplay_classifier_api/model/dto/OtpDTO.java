package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class OtpDTO {
    private UUID userId;
    private String otpCode;
    private LocalDate issuedDate;
    private LocalDate expiration;
    private Boolean isVerified;
}
