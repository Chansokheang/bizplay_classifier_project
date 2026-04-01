package com.api.bizplay_classifier_api.model.entity;

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
public class Otps {
    private UUID otpId;
    private UUID userId;
    private String otpCode;
    private LocalDate issuedDate;
    private LocalDate expiration;
    private Boolean isVerified;
}
