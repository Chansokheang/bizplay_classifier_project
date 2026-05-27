package com.api.bizplay_chatbot.corp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CorpGroupResponse {

    private Long id;
    private String corpGroupCd;
}
