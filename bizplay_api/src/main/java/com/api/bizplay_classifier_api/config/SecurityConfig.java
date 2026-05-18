package com.api.bizplay_classifier_api.config;

import com.api.bizplay_classifier_api.security.JwtAuthEntrypoint;
import com.api.bizplay_classifier_api.security.JwtAuthFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntrypoint jwtAuthEntrypoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, JwtAuthEntrypoint jwtAuthEntrypoint){
        this.jwtAuthEntrypoint = jwtAuthEntrypoint;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntrypoint));
        return http.build();
    }
}

