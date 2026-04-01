package com.api.bizplay_classifier_api.utils;

import com.api.bizplay_classifier_api.model.entity.AppUser;
import com.api.bizplay_classifier_api.service.userService.AppUserService;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class GetCurrentUser {

    private final AppUserService appUserService;

    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("User is not authenticated.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AppUser appUser)) {
            throw new AccessDeniedException("Authenticated principal is not a valid application user.");
        }

        return appUser;
    }

    public UUID getCurrentUserId() throws UsernameNotFoundException {
        UUID userId = getCurrentUser().getUserId();
        appUserService.findUserByUserId(userId);
        return userId;
    }
}
