package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single-row runtime setting selecting which LLM the conversational sub-agents use.
 * {@code activeModel} null/blank means each agent falls back to its own configured default
 * (app.conversational.&lt;agent&gt;.model). The row is a singleton pinned to id = 1.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConversationalLlmSetting {

    /** Singleton primary key — always 1. */
    public static final short SINGLETON_ID = 1;

    private short id = SINGLETON_ID;
    private String activeModel;
    private LocalDateTime updatedDate;
}
