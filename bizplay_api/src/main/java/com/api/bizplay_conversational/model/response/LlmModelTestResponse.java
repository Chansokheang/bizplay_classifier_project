package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of a connectivity test against a registered LLM model — a tiny "ping" call. {@code ok=true}
 * means the provider responded; otherwise {@code error} carries the provider's message (e.g.
 * "400 - Auth Fail: No apikey provided"), so the UI can validate a model right after adding it.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmModelTestResponse {
    private String name;
    private boolean ok;
    /** A short excerpt of the model's reply when ok. */
    private String reply;
    /** The provider/transport error when not ok. */
    private String error;
    private Long latencyMs;
}
