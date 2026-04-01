package com.api.bizplay_classifier_api.service.userService;

import com.api.bizplay_classifier_api.model.dto.AppUserDTO;
import com.api.bizplay_classifier_api.model.request.AppUserRequest;
import com.api.bizplay_classifier_api.model.request.AuthRequest;
import com.api.bizplay_classifier_api.model.response.LoginResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.UUID;


public interface AppUserService extends UserDetailsService {
    AppUserDTO registerUser(AppUserRequest appUserRequest);

    AppUserDTO findUserByEmail(String email);

    LoginResponse authenticate(AuthRequest authRequest) throws Exception;

    AppUserDTO findUserByUserId(UUID userId);
}
