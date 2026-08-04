package com.api.bizplay_conversational.service.fieldMapperAgentService;

import com.api.bizplay_conversational.service.dateContextService.DateContextService;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FieldMapperAgentServiceImple implements FieldMapperAgentService {

    private static final String SYSTEM_PROMPT = """
            You fill a business-trip form from a user's message. The form definition is corp-specific
            configuration (labels are often Korean) and is given per request — never assume fields
            that are not listed.
            Return ONLY JSON, no prose, no markdown: an object whose keys are field keys from the
            definition and whose values follow the field's type:
            - BSTR_PERIOD: {"start":"YYYY-MM-DD","end":"YYYY-MM-DD","destination":<place or null>,"origin":<place or null>,"memo":<string or null>}
            - EDUCATION_INFO (교육정보): {"구분":"사내교육"|"사외교육","교육과정":<string>,"교육클래스":<string>,
              "교육시작일":"YYYY-MM-DD","교육종료일":"YYYY-MM-DD","교육기관":<string>,"내용":<string>}
              — include only the keys the message actually gives
            - BSTR_LINKED_LEAVE (출장 연계 휴가): {"start":"YYYY-MM-DD","end":"YYYY-MM-DD",
              "region":<stay place or null>,"purpose":<leave purpose or null>} — the LEAVE dates,
              not the trip dates; include only when the message mentions leave linked to the trip
            - PARTNER_SUPPORT (협력사 지원): {"choice":"true"|"false","partner":<partner company
              name or null>,"purpose":<visit purpose or null>}
            - a field listing "options": {"choice":"<EXACTLY one of the listed options>"}
            - BASIC_TRAVELER: {"names":["<person name>", ...]}
            - BASIC_TITLE: a short title string for the trip (compose one from the message if a clear
              subject exists; otherwise omit)
            - BASIC_CONTENT: a 1-2 sentence description string of the trip's purpose/work
            - DESTINATION: the trip destination place string
            - ORIGIN: the departure place string (where the trip starts FROM)
            - anything else (HTML or unknown types): a plain string
            Rules:
            - Include ONLY keys the message actually gives information for. Omit everything unknown.
            - Normalize dates to YYYY-MM-DD. Do not invent names, dates, options, or places.
            - RELATIVE dates ("next Tuesday", "tomorrow", "in two weeks") MUST be resolved to
              YYYY-MM-DD using the Date context calendar supplied below — look the date up in the
              table; never guess and never output relative words.
            - If the message mentions a trip period or ANY start/end dates, you MUST include the
              form's BSTR_PERIOD field key with {"start","end"} — never omit it.
            - BASIC_TRAVELER names must be ACTUAL PERSON NAMES. Never include group words or
              pronouns ("our team", "everyone", "we", "colleagues") — omit the key instead.
            - DESTINATION (출장지) only when the user EXPLICITLY names a place they travel TO.
              Company/organization names (e.g. "파트너사", "고객사") and words that are part of the
              trip description are NOT destinations — omit the key instead of reusing them.
            - ORIGIN (출발지) only when the user EXPLICITLY names the place they depart FROM
              ("from Seoul to Busan" → origin Seoul; "서울에서 부산으로" → origin 서울). Never guess
              an origin from the company location or the user's profile — omit the key instead.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final DateContextService dateContextService;
    private final ObjectMapper objectMapper;
    private final com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService;

    @Value("${app.conversational.field-mapper-agent.model:qwen3-14b}")
    private String modelName;

    public FieldMapperAgentServiceImple(Map<String, ChatClient> chatClientRegistry,
                                        LlmSettingsService llmSettingsService,
                                        DateContextService dateContextService,
                                        ObjectMapper objectMapper,
                                        com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.dateContextService = dateContextService;
        this.objectMapper = objectMapper;
        this.agentPromptService = agentPromptService;
        agentPromptService.registerDefault("field-mapper", SYSTEM_PROMPT);
    }

    @Override
    public JsonNode mapFields(String message, JsonNode fields) {
        if (message == null || message.isBlank() || fields == null || !fields.isArray() || fields.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            log.warn("Field mapper model is not configured: {}", modelName);
            return objectMapper.createObjectNode();
        }
        try {
            StringBuilder def = new StringBuilder("Form definition:\n");
            for (JsonNode f : fields) {
                def.append("- key=").append(f.path("key").asText())
                        .append(" | label=").append(f.path("label").asText())
                        .append(" | type=").append(f.path("type").asText())
                        .append(" | required=").append(f.path("required").asBoolean(false));
                if (f.path("options").isArray() && f.path("options").size() > 0) {
                    def.append(" | options=").append(f.path("options").toString());
                }
                def.append('\n');
            }
            List<Message> prompt = List.of(
                    new SystemMessage(agentPromptService.resolve("field-mapper", SYSTEM_PROMPT)
                            + "\n" + dateContextService.buildContext()),
                    new UserMessage(def + "\nUser message:\n" + message));
            String raw = client.prompt().messages(prompt).call().content();
            log.info("Field mapper raw output: {}", raw);
            JsonNode parsed = objectMapper.readTree(extractJson(stripThink(raw)));
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("Field mapping failed: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String stripThink(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        return cleaned.replaceAll("(?is)</?think>", "").trim();
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        return (start < 0 || end < start) ? "{}" : cleaned.substring(start, end + 1);
    }
}
