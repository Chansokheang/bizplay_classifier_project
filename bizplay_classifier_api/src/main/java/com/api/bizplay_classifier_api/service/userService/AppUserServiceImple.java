package com.api.bizplay_classifier_api.service.userService;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.AppUserDTO;
import com.api.bizplay_classifier_api.model.dto.OtpDTO;
import com.api.bizplay_classifier_api.model.entity.AppUser;
import com.api.bizplay_classifier_api.model.entity.Otps;
import com.api.bizplay_classifier_api.model.request.AppUserRequest;
import com.api.bizplay_classifier_api.model.request.AuthRequest;
import com.api.bizplay_classifier_api.model.response.AuthResponse;
import com.api.bizplay_classifier_api.model.response.LoginResponse;
import com.api.bizplay_classifier_api.repository.AppUserRepo;
import com.api.bizplay_classifier_api.repository.OtpRepo;
import com.api.bizplay_classifier_api.security.JwtService;
import com.api.bizplay_classifier_api.utils.EmailUtil;
import com.api.bizplay_classifier_api.utils.OtpUtil;
import jakarta.mail.MessagingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@Service
public class AppUserServiceImple implements AppUserService{

    private static final String STATIC_LOGIN_USERNAME = "bizplay";
    private static final String STATIC_LOGIN_FIRSTNAME = "Biz";
    private static final String STATIC_LOGIN_LASTNAME = "Play";
    private static final String STATIC_LOGIN_EMAIL = "bizplay.admin@local";
    private static final String STATIC_LOGIN_PASSWORD = "BizplayStatic123!";
    private static final Character STATIC_LOGIN_GENDER = 'N';
    private static final LocalDate STATIC_LOGIN_DOB = LocalDate.of(2000, 1, 1);

    private final AppUserRepo appUserRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final JwtService jwtService;
    private final OtpRepo otpRepo;

    private final OtpUtil otpUtil;
    private final EmailUtil emailUtil;

    public LoginResponse authenticate(AuthRequest authRequest) {
        AppUser staticUser = ensureStaticLoginUser();
        AppUserDTO appUser = modelMapper.map(staticUser, AppUserDTO.class);
        final UserDetails userDetails = loadUserByUsername(STATIC_LOGIN_EMAIL);

        final String token = jwtService.generateToken(userDetails);
        AuthResponse authResponse = new AuthResponse(token);

        return new LoginResponse(appUser, authResponse);
    }

    private AppUser ensureStaticLoginUser() {
        AppUser existingUser = appUserRepo.findUserByEmail(STATIC_LOGIN_EMAIL);
        if (existingUser != null) {
            return existingUser;
        }

        String encodedPassword = passwordEncoder.encode(STATIC_LOGIN_PASSWORD);
        return appUserRepo.createStaticLoginUser(
                STATIC_LOGIN_USERNAME,
                STATIC_LOGIN_FIRSTNAME,
                STATIC_LOGIN_LASTNAME,
                STATIC_LOGIN_EMAIL,
                encodedPassword,
                STATIC_LOGIN_GENDER,
                STATIC_LOGIN_DOB
        );
    }

    @Override
    public AppUserDTO findUserByUserId(UUID userId) throws UsernameNotFoundException {
        AppUser appUser = appUserRepo.findUserByUserId(userId);
        if (appUser == null) {
            throw new CustomNotFoundException("User not found with id: " + userId);
        }
        return modelMapper.map(appUser, AppUserDTO.class);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser appUser = appUserRepo.findUserByEmail(email);
        if (appUser == null) {
            throw new CustomNotFoundException("The email or password is incorrect.");
        }
        return appUser;
    }

    @Override
    @Transactional
    public AppUserDTO registerUser(AppUserRequest appUserRequest) {
        // Set email to lowercase
        appUserRequest.setEmail(appUserRequest.getEmail().toLowerCase());

        if (!appUserRequest.getPassword().equals(appUserRequest.getConfirmPassword())){
            throw new CustomNotFoundException("Password and confirmed password do not match.");
        }

        // Check if the email is existed or not
        if (appUserRepo.findUserByEmail(appUserRequest.getEmail()) != null){
            throw new CustomNotFoundException("This email: " + appUserRequest.getEmail() + " is already existed.");
        }

        // Date of birth must be before today.
        if (appUserRequest.getDob() == null || !appUserRequest.getDob().isBefore(LocalDate.now())) {
            throw new CustomNotFoundException("Date of Birth can't be today or in the future.");
        }

        String passwordEncode = passwordEncoder.encode(appUserRequest.getPassword());
        appUserRequest.setPassword(passwordEncode);

        AppUser appUser = appUserRepo.registerUser(appUserRequest);

        // Send email for confirmation
        OtpDTO otpDTO = otpUtil.generateOTP(appUser.getUserId());
        Otps otps = otpRepo.insertOtp(otpDTO);

        try {
            emailUtil.sendOtpEmail(appUser.getEmail(), otps.getOtpCode());
        } catch (MessagingException | RuntimeException e) {
            log.warn("OTP email could not be sent to {}: {}", appUser.getEmail(), e.getMessage());
        }

        return modelMapper.map(appUser, AppUserDTO.class);
    }

    @Override
    public AppUserDTO findUserByEmail(String email) throws UsernameNotFoundException {
        AppUser appUser = appUserRepo.findUserByEmail(email);
        if (appUser == null) {
            throw new CustomNotFoundException("The email or password is incorrect.");
        }
        return modelMapper.map(appUser, AppUserDTO.class);
    }

}
