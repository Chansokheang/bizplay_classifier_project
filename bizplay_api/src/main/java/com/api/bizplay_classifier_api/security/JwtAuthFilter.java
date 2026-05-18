package com.api.bizplay_classifier_api.security;

import com.api.bizplay_classifier_api.service.userService.AppUserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String AUTH_MODE_MOCK = "mock";

    private final JwtService jwtService;
    private final AppUserService appUserService;
    private final String authMode;

    public JwtAuthFilter(
            JwtService jwtService,
            AppUserService appUserService,
            @Value("${app.auth.mode:static}") String authMode
    ){
        this.jwtService = jwtService;
        this.appUserService = appUserService;
        this.authMode = authMode;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (AUTH_MODE_MOCK.equalsIgnoreCase(authMode)) {
            authenticateAsStaticUser(request);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")){
            token = extractBearerToken(authHeader);
            if (token != null && !token.isBlank()) {
                if (jwtService.isStaticToken(token)) {
                    email = jwtService.extractUsername(token);
                } else {
                    try {
                        email = jwtService.extractUsername(token);
                    } catch (JwtException | IllegalArgumentException e) {
                        SecurityContextHolder.clearContext();
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token.");
                        return;
                    }
                }
            }
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null){
            UserDetails userDetails = appUserService.loadUserByUsername(email);
            if (jwtService.validateToken(token, userDetails)){
                setAuthentication(request, userDetails);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateAsStaticUser(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UserDetails userDetails = appUserService.getOrCreateStaticLoginUser();
        setAuthentication(request, userDetails);
    }

    private void setAuthentication(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    private String extractBearerToken(String authHeader) {
        String rawToken = authHeader.substring(7).trim();
        if (rawToken.isEmpty()) {
            return null;
        }
        int commaIndex = rawToken.indexOf(',');
        if (commaIndex >= 0) {
            rawToken = rawToken.substring(0, commaIndex).trim();
        }
        int whitespaceIndex = rawToken.indexOf(' ');
        if (whitespaceIndex >= 0) {
            rawToken = rawToken.substring(0, whitespaceIndex).trim();
        }
        return rawToken;
    }
}
