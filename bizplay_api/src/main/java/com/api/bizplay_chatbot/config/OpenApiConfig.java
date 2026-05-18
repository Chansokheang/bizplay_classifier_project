package com.api.bizplay_chatbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    /** Desired order of the Swagger UI sections. Tags not in this list fall
     *  through to alphabetical sorting after the explicitly-ordered ones, so
     *  newly-added controllers don't silently disappear from the doc. Kakao
     *  sits directly below Telegram on purpose — both are channel-integration
     *  sections and operators expect to find them adjacent. */
    private static final List<String> TAG_ORDER = List.of(
            "Bots",
            "Documents",
            "RAG Chat",
            "Corp Groups",
            "Corporations",
            "Telegram integration",
            "Kakao integration"
    );

    @Bean
    public OpenAPI openAPI() {
        // Pin the server URL to "/" so Swagger UI's "Try it out" calls always
        // resolve against the same origin/scheme as the page. Without this,
        // springdoc auto-fills the URL from the internal request (e.g.
        // http://localhost:8080) — which the browser then blocks under HTTPS
        // with "URL scheme must be 'http' or 'https' for CORS request".
        //
        // Tag order is set in the customizer below — not here — because
        // declaring tags on the bean AND letting controllers carry @Tag
        // annotations causes SpringDoc 2.8 to emit BOTH copies in the final
        // doc (with-description and without-description), confusing Swagger UI.
        return new OpenAPI()
                .info(new Info()
                        .title("BizPlay RAG Chatbot API")
                        .description("On-premise RAG-based chatbot API for internal enterprise knowledge.")
                        .version("0.2.0"))
                .servers(List.of(new Server().url("/")));
    }

    /**
     * Runs after SpringDoc has finished assembling the OpenAPI doc.
     * Deduplicates tags by name (SpringDoc 2.8 leaves duplicates when a tag
     * appears both via @Tag on a controller and via a programmatic tag list)
     * and reorders them per {@link #TAG_ORDER}. The full descriptions from
     * the controllers' @Tag annotations are preserved.
     */
    @Bean
    public OpenApiCustomizer tagOrderingCustomizer() {
        return openApi -> {
            if (openApi.getTags() == null || openApi.getTags().isEmpty()) return;

            // Dedupe by name, preferring the entry that carries a description
            // (controllers' @Tag values) over a bare name-only entry.
            LinkedHashMap<String, Tag> uniqueByName = new LinkedHashMap<>();
            for (Tag t : openApi.getTags()) {
                uniqueByName.merge(t.getName(), t, (existing, dup) ->
                        existing.getDescription() != null ? existing : dup);
            }

            Map<String, Integer> orderIndex = new java.util.HashMap<>();
            for (int i = 0; i < TAG_ORDER.size(); i++) {
                orderIndex.put(TAG_ORDER.get(i), i);
            }

            // Tags in TAG_ORDER come first (in declared order); anything else
            // falls back to alphabetical so a forgotten controller is still
            // findable instead of being silently hidden.
            List<Tag> sorted = uniqueByName.values().stream()
                    .sorted(Comparator
                            .comparingInt((Tag t) -> orderIndex.getOrDefault(t.getName(), Integer.MAX_VALUE))
                            .thenComparing(Tag::getName))
                    .toList();

            openApi.setTags(sorted);
        };
    }
}
