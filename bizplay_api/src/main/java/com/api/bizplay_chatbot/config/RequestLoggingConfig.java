package com.api.bizplay_chatbot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class RequestLoggingConfig {

    @Bean
    public FilterRegistrationBean<RequestResponseLoggingFilter> requestLoggingFilterRegistration() {
        FilterRegistrationBean<RequestResponseLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestResponseLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Slf4j
    public static class RequestResponseLoggingFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            long start = System.currentTimeMillis();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            String fullPath = query != null ? uri + "?" + query : uri;

            log.info("--> {} {} [remote={}]", method, fullPath, request.getRemoteAddr());

            try {
                filterChain.doFilter(request, response);
            } finally {
                long duration = System.currentTimeMillis() - start;
                log.info("<-- {} {} [status={}, duration={}ms]", method, fullPath, response.getStatus(), duration);
            }
        }
    }
}
