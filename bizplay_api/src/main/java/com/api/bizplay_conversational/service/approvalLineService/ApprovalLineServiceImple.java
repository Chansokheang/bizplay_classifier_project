package com.api.bizplay_conversational.service.approvalLineService;

import com.api.bizplay_conversational.config.BizplayProperties;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.api.bizplay_conversational.service.destinationResolverAgentService.DestinationResolverAgentService;
import com.api.bizplay_conversational.service.slotFillerAgentService.SlotFillerAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The 결재선 both agents share. The plan asks for one before it is filed; BizPlay's own screen asks
 * again before a 정산서 is filed, so the same editor serves both — one place where "add 김도하",
 * "김비플 빼줘" and "김도하 대신 김철수" mean the same thing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalLineServiceImple implements ApprovalLineService {

    private final BizplayGatewayService bizplayGatewayService;
    private final SlotFillerAgentService slotFillerAgentService;
    private final DestinationResolverAgentService destinationResolverAgentService;
    private final BizplayProperties bizplayProperties;
    private final ObjectMapper objectMapper;

    /** Reply fragment in the conversation's language — never mix the two in one turn. */
    private static String t(boolean ko, String en, String kr) {
        return ko ? kr : en;
    }

    /** The UI prefixes each message with a context block; the traveller's own words are what count. */
    private String stripChatContext(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceFirst("(?s)^\\(Current user:.*?\\)\\s*", "").trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** currentCorpId from the (unverified) JWT payload — a lookup key, not authentication. */
    private Long corporationIdFromToken(String token) {
        try {
            String jwt = (token == null || token.isBlank())
                    ? bizplayProperties.getDevToken() : token.trim();
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] raw = java.util.Base64.getUrlDecoder().decode(
                    parts[1] + "=".repeat((4 - parts[1].length() % 4) % 4));
            JsonNode claims = objectMapper.readTree(raw);
            long id = claims.path("currentCorpId").asLong(claims.path("corporationId").asLong(0));
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * One edit to the approval line, in the traveller's own words. Three things can be asked for —
     * add somebody, remove somebody, swap one for another — and the parse is a single judged pass
     * so "김도하 넣어줘", "김충북 빼줘" and "change approval from 김충북 to 김도하" all land.
     *
     * <p>Nothing is accepted on a guess: a name that does not resolve to a real person of this
     * corporation returns null, and the caller asks again. That is what let "asdfasdf" put the
     * first person in the roster on the line.
     *
     * @return the reply to send, or null when the message asked for no such edit
     */
    @Override
    public String applyEdit(ObjectNode state, String message, String token, boolean ko,
                            java.util.List<String> turns) {
        String said = stripChatContext(message == null ? "" : message).trim();
        if (said.isBlank() || said.length() > 200) {
            return null;
        }
        JsonNode people;
        try {
            Long corporationId = corporationIdFromToken(token);
            people = corporationId == null ? null
                    : bizplayGatewayService.getCorporationUsers(corporationId, token);
        } catch (RuntimeException e) {
            log.warn("Approver lookup failed while reading '{}': {}", truncate(said, 30), e.getMessage());
            return null;
        }
        JsonNode users = people == null ? null : people.path("users");
        if (users == null || !users.isArray() || users.isEmpty()) {
            return null;
        }

        // What KIND of edit, and about whom. One pass, so a sentence naming two people ("change A
        // to B") is read as one instruction instead of two adds.
        JsonNode parsed;
        try {
            parsed = slotFillerAgentService.extract(said, java.util.Map.of(
                    "action", "The assistant is building the plan's APPROVAL LINE (결재선). Read this "
                            + "message and answer with EXACTLY one word. "
                            + "\"add\" - it puts ONE person on the line, with or without a role "
                            + "(김도하 넣어줘 / 김비플 님은 합의로 넣어줘 / 결재자는 김철수). "
                            + "\"remove\" - it takes somebody off (김충북 빼줘 / remove 김충북). "
                            + "\"replace\" - it names TWO people, one replacing the other "
                            + "(김도하 대신 김철수 / change approval from A to B). A message naming "
                            + "only ONE person is never a replace, whatever particle follows the "
                            + "name. \"none\" - anything else, including text naming nobody. "
                            + "Any language",
                    "person", "the person this message is about — the one to ADD, the one to "
                            + "REMOVE, or, for a replace, the one being REPLACED. Their name as "
                            + "written. Omit the field when the message names nobody",
                    "replacement", "for a replace only: the person taking their place. Omit "
                            + "otherwise",
                    "role", "the role named for them, if any: 결재 / 합의 / 참조 / approval / "
                            + "agree / reference. Omit when not stated"),
                    ko, turns);
        } catch (Exception e) {
            log.warn("Approval-line edit judge unavailable: {}", e.getMessage());
            return null;
        }
        String action = parsed.path("action").asText("").trim().toLowerCase(java.util.Locale.ROOT);
        if (action.isBlank() || "none".equals(action)) {
            return null;
        }
        JsonNode who = resolvePerson(users, parsed.path("person").asText(""), said, turns, ko);
        if (who == null) {
            // Nobody resolved: this handler runs before the mapper, so it must not consume a turn
            // it cannot act on — "출장지를 오사카로 바꿔줘" is an edit, not an approver.
            log.info("[APPR] '{}' resolved to no person — leaving the turn to the normal flow.",
                    truncate(said, 30));
            return null;
        }
        ArrayNode lines = state.withArray("approvalLines");
        long id = who.path("corporationUserId").asLong();
        String name = who.path("userName").asText("");

        if ("remove".equals(action)) {
            ArrayNode kept = objectMapper.createArrayNode();
            boolean removed = false;
            for (JsonNode l : lines) {
                if (l.path("corporationUserId").asLong() == id) {
                    removed = true;
                } else {
                    kept.add(l);
                }
            }
            state.set("approvalLines", kept);
            log.info("[APPR] {} ({}) removed from the approval line.", name, id);
            return removed
                    ? t(ko, "Removed " + name + " from the approval line.",
                            name + " 님을 결재선에서 뺐어요.")
                    : t(ko, name + " was not on the approval line.",
                            name + " 님은 결재선에 없었어요.");
        }

        if ("replace".equals(action)) {
            JsonNode with = resolvePerson(users, parsed.path("replacement").asText(""), said, turns, ko);
            if (with == null) {
                return t(ko, "Who should replace " + name + " on the approval line?",
                        name + " 님 대신 누구를 넣을까요?");
            }
            long newId = with.path("corporationUserId").asLong();
            String newName = with.path("userName").asText("");
            if (newId == id) {
                return t(ko, name + " is already on the approval line.",
                        name + " 님은 이미 결재선에 있어요.");
            }
            boolean swapped = false;
            for (JsonNode l : lines) {
                if (l.path("corporationUserId").asLong() == id && l instanceof ObjectNode o) {
                    o.put("corporationUserId", newId);
                    o.put("name", newName);
                    swapped = true;
                }
            }
            if (!swapped) {
                return t(ko, name + " is not on the approval line — should I add " + newName + "?",
                        name + " 님은 결재선에 없어요 — " + newName + " 님을 추가할까요?");
            }
            log.info("[APPR] {} replaced by {} on the approval line.", name, newName);
            return t(ko, "Replaced " + name + " with " + newName + " on the approval line.",
                    "결재선에서 " + name + " 님을 " + newName + " 님으로 바꿨어요.");
        }

        // add
        String roleWord = parsed.path("role").asText("").toLowerCase(java.util.Locale.ROOT);
        String kind = (roleWord.contains("합의") || roleWord.contains("agree")) ? "AGREE"
                : (roleWord.contains("참조") || roleWord.contains("reference")) ? "REFERENCE"
                : "APPROVAL";
        for (JsonNode existing : lines) {
            if (existing.path("corporationUserId").asLong() == id) {
                return t(ko, name + " is already on the approval line.",
                        name + " 님은 이미 결재선에 있어요.");
            }
        }
        ObjectNode line = lines.addObject();
        line.put("corporationUserId", id);
        line.put("approvalKindType", kind);
        line.put("name", name);
        log.info("[APPR] {} ({}) added to the approval line as {}.", name, id, kind);
        String role = t(ko, kind.equals("AGREE") ? "agreement" : kind.equals("REFERENCE")
                        ? "reference" : "approval",
                kind.equals("AGREE") ? "합의" : kind.equals("REFERENCE") ? "참조" : "결재");
        return t(ko,
                "Added " + name + " to the approval line (" + role + "). Anyone else? "
                        + "If that's everyone, say so and I'll file the plan.",
                name + " 님을 결재선에 추가했어요 (" + role + "). 더 넣을 분 있나요? "
                        + "없으면 말씀해 주시면 바로 상신할게요.");
    }

    /**
     * A named person, resolved against this corporation's own user list. Exact-name first (a chip
     * sends the name verbatim), then the semantic picker — whose answer is CONFIRMED, because a
     * picker asked "which one?" answers with one even for typed noise.
     */
    private JsonNode resolvePerson(JsonNode users, String named, String wholeMessage,
                                   java.util.List<String> turns, boolean ko) {
        String want = named == null ? "" : named.trim();
        java.util.List<String> names = new ArrayList<>();
        for (JsonNode u : users) {
            names.add(u.path("userName").asText(""));
        }
        if (!want.isBlank()) {
            for (JsonNode u : users) {
                String n = u.path("userName").asText("");
                if (!n.isBlank() && (n.equals(want) || want.contains(n))) {
                    return u;
                }
            }
        }
        String haystack = want.isBlank() ? wholeMessage : want;
        Integer pick = destinationResolverAgentService.pickDestination(names, haystack,
                "Each option is an employee of this corporation. Choose the one this message names "
                        + "or describes — by name, by team, by title. Choose NOTHING when it names "
                        + "no person.", ko);
        if (pick == null || pick < 0 || pick >= users.size()) {
            return null;
        }
        String candidate = users.get(pick).path("userName").asText("");
        return namesThisPerson(haystack, candidate, turns, ko) ? users.get(pick) : null;
    }

    /**
     * Step one: one chip per Travel Purpose. Candidates come from the resolver (it may already have
     * narrowed things), but the segments are looked up across the WHOLE catalog so a purpose whose
     * trip types were filtered out still leads somewhere.
     */
    /**
     * Does this message really name or describe this person? The picker proposes; this decides.
     * Without it any typed noise resolves to whoever the model liked best.
     */
    private boolean namesThisPerson(String message, String person, java.util.List<String> turns,
                                    boolean ko) {
        if (person == null || person.isBlank()) {
            return false;
        }
        try {
            String verdict = slotFillerAgentService.extract(message, java.util.Map.of(
                    "namesThem", "The assistant asked WHO should approve the trip plan. Judge THIS "
                            + "message: EXACTLY \"yes\" if it names or clearly describes the "
                            + "person \"" + person + "\" (their name, their title, their team). "
                            + "EXACTLY \"no\" if it is anything else — random characters, a "
                            + "question, a different subject, or a name that is not theirs"),
                    ko, turns).path("namesThem").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            return "yes".equals(verdict);
        } catch (Exception e) {
            log.warn("Approver confirmation unavailable: {}", e.getMessage());
            return false;   // unconfirmed is not confirmed
        }
    }

    /**
     * The 결재선 question's own options: the corporation's users, as the provider lists them.
     * Each option's sendText is the approver's NAME, which is what the typed path already
     * resolves — so clicking a chip and typing the name land in the same place. Role (결재 /
     * 합의 / 참조) is chosen in the next turn, exactly as it is when the name is typed.
     */
    @Override
    public List<TripPlanAgentResponse.PendingChoice> approverChoices(String token, boolean ko) {
        JsonNode people;
        try {
            Long corporationId = corporationIdFromToken(token);
            if (corporationId == null) {
                return null;   // no tenant in the token: the ask still goes out, just without chips
            }
            people = bizplayGatewayService.getCorporationUsers(corporationId, token);
        } catch (RuntimeException e) {
            log.warn("Approver list unavailable for the ask: {}", e.getMessage());
            return null;
        }
        JsonNode users = people == null ? null : people.path("users");
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (JsonNode u : (users != null && users.isArray()) ? users : objectMapper.createArrayNode()) {
            String name = u.path("userName").asText("");
            long id = u.path("corporationUserId").asLong(0);
            if (name.isBlank() || id <= 0) {
                continue;
            }
            String dept = u.path("departments").isArray() && u.path("departments").size() > 0
                    ? u.path("departments").get(0).path("departmentName").asText("") : "";
            String title = u.path("positionName").asText("");
            java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
            meta.put("corporationUserId", String.valueOf(id));
            if (!dept.isBlank()) {
                meta.put("department", dept);
            }
            if (!title.isBlank()) {
                meta.put("title", title);
            }
            String suffix = java.util.stream.Stream.of(dept, title)
                    .filter(x -> !x.isBlank()).reduce((a, b) -> a + " · " + b).orElse("");
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(suffix.isBlank() ? name : name + " (" + suffix + ")")
                    .sendText(name)
                    .meta(meta)
                    .build());
        }
        if (options.isEmpty()) {
            return null;
        }
        return List.of(TripPlanAgentResponse.PendingChoice.builder()
                .kind("APPROVAL_LINE")
                .name(t(ko, "Approval line", "결재선"))
                .options(options)
                .build());
    }

}
