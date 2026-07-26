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

        if ("BSTR_PERIOD".equals(itemType) || value.has("start") || value.has("end")) {
            return writePeriod(document, state, issued, value, label);
        }

        // Options come from itemList, or from labelItems for BSTR_SELECT-style items.
        JsonNode itemList = issued.path("item").path("itemList");
        if (!itemList.isArray() || itemList.isEmpty()) {
            itemList = issued.path("item").path("labelItems");
        }
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

    /** BSTR_PERIOD: top-level dates + the save body's selections encoding (start/name, end/erpCode). */
    private String writePeriod(ObjectNode document, ObjectNode state, ObjectNode issued, JsonNode value, String label) {
        String start = text(value.path("start"));
        String end = text(value.path("end"));
        String memo = text(value.path("memo"));
        String destination = text(value.path("destination"));
        if (destination != null) {
            state.put("destination", destination);
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
        String startText = isoDate(document.path("bstrStartDate"));
        String endText = isoDate(document.path("bstrEndDate"));
        return (startText == null && endText == null) ? null
                : label + " = " + startText + " .. " + endText;
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
            case "basic:BASIC_TRAVELER" -> state.path("travelers").size() > 0;
            default -> {
                if (!key.startsWith("item:")) {
                    yield true; // unknown basics never block completion
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
