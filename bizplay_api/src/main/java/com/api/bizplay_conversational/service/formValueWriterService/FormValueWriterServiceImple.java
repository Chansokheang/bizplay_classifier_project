package com.api.bizplay_conversational.service.formValueWriterService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormValueWriterServiceImple implements FormValueWriterService {

    /** 신청 toggles — real UI saves "true"/"false" strings (captured from seah/bstr/save). */
    private static final java.util.Set<String> APPLY_TYPES = java.util.Set.of(
            "EXPENSE_BEYOND_BSTR_PERIOD", "DAILY_COST", "FUEL_COST", "REQUEST_STAFF_LODGE");
    /** Yes/No items — real UI saves "true"/"false" strings (value2 carries optional sub-choices). */
    private static final java.util.Set<String> YN_TYPES = java.util.Set.of(
            "PARTNER_SUPPORT", "NATIONAL_PROJECT", "NOTEBOOK_EXPORT");

    private final ObjectMapper objectMapper;

    @Override
    public List<String> apply(ObjectNode document, JsonNode fields, ObjectNode state, JsonNode mappedValues) {
        List<String> applied = new ArrayList<>();
        if (mappedValues == null || !mappedValues.isObject()) {
            return applied;
        }
        dropEchoedDestination(state, (ObjectNode) mappedValues);
        mappedValues.fields().forEachRemaining(e -> {
            JsonNode field = findField(fields, e.getKey());
            if (field == null) {
                log.info("Mapper produced unknown field key '{}' — ignored.", e.getKey());
                return;
            }
            try {
                String desc = write(document, state, field, e.getValue());
                if (desc != null) {
                    applied.add(desc);
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to write field '{}': {}", e.getKey(), ex.getMessage());
            }
        });
        return applied;
    }

    @Override
    public List<String> missingRequired(JsonNode document, JsonNode fields, JsonNode state) {
        List<String> missing = new ArrayList<>();
        for (JsonNode field : fields) {
            if (field.path("required").asBoolean(false) && !isFilled(document, state, field)) {
                missing.add(field.path("label").asText(field.path("key").asText()));
            }
        }
        return missing;
    }

    /**
     * The mapper LLM sometimes re-extracts a word from the SAME turn's title/content as a new
     * destination (e.g. "파트너사" out of "파트너사 미팅"). When a destination is already set and the
     * candidate is just an echo of another mapped text field, drop the update instead of
     * overwriting a real place the user gave earlier.
     */
    private void dropEchoedDestination(JsonNode state, ObjectNode mappedValues) {
        String existing = text(state.path("destination"));
        if (existing == null) {
            return;
        }
        String title = text(mappedValues.path("basic:BASIC_TITLE"));
        String content = text(mappedValues.path("basic:BASIC_CONTENT"));
        if (title == null && content == null) {
            return;
        }
        java.util.function.Predicate<String> echoed = v -> v != null && !v.equalsIgnoreCase(existing)
                && ((title != null && title.contains(v)) || (content != null && content.contains(v)));

        if (echoed.test(text(mappedValues.path("basic:DESTINATION")))) {
            log.info("Dropped destination '{}' — echoed from this turn's title/content; keeping '{}'.",
                    text(mappedValues.path("basic:DESTINATION")), existing);
            mappedValues.remove("basic:DESTINATION");
        }
        mappedValues.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v != null && v.isObject() && echoed.test(text(v.path("destination")))) {
                log.info("Dropped period destination '{}' — echoed from this turn's title/content; keeping '{}'.",
                        text(v.path("destination")), existing);
                ((ObjectNode) v).remove("destination");
            }
        });
    }

    // --- write dispatch ----------------------------------------------------------

    /** Returns a short "label = value" description when something was written; null otherwise. */
    private String write(ObjectNode document, ObjectNode state, JsonNode field, JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        String key = field.path("key").asText();
        String label = field.path("label").asText(key);

        switch (key) {
            case "basic:BASIC_TITLE" -> {
                String v = text(value);
                if (v == null) return null;
                document.put("title", v); // value-only update: slot exists in the retrieved structure
                return label + " = " + v;
            }
            case "basic:BASIC_CONTENT" -> {
                String v = text(value);
                if (v == null) return null;
                document.put("content", v);
                return label + " = " + v;
            }
            case "basic:DESTINATION" -> {
                String v = text(value);
                if (v == null) return null;
                // No destination slot exists in the save body — it rides the BSTR_PERIOD
                // selectionAreaInfo. Keep it in agent state and refresh those selections.
                state.put("destination", v);
                refreshPeriodSelections(document, state);
                return label + " = " + v;
            }
            case "basic:ORIGIN" -> {
                String v = text(value);
                if (v == null) return null;
                // Like the destination, the departure place has no slot in the save body —
                // it lives in agent state (place-validated, shown in previews).
                state.put("origin", v);
                return label + " = " + v;
            }
            case "basic:BASIC_TRAVELER" -> {
                // Traveler NAMES have no slot in the body (it is one document per corporationUserId).
                // Held in agent state until the BizPlay user-search API can resolve ids.
                return mergeTravelers(state, value, label);
            }
            default -> {
                if (key.startsWith("item:")) {
                    return writeItem(document, state, field, value, label);
                }
                log.info("No writer for field key '{}' — ignored.", key);
                return null;
            }
        }
    }

    /** Custom item write: dedicated behavior per known type, then shape-based fallback. */
    private String writeItem(ObjectNode document, ObjectNode state, JsonNode field, JsonNode value, String label) {
        ObjectNode issued = findIssuedItem(document, field.path("itemId").asLong());
        if (issued == null) {
            return null;
        }
        String itemType = field.path("type").asText("");

        if ("BSTR_LINKED_LEAVE".equals(itemType)) {
            return writeLinkedLeave(issued, value, label);
        }

        if ("BSTR_PERIOD".equals(itemType) || value.has("start") || value.has("end")) {
            return writePeriod(document, state, issued, value, label);
        }

        if ("EDUCATION_INFO".equals(itemType)) {
            return writeEducationInfo(document, issued, value, label);
        }

        if ("BSTR_ROUTE".equals(itemType)) {
            // Company storage spec: routes NEVER live in the issued item — they go to the
            // document's top-level bstrRoutes[] (route row shape pending a real-UI capture).
            // Hold the description in agent state so nothing the user said is lost.
            String note = value.isObject() ? text(value.path("choice")) : text(value);
            if (note == null) {
                return null;
            }
            state.put("routeNote", note);
            return label + " = " + note + " (경로는 bstrRoutes[]로 저장 예정)";
        }

        // Real-UI captures: 신청 toggles and Yes/No items store STRING booleans, and the
        // insurance item stores its radio codes — not Korean labels.
        if (APPLY_TYPES.contains(itemType) || YN_TYPES.contains(itemType)) {
            String raw = value.isObject() ? text(value.path("choice")) : text(value);
            if (raw == null) {
                return null;
            }
            boolean yes = !(raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("no")
                    || raw.contains("아니") || raw.equalsIgnoreCase("n") || raw.contains("미신청"));
            issued.put("value", yes ? "true" : "false");
            writeSubChoice(issued, value);
            if ("DAILY_COST".equals(itemType)) {
                writeDailyCostRows(document, field, issued, yes);
            }
            if ("PARTNER_SUPPORT".equals(itemType)) {
                writePartnerDetails(issued, value);
            }
            return label + " = " + (yes ? "true" : "false");
        }
        if ("OVERSEAS_INSURANCE".equals(itemType)) {
            String raw = value.isObject() ? text(value.path("choice")) : text(value);
            if (raw == null) {
                return null;
            }
            String code = raw.contains("왕복") || raw.equalsIgnoreCase("ROUND_TRIP") ? "ROUND_TRIP"
                    : raw.contains("편도") || raw.equalsIgnoreCase("ONE_WAY") ? "ONE_WAY" : raw;
            issued.put("value", code);
            writeSubChoice(issued, value);
            return label + " = " + code;
        }

        // Option ROWS live in the field spec — the document's issued item is slim ({id,itemType,name}).
        JsonNode itemList = field.path("optionItems");
        String choice = value.isObject() ? text(value.path("choice")) : text(value);
        if (itemList.isArray() && itemList.size() > 0 && choice != null) {
            // Select-like (BSTR_SELECT and anything with options): pick the configured option.
            JsonNode opt = matchOption(itemList, choice);
            if (opt != null) {
                ObjectNode sel = objectMapper.createObjectNode();
                sel.set("selectionId", opt.path("id").deepCopy());
                sel.put("selectionName", opt.path("name").asText());
                sel.put("selectionErpCode", opt.path("erpCode").asText(""));
                ArrayNode selections = objectMapper.createArrayNode();
                selections.add(sel);
                issued.set("selections", selections);
                issued.putNull("value");
                return label + " = " + opt.path("name").asText();
            }
            // No option matched: keep the user's words as text so nothing is invented.
            issued.put("value", choice);
            return label + " = " + choice + " (free text — no matching option)";
        }

        // Text-like (HTML and unknown types without options): plain value.
        String v = choice != null ? choice : text(value);
        if (v == null) {
            return null;
        }
        issued.put("value", v);
        return label + " = " + v;
    }

    /**
     * EDUCATION_INFO — encoding captured from a REAL BizPlay UI save: the values live in the
     * document's top-level {@code bstrEdus[]} array, NOT in the issued item's value:
     * {@code {eduType: INTERNAL|EXTERNAL, eduCourse, eduClass, eduStartDate, eduEndDate,
     * eduInstitution, content}} (unset parts are EMPTY STRINGS, matching the capture).
     * Accepts a structured object (UI composite / mapper), or our own "구분: … / 교육과정: …"
     * aggregate string, or free text (which becomes the content field).
     */
    private String writeEducationInfo(ObjectNode document, ObjectNode issued, JsonNode value, String label) {
        java.util.Map<String, String> p = new java.util.HashMap<>();
        if (value.isObject()) {
            value.fields().forEachRemaining(e -> {
                String v = text(e.getValue());
                if (v != null) {
                    p.put(e.getKey(), v);
                }
            });
        } else {
            String s = text(value);
            if (s == null) {
                return null;
            }
            // Our aggregate format parses deterministically; anything else is content.
            for (String part : s.split("\\s*/\\s*")) {
                int colon = part.indexOf(':');
                if (colon > 0) {
                    p.put(part.substring(0, colon).trim(), part.substring(colon + 1).trim());
                }
            }
            if (p.isEmpty()) {
                p.put("내용", s);
            }
        }
        String gubun = firstOf(p, "구분", "eduType");
        String type = (gubun != null && (gubun.contains("사외") || gubun.equalsIgnoreCase("EXTERNAL")))
                ? "EXTERNAL" : "INTERNAL";
        String course = orEmpty(firstOf(p, "교육과정", "eduCourse"));
        String clazz = orEmpty(firstOf(p, "교육클래스", "eduClass"));
        String start = orEmpty(firstOf(p, "교육시작일", "eduStartDate"));
        String end = orEmpty(firstOf(p, "교육종료일", "eduEndDate"));
        String institution = orEmpty(firstOf(p, "교육기관", "eduInstitution"));
        String content = orEmpty(firstOf(p, "내용", "content", "choice"));
        if (course.isEmpty() && clazz.isEmpty() && start.isEmpty() && end.isEmpty()
                && institution.isEmpty() && content.isEmpty()) {
            return null;   // nothing real was provided (a bare 구분 radio default is not a value)
        }
        ObjectNode edu = objectMapper.createObjectNode();
        edu.put("eduType", type);
        edu.put("eduCourse", course);
        edu.put("eduClass", clazz);
        edu.put("eduStartDate", start);
        edu.put("eduEndDate", end);
        edu.put("eduInstitution", institution);
        edu.put("content", content);
        ArrayNode edus = objectMapper.createArrayNode();
        edus.add(edu);
        document.set("bstrEdus", edus);
        issued.put("value", type);   // real UI: the issued item's value carries the eduType
        return label + " = " + (content.isEmpty() ? course + " " + institution : content).trim();
    }

    /** Sub-choice (captured in real saves as value2: PARTNER_REGISTERED, OVER_90_DAYS, …). */
    private void writeSubChoice(ObjectNode issued, JsonNode value) {
        String sub = value.isObject() ? text(value.path("sub")) : null;
        if (sub != null) {
            issued.put("value2", sub);
        }
    }

    /**
     * BSTR_LINKED_LEAVE — company storage spec (slide "복합 항목"): leave start = value,
     * leave end = value2, stay region/purpose = a selections row. Accepts a structured
     * object ({start,end,region,purpose}), our UI aggregate string
     * ("시작: … / 종료: … / 체류지역: … / 체류목적: …"), or free text (→ purpose).
     * The selections row mirrors the period picker family: region in selectionAreaInfo,
     * purpose in selectionMemo (field mapping to be confirmed by a real-UI capture).
     */
    private String writeLinkedLeave(ObjectNode issued, JsonNode value, String label) {
        java.util.Map<String, String> p = new java.util.HashMap<>();
        if (value.isObject()) {
            value.fields().forEachRemaining(e -> {
                String v = text(e.getValue());
                if (v != null) {
                    p.put(e.getKey(), v);
                }
            });
        } else {
            String s = text(value);
            if (s == null) {
                return null;
            }
            for (String part : s.split("\\s*/\\s*")) {
                int colon = part.indexOf(':');
                if (colon > 0) {
                    p.put(part.substring(0, colon).trim(), part.substring(colon + 1).trim());
                }
            }
            if (p.isEmpty()) {
                p.put("체류목적", s);
            }
        }
        String start = firstOf(p, "시작", "start");
        String end = firstOf(p, "종료", "end");
        String region = firstOf(p, "체류지역", "region");
        String purpose = firstOf(p, "체류목적", "purpose", "choice");
        if (start == null && end == null && region == null && purpose == null) {
            return null;
        }
        if (start != null) {
            issued.put("value", start);
        }
        if (end != null) {
            issued.put("value2", end);
        }
        if (region != null || purpose != null) {
            ObjectNode sel = objectMapper.createObjectNode();
            sel.putNull("selectionId");
            sel.putNull("selectionName");
            sel.putNull("selectionErpCode");
            if (purpose != null) {
                sel.put("selectionMemo", purpose);
            } else {
                sel.putNull("selectionMemo");
            }
            if (region != null) {
                sel.put("selectionAreaInfo", "{\"name\":\"" + region.replace("\"", "\\\"") + "\"}");
            } else {
                sel.putNull("selectionAreaInfo");
            }
            ArrayNode selections = objectMapper.createArrayNode();
            selections.add(sel);
            issued.set("selections", selections);
        }
        List<String> parts = new ArrayList<>();
        if (start != null || end != null) {
            parts.add(orEmpty(start) + " ~ " + orEmpty(end));
        }
        if (region != null) {
            parts.add(region);
        }
        if (purpose != null) {
            parts.add(purpose);
        }
        return label + " = " + String.join(" · ", parts);
    }

    /**
     * DAILY_COST — real-UI capture: applying for daily cost stores value "true" AND one
     * selections row per trip day ({selectionName: yyyy-MM-dd, everything else null})
     * for day-confirm items (item.requestWay = PAY_DAY_CONFIRM). Rows are rebuilt from
     * the document's period; cleared when the user declines.
     */
    private void writeDailyCostRows(ObjectNode document, JsonNode field, ObjectNode issued, boolean yes) {
        if (!yes) {
            issued.set("selections", objectMapper.createArrayNode());
            return;
        }
        // requestWay is form configuration — it rides the field spec, not the slim issued item.
        if (!"PAY_DAY_CONFIRM".equals(field.path("requestWay").asText())) {
            return;
        }
        String start = isoDate(document.path("bstrStartDate"));
        String end = isoDate(document.path("bstrEndDate"));
        if (start == null) {
            return;
        }
        java.time.LocalDate from = java.time.LocalDate.parse(start);
        java.time.LocalDate to = end != null ? java.time.LocalDate.parse(end) : from;
        if (to.isBefore(from)) {
            to = from;
        }
        ArrayNode rows = objectMapper.createArrayNode();
        for (java.time.LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            ObjectNode row = objectMapper.createObjectNode();
            row.putNull("selectionId");
            row.putNull("selectionErpCode");
            row.put("selectionName", d.toString());
            row.putNull("selectionMemo");
            row.putNull("selectionAreaInfo");
            rows.add(row);
        }
        issued.set("selections", rows);
    }

    /**
     * PARTNER_SUPPORT — company storage spec: beyond value (Y/N) and value2 (partner
     * type), the partner COMPANY is a selections row and the visit purpose rides the
     * "text" slot (onChangeValue's 4th argument, serialized on the issued item).
     */
    private void writePartnerDetails(ObjectNode issued, JsonNode value) {
        if (!value.isObject()) {
            return;
        }
        String partner = text(value.path("partner"));
        if (partner != null) {
            ObjectNode sel = objectMapper.createObjectNode();
            sel.putNull("selectionId");
            sel.put("selectionName", partner);
            sel.putNull("selectionErpCode");
            ArrayNode selections = objectMapper.createArrayNode();
            selections.add(sel);
            issued.set("selections", selections);
        }
        String purpose = text(value.path("purpose"));
        if (purpose != null) {
            issued.put("text", purpose);
        }
    }

    private static String firstOf(java.util.Map<String, String> map, String... keys) {
        for (String key : keys) {
            String v = map.get(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** BSTR_PERIOD: top-level dates + the save body's selections encoding (start/name, end/erpCode). */
    private String writePeriod(ObjectNode document, ObjectNode state, ObjectNode issued, JsonNode value, String label) {
        String start = text(value.path("start"));
        String end = text(value.path("end"));
        String memo = text(value.path("memo"));
        String destination = text(value.path("destination"));
        if (destination != null) {
            state.put("destination", destination);
        }
        String origin = text(value.path("origin"));
        if (origin != null) {
            state.put("origin", origin);
        }
        if (memo != null) {
            state.put("periodMemo", memo);
        }
        if (start != null) {
            document.put("bstrStartDate", start + "T00:00:00.000Z");
        }
        if (end != null) {
            document.put("bstrEndDate", end + "T00:00:00.000Z");
        }
        writePeriodSelections(document, state, issued);
        refreshDailyCostRows(document, state);
        String startText = isoDate(document.path("bstrStartDate"));
        String endText = isoDate(document.path("bstrEndDate"));
        return (startText == null && endText == null) ? null
                : label + " = " + startText + " .. " + endText;
    }

    /** Period changed: day-confirm daily-cost rows follow the trip dates, so rebuild them. */
    private void refreshDailyCostRows(ObjectNode document, JsonNode state) {
        for (JsonNode issued : document.path("issuedItems")) {
            if ("DAILY_COST".equals(issued.path("item").path("itemType").asText())
                    && "true".equals(issued.path("value").asText())) {
                writeDailyCostRows(document, fieldForItem(state, issued.path("item").path("id").asLong()),
                        (ObjectNode) issued, true);
            }
        }
    }

    /** The field spec of one issued item (config lives there now that the document is slim). */
    private JsonNode fieldForItem(JsonNode state, long itemId) {
        for (JsonNode f : state.path("fields")) {
            if (f.path("itemId").asLong() == itemId) {
                return f;
            }
        }
        return objectMapper.createObjectNode();
    }

    /** (Re)build the period item's selections from the document dates + state destination/memo. */
    private void writePeriodSelections(ObjectNode document, JsonNode state, ObjectNode issued) {
        String start = isoDate(document.path("bstrStartDate"));
        String end = isoDate(document.path("bstrEndDate"));
        if (start == null && end == null) {
            return;
        }
        ObjectNode sel = objectMapper.createObjectNode();
        sel.putNull("selectionId");
        sel.put("selectionName", start != null ? start : "");
        sel.put("selectionErpCode", end != null ? end : "");
        String memo = text(state.path("periodMemo"));
        if (memo != null) {
            sel.put("selectionMemo", memo);
        } else {
            sel.putNull("selectionMemo");
        }
        String destination = text(state.path("destination"));
        if (destination != null) {
            sel.put("selectionAreaInfo", "{\"name\":\"" + destination.replace("\"", "\\\"") + "\"}");
        } else {
            sel.putNull("selectionAreaInfo");
        }
        ArrayNode selections = objectMapper.createArrayNode();
        selections.add(sel);
        issued.set("selections", selections);
    }

    /** Destination changed: refresh the period selections so areaInfo stays in sync. */
    private void refreshPeriodSelections(ObjectNode document, JsonNode state) {
        for (JsonNode issued : document.path("issuedItems")) {
            if ("BSTR_PERIOD".equals(issued.path("item").path("itemType").asText())) {
                writePeriodSelections(document, state, (ObjectNode) issued);
            }
        }
    }

    private String mergeTravelers(ObjectNode state, JsonNode value, String label) {
        JsonNode names = value.isObject() ? value.path("names") : value;
        if (!names.isArray() || names.isEmpty()) {
            return null;
        }
        ArrayNode travelers = state.withArray("travelers");
        List<String> added = new ArrayList<>();
        for (JsonNode n : names) {
            String name = text(n);
            if (name == null) {
                continue;
            }
            boolean exists = false;
            for (JsonNode t : travelers) {
                if (name.equalsIgnoreCase(t.asText())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                travelers.add(name);
                added.add(name);
            }
        }
        return added.isEmpty() ? null : label + " = " + String.join(", ", added);
    }

    // --- emptiness ---------------------------------------------------------------

    private boolean isFilled(JsonNode document, JsonNode state, JsonNode field) {
        String key = field.path("key").asText();
        return switch (key) {
            case "basic:BASIC_TITLE" -> notBlank(document.path("title"));
            case "basic:BASIC_CONTENT" -> notBlank(document.path("content"));
            case "basic:DESTINATION" -> notBlank(state.path("destination"));
            case "basic:ORIGIN" -> notBlank(state.path("origin"));
            case "basic:BASIC_TRAVELER" -> state.path("travelers").size() > 0;
            default -> {
                if (!key.startsWith("item:")) {
                    yield true; // unknown basics never block completion
                }
                if ("EDUCATION_INFO".equals(field.path("type").asText())
                        && document.path("bstrEdus").size() > 0) {
                    yield true;   // education values live in bstrEdus, not the issued item
                }
                if ("BSTR_ROUTE".equals(field.path("type").asText())) {
                    // Routes live in bstrRoutes[] (never the issued item); until that array
                    // is wired, a held route note also counts so required routes don't block.
                    yield document.path("bstrRoutes").size() > 0 || notBlank(state.path("routeNote"));
                }
                JsonNode issued = findIssuedItemView(document, field.path("itemId").asLong());
                yield issued != null
                        && (notBlank(issued.path("value")) || issued.path("selections").size() > 0);
            }
        };
    }

    // --- helpers -----------------------------------------------------------------

    private JsonNode findField(JsonNode fields, String key) {
        for (JsonNode field : fields) {
            if (key.equals(field.path("key").asText())) {
                return field;
            }
        }
        return null;
    }

    private ObjectNode findIssuedItem(ObjectNode document, long itemId) {
        for (JsonNode issued : document.path("issuedItems")) {
            if (issued.path("item").path("id").asLong() == itemId) {
                return (ObjectNode) issued;
            }
        }
        return null;
    }

    private JsonNode findIssuedItemView(JsonNode document, long itemId) {
        for (JsonNode issued : document.path("issuedItems")) {
            if (issued.path("item").path("id").asLong() == itemId) {
                return issued;
            }
        }
        return null;
    }

    private JsonNode matchOption(JsonNode itemList, String choice) {
        String c = choice.trim().toLowerCase(Locale.ROOT);
        for (JsonNode opt : itemList) {
            if (opt.path("name").asText("").trim().toLowerCase(Locale.ROOT).equals(c)) {
                return opt;
            }
        }
        for (JsonNode opt : itemList) {
            String name = opt.path("name").asText("").trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && (name.contains(c) || c.contains(name))) {
                return opt;
            }
        }
        return null;
    }

    private static String isoDate(JsonNode node) {
        String v = text(node);
        if (v == null) {
            return null;
        }
        int t = v.indexOf('T');
        return t > 0 ? v.substring(0, t) : v;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String v = node.asText(null);
        return (v == null || v.isBlank() || "null".equalsIgnoreCase(v)) ? null : v.trim();
    }

    private static boolean notBlank(JsonNode node) {
        return text(node) != null;
    }
}
