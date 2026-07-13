package com.api.bizplay_chatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds a Spring AI {@link ChatClient} for one LLM model definition. Shared by the startup registry
 * ({@link SpringAiConfig}) and the runtime model-management service, so config-declared and
 * DB-declared models are wired identically (auth style, custom header, completions path).
 */
@Component
public class ChatClientFactory {

    public ChatClient build(LlmProperties.ModelEntry entry) {
        // Force HTTP/1.1 with 60s timeout — vLLM drops POST bodies over HTTP/2.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        // Auth styles (from api-key-header):
        //  - "bearer"    : standard OpenAI-compatible Authorization: Bearer <key>.
        //  - "x-api-key" : legacy local-vLLM custom header; Authorization is stripped.
        //  - any other   : a literal custom header name (e.g. "apikey" for LUXIA); sent as
        //                  "<headerName>: <key>" with Authorization stripped.
        String headerStyle = entry.getApiKeyHeader() == null
                ? "bearer"
                : entry.getApiKeyHeader().trim().toLowerCase();
        boolean useBearer = "bearer".equals(headerStyle);

        String openAiApiKey;
        if (useBearer) {
            // Hand the real key to OpenAiApi so it emits Authorization: Bearer <key>.
            openAiApiKey = entry.getApiKey();
        } else {
            // Non-bearer: send the key in a custom header and strip the Authorization header
            // Spring AI's OpenAiApi would otherwise add.
            String headerName = "x-api-key".equals(headerStyle) ? "x-api-key" : entry.getApiKeyHeader().trim();
            restClientBuilder
                    .defaultHeader(headerName, entry.getApiKey())
                    .requestInterceptor((request, body, execution) -> {
                        request.getHeaders().remove(HttpHeaders.AUTHORIZATION);
                        return execution.execute(request, body);
                    });
            openAiApiKey = "unused";
        }

        String completionsPath = entry.getCompletionsPath() == null || entry.getCompletionsPath().isBlank()
                ? "/chat/completions"
                : entry.getCompletionsPath().trim();

        OpenAiApi chatApi = OpenAiApi.builder()
                .baseUrl(entry.getBaseUrl())
                .apiKey(openAiApiKey)
                .completionsPath(completionsPath)
                .restClientBuilder(restClientBuilder)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(entry.getModel())
                .temperature(entry.getTemperature())
                .maxTokens(entry.getMaxTokens())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(chatApi)
                .defaultOptions(options)
                .build();

        // No defaultSystem(...) — system prompt is supplied per-call.
        return ChatClient.builder(chatModel).build();
    }
}
