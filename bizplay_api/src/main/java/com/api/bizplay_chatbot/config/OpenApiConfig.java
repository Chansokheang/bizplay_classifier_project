package com.api.bizplay_chatbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bizplayOpenAPI() {
        // Pin the server URL to "/" so Swagger UI's "Try it out" calls always
        // resolve against the same origin/scheme as the page (otherwise HTTPS
        // pages would emit blocked http://localhost requests).
        return new OpenAPI()
                .info(new Info()
                        .title("BizPlay API")
                        .description("BizPlay platform APIs — chatbot, classifier, and compliance modules.")
                        .version("0.2.0"))
                .servers(List.of(new Server().url("/")));
    }

    @Bean
    public GroupedOpenApi chatbotApi() {
        return GroupedOpenApi.builder()
                .group("1-chatbot")
                .displayName("Chatbot")
                .packagesToScan("com.api.bizplay_chatbot")
                .build();
    }

    @Bean
    public GroupedOpenApi classifierApi() {
        return GroupedOpenApi.builder()
                .group("2-classifier")
                .displayName("Classifier")
                .packagesToScan("com.api.bizplay_classifier_api")
                .build();
    }

    @Bean
    public GroupedOpenApi complianceApi() {
        return GroupedOpenApi.builder()
                .group("3-compliance")
                .displayName("Compliance")
                .packagesToScan("com.api.bizplay_compliance")
                .build();
    }
}
