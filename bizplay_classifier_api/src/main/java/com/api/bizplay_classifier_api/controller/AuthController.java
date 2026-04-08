package com.api.bizplay_classifier_api.controller;

import com.api.bizplay_classifier_api.model.dto.AppUserDTO;
import com.api.bizplay_classifier_api.model.request.AppUserRequest;
import com.api.bizplay_classifier_api.model.request.AuthRequest;
import com.api.bizplay_classifier_api.model.response.ApiResponse;
import com.api.bizplay_classifier_api.model.response.LoginResponse;
import com.api.bizplay_classifier_api.service.userService.AppUserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auths")
@AllArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
public class AuthController {

    private final AppUserService appUserService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AppUserDTO>> registerUser(@Valid @RequestBody AppUserRequest appUserRequest) {
            AppUserDTO appUserDTO = appUserService.registerUser(appUserRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<AppUserDTO>builder()
                            .payload(appUserDTO)
                            .message("User registered successfully.")
                            .code(HttpStatus.CREATED.value())
                            .status(HttpStatus.CREATED)
                            .build()
            );
    }

    @PostMapping("login")
    public ResponseEntity<?> authenticate(@Valid @RequestBody AuthRequest authRequest) throws Exception {
        LoginResponse loginResponse = appUserService.authenticate(authRequest);
        return ResponseEntity.ok(
                ApiResponse.<AppUserDTO>builder()
                        .payload(loginResponse.getAppUserDTO())
                        .message("Login successfully.")
                        .token(loginResponse.getAuthResponse().getToken())
                        .code(200)
                        .status(HttpStatus.OK)
                        .build()
        );
    }
}
