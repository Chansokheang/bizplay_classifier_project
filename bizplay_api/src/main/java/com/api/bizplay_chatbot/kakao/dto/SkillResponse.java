package com.api.bizplay_chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Both the synchronous webhook response AND the eventual callback POST body
 * share this shape. Two patterns matter:
 *
 * <ol>
 *   <li><b>Callback hand-off (sync response, sent inside the 5s window):</b>
 *       {@code { "version": "2.0", "useCallback": true, "data": { "text": "…placeholder…" } }}
 *       The {@code data.text} is what the user sees as the immediate "I'm thinking"
 *       message; the real answer comes via the callback POST.</li>
 *   <li><b>Final answer (callback POST body OR a sync response when fast enough):</b>
 *       {@code { "version": "2.0", "template": { "outputs": [ { "simpleText": { "text": "…" } } ] } }}</li>
 * </ol>
 *
 * {@link JsonInclude.Include#NON_NULL} drops the unused branch so the two
 * shapes are reachable with the same builder.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillResponse {

    /** Kakao Skill protocol version. Always "2.0" for current Openbuilder. */
    @Builder.Default
    private String version = "2.0";

    /** True on the synchronous response when we want Kakao to wait for a
     *  callback POST with the real answer. False / null on the final
     *  response. */
    private Boolean useCallback;

    /** Per Kakao docs: payload attached to a callback hand-off, surfaced as
     *  the placeholder bubble in the chat. The {@code data.text} key is
     *  recognised by Openbuilder; we send no other data fields. */
    private CallbackData data;

    /** The actual user-visible answer. Only set on the final response. */
    private SkillTemplate template;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CallbackData {
        private String text;
    }
}
