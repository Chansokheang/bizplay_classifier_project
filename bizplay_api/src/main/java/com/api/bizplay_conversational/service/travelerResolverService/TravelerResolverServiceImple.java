package com.api.bizplay_conversational.service.travelerResolverService;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class TravelerResolverServiceImple implements TravelerResolverService {

    private static final String SYSTEM_PROMPT = """
            You match a person reference from a chat message to a corporate staff ROSTER. The
            reference may be romanized while the roster names are Korean (e.g. "Kim Doha" = 김도하).
            Return ONLY JSON, no prose: {"match": <roster number or null>, "candidates": [<roster numbers>]}
            Rules:
            - "match" only when exactly one roster entry is clearly the same person.
            - Several plausible entries -> match=null and list them in "candidates".
            - Nobody plausible -> match=null, candidates=[].
            - Numbers must come from the roster list. Never invent people.
            /no_think
            """;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.traveler-resolver-agent.model:qwen3-14b}")
    private String modelName;

    public TravelerResolverServiceImple(Map<String, ChatClient> chatClientRegistry,
                                        LlmSettingsService llmSettingsService,
                                        ObjectMapper objectMapper) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Resolution> resolve(List<String> inputs, JsonNode roster) {
        List<Candidate> all = toCandidates(roster);
        List<Resolution> results = new ArrayList<>();
        for (String input : inputs) {
            if (input == null || input.isBlank()) {
                continue;
            }
            results.add(resolveOne(input.trim(), all, roster));
        }
        return results;
    }

    private Resolution resolveOne(String input, List<Candidate> all, JsonNode roster) {
        String needle = input.toLowerCase(Locale.ROOT);

        // 1. Deterministic: exact then contains on userName / englishUserName / employeeNumber.
        List<Candidate> exact = new ArrayList<>();
        List<Candidate> partial = new ArrayList<>();
        int i = 0;
        for (JsonNode u : roster.path("users")) {
            Candidate c = all.get(i++);
            String name = u.path("userName").asText("").toLowerCase(Locale.ROOT);
            String eng = u.path("englishUserName").asText("").toLowerCase(Locale.ROOT);
            String emp = u.path("employeeNumber").asText("").toLowerCase(Locale.ROOT);
            if (needle.equals(name) || (!eng.isBlank() && needle.equals(eng)) || (!emp.isBlank() && needle.equals(emp))) {
                exact.add(c);
            } else if ((!name.isBlank() && (name.contains(needle) || needle.contains(name)))
                    || (!eng.isBlank() && (eng.contains(needle) || needle.contains(eng)))) {
                partial.add(c);
            }
        }
        if (exact.size() == 1) {
            return new Resolution(input, exact.get(0), null, false);
        }
        if (exact.size() > 1) {
            return new Resolution(input, null, exact, false); // duplicate names -> user confirms
        }
        if (partial.size() == 1) {
            return new Resolution(input, partial.get(0), null, false);
        }
        if (partial.size() > 1) {
            return new Resolution(input, null, partial, false);
        }

        // 2. Department reference: all its members become candidates to pick from.
        List<Candidate> dept = new ArrayList<>();
        for (Candidate c : all) {
            String d = c.departmentName() == null ? "" : c.departmentName().toLowerCase(Locale.ROOT);
            if (!d.isBlank() && (d.equals(needle) || d.contains(needle) || needle.contains(d))) {
                dept.add(c);
            }
        }
        if (!dept.isEmpty()) {
            return new Resolution(input, null, dept, false);
        }

        // 3. Cross-script fuzzy assist (romanized vs Korean) — LLM picks FROM the roster only.
        Resolution fuzzy = fuzzyAssist(input, all);
        if (fuzzy != null) {
            return fuzzy;
        }
        return new Resolution(input, null, null, true);
    }

    private Resolution fuzzyAssist(String input, List<Candidate> all) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null || all.isEmpty()) {
            return null;
        }
        try {
            StringBuilder rosterText = new StringBuilder("Roster:\n");
            for (int i = 0; i < all.size(); i++) {
                Candidate c = all.get(i);
                rosterText.append(i + 1).append(". ").append(c.userName())
                        .append(" (").append(nullSafe(c.departmentName())).append(" / ")
                        .append(nullSafe(c.positionName())).append(")\n");
            }
            List<Message> prompt = List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(rosterText + "\nPerson reference:\n" + input));
            String raw = client.prompt().messages(prompt).call().content();
            JsonNode parsed = objectMapper.readTree(extractJson(stripThink(raw)));
            Integer match = parsed.path("match").isNumber() ? parsed.path("match").asInt() : null;
            if (match != null && match >= 1 && match <= all.size()) {
                return new Resolution(input, all.get(match - 1), null, false);
            }
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode n : parsed.path("candidates")) {
                int idx = n.asInt();
                if (idx >= 1 && idx <= all.size()) {
                    candidates.add(all.get(idx - 1));
                }
            }
            return candidates.isEmpty() ? null : new Resolution(input, null, candidates, false);
        } catch (Exception e) {
            log.warn("Fuzzy traveler match failed for '{}': {}", input, e.getMessage());
            return null;
        }
    }

    private List<Candidate> toCandidates(JsonNode roster) {
        List<Candidate> all = new ArrayList<>();
        for (JsonNode u : roster.path("users")) {
            JsonNode dep0 = u.path("departments").path(0);
            all.add(new Candidate(
                    u.path("corporationUserId").asLong(),
                    u.path("userName").asText(null),
                    dep0.path("departmentName").asText(null),
                    u.path("positionName").asText(null)));
        }
        return all;
    }

    private static String nullSafe(String s) {
        return s == null ? "?" : s;
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
