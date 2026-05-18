package com.api.bizplay_chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * The {@code userRequest} block from a Kakao Skill webhook payload.
 *
 * <p>{@code utterance} is what the user typed. {@code user.id} identifies
 * the user (stable per-bot). {@code callbackUrl} is where we POST the real
 * answer when using the callback pattern — only present when our Skill
 * config has callback enabled and Kakao has decided this turn needs one.
 *
 * <p>No {@code @JsonNaming} strategy: Kakao's Skill API uses camelCase
 * keys (this is the field that exposed the bug — Kakao sends
 * {@code callbackUrl}, not {@code callback_url}).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillUserRequest {
    private String timezone;
    private String utterance;
    private String lang;
    private String callbackUrl;
    private SkillUser user;
}
