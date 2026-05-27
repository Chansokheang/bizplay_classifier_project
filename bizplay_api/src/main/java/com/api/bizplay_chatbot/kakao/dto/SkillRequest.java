package com.api.bizplay_chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Top-level body Kakao i Openbuilder POSTs to a Skill webhook. We only
 * read what we need — intent/action/bot blocks are deserialized as raw
 * maps and ignored. {@code @JsonIgnoreProperties(ignoreUnknown=true)}
 * means future Kakao additions won't break deserialisation.
 *
 * <p>No {@code @JsonNaming} strategy: Kakao's Skill API uses camelCase JSON
 * keys (e.g. {@code userRequest}), which matches Java field names directly
 * under Jackson's default property-name matching.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillRequest {
    private SkillUserRequest userRequest;
}
