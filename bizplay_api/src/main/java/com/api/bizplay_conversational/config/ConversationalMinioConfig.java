package com.api.bizplay_conversational.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Self-contained MinIO client for the conversational module's uploaded files.
 * Uses its own app.conversational.minio.* properties and its own bean name so it does
 * not depend on any storage bean from other modules.
 */
@Configuration
public class ConversationalMinioConfig {

    @Bean(name = "conversationalMinioClient")
    public MinioClient conversationalMinioClient(
            @Value("${app.conversational.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.conversational.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.conversational.minio.secret-key:minioadmin}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
