package com.api.bizplay_conversational.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the "traditional method" demo site bundled under {@code classpath:/web/} at
 * {@code /web/**}. Keeping it same-origin with the REST API (port 8080) means the demo's
 * {@code fetch()} calls to {@code /api/v1/plans} need no CORS configuration.
 *
 * <p>Open it at {@code http://localhost:8080/web/} (also reachable as {@code /web/index.html}).
 */
@Configuration
public class WebDemoStaticConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/web/**")
                .addResourceLocations("classpath:/web/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // /web (no trailing slash) -> redirect to the canonical /web/ URL.
        registry.addRedirectViewController("/web", "/web/");
        // /web/ -> serve the index page.
        registry.addViewController("/web/").setViewName("forward:/web/index.html");
    }

    /**
     * Dev-only CORS for the plans endpoint so the demo also works when opened from another
     * localhost origin (e.g. an IDE live-server on a different port) rather than {@code :8080/web/}.
     * Scoped narrowly to {@code /api/v1/plans} and localhost patterns only — it does not affect
     * the production controllers' own {@code @CrossOrigin} domain allowlists.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] localhost = {
                "http://localhost:[*]", "http://127.0.0.1:[*]",
                "https://localhost:[*]", "https://127.0.0.1:[*]"
        };
        String[] paths = {
                "/api/v1/plans", "/api/v1/plans/**",
                "/api/v1/reports", "/api/v1/reports/**",
                "/api/v1/agent-conversations/**"
        };
        for (String path : paths) {
            registry.addMapping(path)
                    .allowedOriginPatterns(localhost)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*");
        }
    }
}
