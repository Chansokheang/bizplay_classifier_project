package com.api.bizplay_conversational.service.textAnalysisAgentService;

import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface TextAnalysisAgentService {

    /**
     * Analyze a natural-language message and extract trip information (data only).
     * Does not modify draft_json — the RequestBody builder is responsible for that.
     */
    TextAnalysisResult analyze(String message, List<Message> history);

    /** Convenience overload with no conversation history (e.g. for isolated testing). */
    default TextAnalysisResult analyze(String message) {
        return analyze(message, List.of());
    }
}
