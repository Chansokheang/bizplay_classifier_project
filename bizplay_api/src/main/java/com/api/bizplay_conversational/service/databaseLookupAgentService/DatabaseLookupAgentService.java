package com.api.bizplay_conversational.service.databaseLookupAgentService;

import com.api.bizplay_conversational.model.response.DatabaseLookupAgentResponse;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface DatabaseLookupAgentService {
    DatabaseLookupAgentResponse lookup(String corpNo, String task);

    DatabaseLookupAgentResponse lookup(String corpNo, String task, List<Message> history);
}
