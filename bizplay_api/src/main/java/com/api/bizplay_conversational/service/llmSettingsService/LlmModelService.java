package com.api.bizplay_conversational.service.llmSettingsService;

import com.api.bizplay_conversational.model.request.LlmModelRequest;
import com.api.bizplay_conversational.model.response.LlmModelResponse;
import com.api.bizplay_conversational.model.response.LlmModelTestResponse;

import java.util.List;

/**
 * Manage the LLM models available to the conversational agents at runtime: list, add, update, and
 * delete DB-backed models (with their API key), hot-registering them into the live ChatClient
 * registry. Static app.llm.models config entries are also listed, but are read-only here.
 */
public interface LlmModelService {

    List<LlmModelResponse> list();

    LlmModelResponse getByName(String name);

    LlmModelResponse create(LlmModelRequest request);

    LlmModelResponse update(String name, LlmModelRequest request);

    void delete(String name);

    /** Send a tiny "ping" to a registered model to verify its URL / key / headers actually work. */
    LlmModelTestResponse test(String name);
}
