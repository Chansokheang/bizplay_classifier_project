package com.api.bizplay_classifier_api.model.response;

import com.api.bizplay_classifier_api.model.dto.AppUserDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private AppUserDTO appUserDTO;
    private AuthResponse authResponse;
}
