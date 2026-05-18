package com.api.bizplay_chatbot.corp.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CorpResponse {

    private Long id;
    private String corpNo;
    private Long corpGroupId;
    private String corpName;
    private LocalDateTime createdDate;
}
