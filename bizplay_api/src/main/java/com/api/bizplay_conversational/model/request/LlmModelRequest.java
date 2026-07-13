package com.api.bizplay_conversational.model.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Create/update payload for a runtime-managed LLM model. On create, {@code name}, {@code baseUrl} and
 * {@code model} are required. On update, {@code name} is taken from the path and ignored here; a null
 * {@code apiKey} keeps the stored key (so the UI need not resend the secret to edit other fields).
 */
@Getter
@Setter
public class LlmModelRequest {

    /** Registry key / unique id (create only). e.g. "luxia3-llm-32b-0731". */
    private String name;
    /** Display label for the UI. */
    private String label;
    /** Base URL, e.g. "https://bridge.luxiacloud.com" (path is appended from completionsPath). */
    private String baseUrl;
    /** API key / secret. Null on update = keep the existing key. */
    private String apiKey;
    /** "bearer" | "x-api-key" | any custom header name (e.g. "apikey"). Defaults to "bearer". */
    private String authScheme;
    /** How the key is sent: "bearer" | "x-api-key" | custom header name. Defaults to "bearer". */
    private String apiKeyHeader;
    /** Completions path appended to baseUrl. Defaults to "/chat/completions". */
    private String completionsPath;
    /** Upstream model id sent in the request body. */
    private String model;
    /** Sampling temperature. */
    private Double temperature;
    /** Max output tokens. */
    private Integer maxTokens;
    /** Whether the model is registered/usable. Defaults to true. */
    private Boolean enabled;
}
