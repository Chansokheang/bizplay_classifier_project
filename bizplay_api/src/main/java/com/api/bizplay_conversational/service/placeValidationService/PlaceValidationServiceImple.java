package com.api.bizplay_conversational.service.placeValidationService;

import com.api.bizplay_compliance.service.corpService.LocationGeocodeService;
import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;
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
import java.util.Set;

@Slf4j
@Service
public class PlaceValidationServiceImple implements PlaceValidationService {

    /** LLM judge — primary. Handles Hangul, romanized and landmark inputs alike. */
    private static final String SYSTEM_PROMPT = """
            You verify whether a destination is a place in South Korea (a city, region, district,
            neighbourhood or well-known landmark). The input may be Korean or romanized English.
            Return ONLY JSON, no prose: {"korean": true|false, "normalized": "<Korean si/do or city
            name, e.g. 부산>" or null}
            - "korean" true only for real places in South Korea; normalized = the Hangul name of the
              city/region it belongs to (a landmark normalizes to its city).
            - Not in South Korea, or not a place at all -> {"korean": false, "normalized": null}.
            /no_think
            """;

    /** 시/도 + major Korean cities — fallback gazetteer when the model is unreachable. */
    private static final Set<String> KOREAN_PLACES = Set.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "제주",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남",
            "수원", "성남", "고양", "용인", "부천", "안산", "안양", "화성", "평택", "의정부",
            "청주", "천안", "아산", "전주", "익산", "여수", "순천", "목포",
            "포항", "구미", "경주", "안동", "창원", "김해", "진주", "양산", "거제",
            "춘천", "원주", "강릉", "속초", "판교", "일산", "분당");

    /** English/romanized spellings — fallback aliases when the model is unreachable. */
    private static final Map<String, String> ROMANIZED = Map.ofEntries(
            Map.entry("seoul", "서울"), Map.entry("busan", "부산"),
            Map.entry("daegu", "대구"), Map.entry("incheon", "인천"),
            Map.entry("gwangju", "광주"), Map.entry("daejeon", "대전"),
            Map.entry("ulsan", "울산"), Map.entry("sejong", "세종"),
            Map.entry("jeju", "제주"), Map.entry("suwon", "수원"),
            Map.entry("seongnam", "성남"), Map.entry("goyang", "고양"),
            Map.entry("yongin", "용인"), Map.entry("bucheon", "부천"),
            Map.entry("ansan", "안산"), Map.entry("anyang", "안양"),
            Map.entry("hwaseong", "화성"), Map.entry("pyeongtaek", "평택"),
            Map.entry("cheongju", "청주"), Map.entry("cheonan", "천안"),
            Map.entry("asan", "아산"), Map.entry("jeonju", "전주"),
            Map.entry("iksan", "익산"), Map.entry("yeosu", "여수"),
            Map.entry("suncheon", "순천"), Map.entry("mokpo", "목포"),
            Map.entry("pohang", "포항"), Map.entry("gumi", "구미"),
            Map.entry("gyeongju", "경주"), Map.entry("andong", "안동"),
            Map.entry("changwon", "창원"), Map.entry("gimhae", "김해"),
            Map.entry("jinju", "진주"), Map.entry("yangsan", "양산"),
            Map.entry("geoje", "거제"), Map.entry("chuncheon", "춘천"),
            Map.entry("wonju", "원주"), Map.entry("gangneung", "강릉"),
            Map.entry("sokcho", "속초"), Map.entry("pangyo", "판교"),
            Map.entry("ilsan", "일산"), Map.entry("bundang", "분당"));

    private final LocationGeocodeService locationGeocodeService;
    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;
    private final AgentPromptService agentPromptService;

    @Value("${app.conversational.place-validator-agent.model:qwen3-14b}")
    private String modelName;

    public PlaceValidationServiceImple(LocationGeocodeService locationGeocodeService,
                                       Map<String, ChatClient> chatClientRegistry,
                                       LlmSettingsService llmSettingsService,
                                       ObjectMapper objectMapper,
                                       AgentPromptService agentPromptService) {
        this.locationGeocodeService = locationGeocodeService;
        this.chatClientRegistry = chatClientRegistry;
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
        this.agentPromptService = agentPromptService;
        agentPromptService.registerDefault("place-validator", SYSTEM_PROMPT);
    }

    @Override
    public Result validateKorean(String destination) {
        if (destination == null || destination.isBlank()) {
            return new Result(Result.Status.SKIPPED, null);
        }
        String d = destination.trim();

        Result llm = llmJudge(d);
        if (llm != null) {
            return llm;
        }
        return ruleFallback(d);
    }

    /** LLM-first judgement; null when the model is unreachable or answers garbage. */
    private Result llmJudge(String destination) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            return null;
        }
        try {
            List<Message> prompt = List.of(
                    new SystemMessage(agentPromptService.resolve("place-validator", SYSTEM_PROMPT)),
                    new UserMessage("Destination: " + destination));
            String raw = client.prompt().messages(prompt).call().content();
            String cleaned = raw == null ? "" : raw.replaceAll("(?is)<think>.*?</think>", "").trim();
            cleaned = cleaned.replace("```json", "").replace("```", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end < start) {
                return null;
            }
            JsonNode parsed = objectMapper.readTree(cleaned.substring(start, end + 1));
            if (!parsed.has("korean")) {
                return null;
            }
            if (parsed.path("korean").asBoolean(false)) {
                String normalized = parsed.path("normalized").asText(null);
                return new Result(Result.Status.VALID,
                        normalized == null || normalized.isBlank() ? destination : normalized);
            }
            return new Result(Result.Status.UNKNOWN, null);
        } catch (Exception e) {
            log.warn("LLM place validation unavailable for '{}' ({}) — using fallback.",
                    destination, e.getMessage());
            return null;
        }
    }

    /** Gazetteer + geocoder — only when the model is unreachable. */
    private Result ruleFallback(String d) {
        String lower = d.toLowerCase(java.util.Locale.ROOT);
        String alias = ROMANIZED.get(lower);
        if (alias == null) {
            alias = ROMANIZED.get(lower.split("[\\s,]+")[0]);
        }
        if (alias != null) {
            return new Result(Result.Status.VALID, alias);
        }
        for (String place : KOREAN_PLACES) {
            if (d.equals(place) || d.startsWith(place) || place.startsWith(d)) {
                return new Result(Result.Status.VALID, place);
            }
        }
        try {
            LocationGeocodeService.GeocodedLocation loc = locationGeocodeService.geocode(d);
            String road = loc.roadAddress() != null ? loc.roadAddress() : loc.jibunAddress();
            String normalized = (road == null || road.isBlank()) ? d : road.split("\\s+")[0];
            return new Result(Result.Status.VALID, normalized);
        } catch (IllegalStateException e) {
            // The geocoder throws this for an EMPTY result — the place genuinely wasn't found.
            log.info("Korean place validation: no geocode result for '{}'", d);
            return new Result(Result.Status.UNKNOWN, null);
        } catch (Exception e) {
            // Network / credential problems: validation is best-effort, never punish the user.
            log.warn("Korean place validation unavailable ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return new Result(Result.Status.SKIPPED, null);
        }
    }
}
