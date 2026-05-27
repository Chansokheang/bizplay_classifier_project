package com.api.bizplay_chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/** Per-bot user identifier from Kakao i Openbuilder. The {@code id} is
 *  stable for the lifetime of the (bot, user) pair but opaque to us. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillUser {
    private String id;
    private String type;
}
