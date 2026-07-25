package com.api.bizplay_conversational.service.bizplayPlanAgentService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayFormResponse;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.model.response.PurposeOption;
import com.api.bizplay_conversational.model.response.PurposeResolutionResult;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.api.bizplay_conversational.repository.ConversationalAgentSessionRepo;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.api.bizplay_conversational.service.fieldMapperAgentService.FieldMapperAgentService;
import com.api.bizplay_conversational.service.formFollowUpAgentService.FormFollowUpAgentService;
import com.api.bizplay_conversational.service.formSkeletonService.FormSkeletonService;
import com.api.bizplay_conversational.service.formValueWriterService.FormValueWriterService;
import com.api.bizplay_conversational.config.BizplayProperties;
import com.api.bizplay_conversational.service.purposeSegmentAgentService.PurposeSegmentAgentService;
import com.api.bizplay_conversational.service.travelerResolverService.TravelerResolverService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrator of the BizPlay form-driven trip-plan flow.
 *
 * The session's draft_json is EXACTLY the plan-draft request body: an ARRAY of documents whose
 * structure comes 1:1 from the retrieved form (skeleton from ②) — only VALUES are updated in it,
 * never the structure. Everything the agent needs that has no slot in that body (staged messages,
 * unresolved traveler names, the destination that rides the period selections, the chosen purpose
 * label, the field spec cache key) lives in a separate agent-state entry inside chat_event_json.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizplayPlanAgentServiceImple implements BizplayPlanAgentService {

    private static final String STATE_ROLE = "agent_state";

    private final ConversationalAgentSessionRepo sessionRepo;
    private final BizplayGatewayService bizplayGatewayService;
    private final PurposeSegmentAgentService purposeSegmentAgentService;
    private final FormSkeletonService formSkeletonService;
    private final FieldMapperAgentService fieldMapperAgentService;
    private final FormValueWriterService formValueWriterService;
    private final FormFollowUpAgentService formFollowUpAgentService;
    private final TravelerResolverService travelerResolverService;
    private final BizplayProperties bizplayProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request, String bizplayToken) {
        if (request.getCorpNo() == null || request.getCorpNo().isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        if (request.getCorpUserId() == null || request.getCorpUserId().isBlank()) {
            throw new IllegalArgumentException("corpUserId is required.");
        }
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }

        ConversationalAgentSession session = resolveSession(request);
        ObjectNode state = loadState(session);
        ArrayNode documents = documents(session);

        String intent;
        List<String> subAgents = new ArrayList<>();
        List<TripPlanAgentResponse.PendingChoice> pendingChoices = null;
        StringBuilder reply = new StringBuilder();

        if (documents.isEmpty()) {
            // --- Sub-agent [A]: Purpose & Segment resolution -------------------------------------
            JsonNode catalog = bizplayGatewayService.getPurposeCatalog(request.getCorpUserId(), bizplayToken);
            List<PurposeOption> options = purposeSegmentAgentService.flattenCatalog(catalog);
            PurposeResolutionResult res = purposeSegmentAgentService.resolve(message, options);
            subAgents.add("PURPOSE_SEGMENT_AGENT");

            if (res.isResolved()) {
                intent = "FORM_LOAD";
                PurposeOption chosen = res.getResolved();
                // --- Sub-agent [B]: deterministic Form Builder (② -> request-body skeleton) ------
                JsonNode papers = bizplayGatewayService.getPapers(
                        chosen.getPurposeId(), chosen.getSegmentId(), bizplayToken);
                BizplayFormResponse form = formSkeletonService.buildPlanSkeleton(
                        papers, chosen.getPurposeId(), chosen.getSegmentId());
                ObjectNode document = (ObjectNode) form.getDocument();
                setDraftUser(document, request.getCorpUserId());
                documents.add(document);
                state.set("purpose", objectMapper.valueToTree(chosen));
                state.put("paperName", form.getPaperName());
                state.set("fields", objectMapper.valueToTree(form.getFields()));
                subAgents.add("FORM_BUILDER");
                reply.append("Trip type set to \"").append(chosen.getLabel())
                        .append("\" and the \"").append(form.getPaperName()).append("\" form is loaded. ");
                // Map everything said so far (staged turns + this message) onto the fresh form.
                fillFields(document, state, stagedPlus(state, message), subAgents, reply);
            } else {
                intent = "PURPOSE_SELECTION";
                state.withArray("staged").add(message);
                pendingChoices = List.of(toPendingChoice(res.getCandidates()));
                reply.append("Which Travel Purpose / Trip Type is this trip? Please pick one. ");
            }
        } else {
            java.util.regex.Matcher pick = TRAVELER_PICK.matcher(message);
            if (pick.matches()) {
                // Chip click from a traveler disambiguation: deterministic, no LLM involved.
                intent = "TRAVELER_PICK";
                applyTravelerPick(state, pick.group(1).trim(), Long.parseLong(pick.group(2)), pick.group(3).trim(), reply);
            } else {
                // Form already loaded: every turn is field completion on documents[0].
                intent = "FIELD_COMPLETION";
                fillFields((ObjectNode) documents.get(0), state, message, subAgents, reply);
            }
        }

        // Resolve pending traveler names against the corporation roster (gateway tool + chips).
        if (!documents.isEmpty()) {
            List<TripPlanAgentResponse.PendingChoice> travelerChips =
                    resolveTravelers(request, state, bizplayToken, reply, subAgents);
            if (pendingChoices == null && !travelerChips.isEmpty()) {
                pendingChoices = travelerChips;
            }
        }

        // --- Validation + Sub-agent [D]: follow-up question ---------------------------------------
        List<String> missing = List.of();
        if (!documents.isEmpty()) {
            missing = formValueWriterService.missingRequired(documents.get(0), state.path("fields"), state);
            if (missing.isEmpty()) {
                session.setStatus(ConversationalAgentSession.AgentStatus.READY_FOR_REVIEW);
                reply.append("All required fields are filled. Review the draft and create the plan when ready.");
            } else {
                session.setStatus(ConversationalAgentSession.AgentStatus.COLLECTING);
                reply.append(formFollowUpAgentService.composeFollowUp(
                        state.path("paperName").asText(null), missing));
                subAgents.add("FOLLOW_UP_AGENT");
            }
        }

        // Remember/refresh resolved traveler ids, then fan the master document out: one object per
        // traveler, exactly like the BizPlay save body (followers lack the drafter-only keys).
        if (request.getTravelerCorpUserIds() != null && !request.getTravelerCorpUserIds().isEmpty()) {
            state.set("travelerIds", objectMapper.valueToTree(request.getTravelerCorpUserIds()));
        }
        syncTravelerDocuments(documents, state, request.getCorpUserId());

        // Persist: draft_json = EXACTLY the request-body array; state rides in chat_event_json.
        session.setDraftJson(documents);
        appendTurn(session, "user", message);
        appendTurn(session, "assistant", reply.toString());
        saveState(session, state);
        sessionRepo.save(session);
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent(intent)
                .subAgents(subAgents)
                .reply(reply.toString().trim())
                .pendingChoices(pendingChoices)
                .missingFields(missing.isEmpty() ? null : missing)
                .travelers(travelerNames(state))
                .travelerIds(travelerIdList(state))
                .destination(state.path("destination").asText(null))
                .draftJson(saved.getDraftJson())
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    @Override
    @Transactional
    public BizplayPlanAgentResponse createPlan(String sessionId, String corpNo, String bizplayToken,
                                               JsonNode approvalLines) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        BizplayPlanAgentRequest lookup = new BizplayPlanAgentRequest();
        lookup.setCorpNo(corpNo);
        lookup.setSessionId(sessionId);
        ConversationalAgentSession session = resolveSession(lookup);
        ArrayNode documents = documents(session);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("This session has no draft yet — choose a trip type first.");
        }
        ObjectNode state = loadState(session);
        List<String> missing = formValueWriterService.missingRequired(documents.get(0), state.path("fields"), state);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Cannot create the plan — required fields are missing: "
                    + String.join(", ", missing) + ".");
        }
        applyPickedApprovalLines((ObjectNode) documents.get(0), approvalLines);

        // POST the draft_json AS-IS (it already has the save-body structure).
        String providerResponse = bizplayGatewayService.postPlanDraft(documents, bizplayToken);

        session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
        String reply = "Plan draft saved to BizPlay: " + providerResponse;
        appendTurn(session, "assistant", reply);
        sessionRepo.save(session);
        ConversationalAgentSession saved = sessionRepo.findById(session.getId()).orElse(session);

        return BizplayPlanAgentResponse.builder()
                .sessionId(saved.getId().toString())
                .status(saved.getStatus() == null ? null : saved.getStatus().name())
                .intent("CREATE_PLAN")
                .subAgents(List.of("BIZPLAY_GATEWAY"))
                .reply(reply)
                .travelers(travelerNames(state))
                .travelerIds(travelerIdList(state))
                .destination(state.path("destination").asText(null))
                .draftJson(saved.getDraftJson())
                .createdDate(saved.getCreatedDate())
                .updatedDate(saved.getUpdatedDate())
                .build();
    }

    @Override
    public BizplayPlanAgentResponse createManualPlan(
            com.api.bizplay_conversational.model.request.BizplayManualPlanRequest request,
            String bizplayToken) {
        if (request.getCorpUserId() == null || request.getCorpUserId().isBlank()) {
            throw new IllegalArgumentException("corpUserId is required.");
        }
        // 1) Rebuild the save-ready skeleton from the RETRIEVED form — structure is never invented.
        JsonNode papers = bizplayGatewayService.getPapers(
                request.getPurposeId(), request.getSegmentId(), bizplayToken);
        BizplayFormResponse form = formSkeletonService.buildPlanSkeleton(
                papers, request.getPurposeId(), request.getSegmentId());
        ObjectNode document = ((ObjectNode) form.getDocument()).deepCopy();
        JsonNode fields = objectMapper.valueToTree(form.getFields());

        ObjectNode state = objectMapper.createObjectNode();
        state.set("fields", fields.deepCopy());
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            state.put("destination", request.getDestination().trim());
        }
        // travelerIds drive the per-traveler document fan-out; names are only presence markers here.
        ArrayNode travelerIds = state.withArray("travelerIds");
        ArrayNode travelerNames = state.withArray("travelers");
        if (request.getTravelerCorpUserIds() != null) {
            for (Long id : request.getTravelerCorpUserIds()) {
                if (id != null) {
                    travelerIds.add(id.longValue());
                    travelerNames.add(String.valueOf(id));
                }
            }
        }

        // 2) Values go through the SAME mapped-value writers the agent uses (verified encodings).
        ObjectNode mapped = objectMapper.createObjectNode();
        if (request.getTitle() != null) mapped.put("basic:BASIC_TITLE", request.getTitle());
        if (request.getContent() != null) mapped.put("basic:BASIC_CONTENT", request.getContent());
        for (JsonNode f : fields) {
            if ("BSTR_PERIOD".equals(f.path("type").asText())
                    && (request.getStartDate() != null || request.getEndDate() != null)) {
                ObjectNode period = mapped.putObject(f.path("key").asText());
                if (request.getStartDate() != null) period.put("start", request.getStartDate());
                if (request.getEndDate() != null) period.put("end", request.getEndDate());
            }
        }
        if (request.getItemValues() != null && request.getItemValues().isObject()) {
            request.getItemValues().fields().forEachRemaining(e -> mapped.set(e.getKey(), e.getValue()));
        }
        formValueWriterService.apply(document, fields, state, mapped);

        // 3) drafter + one document per traveler + picked approval lines
        setDraftUser(document, request.getCorpUserId());
        ArrayNode documents = objectMapper.createArrayNode();
        documents.add(document);
        syncTravelerDocuments(documents, state, request.getCorpUserId());
        applyPickedApprovalLines((ObjectNode) documents.get(0), request.getApprovalLines());

        List<String> missing = formValueWriterService.missingRequired(documents.get(0), fields, state);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Cannot create the plan — required fields are missing: "
                    + String.join(", ", missing) + ".");
        }

        String providerResponse = bizplayGatewayService.postPlanDraft(documents, bizplayToken);
        return BizplayPlanAgentResponse.builder()
                .status(ConversationalAgentSession.AgentStatus.POSTED.name())
                .intent("CREATE_PLAN_MANUAL")
                .subAgents(List.of("FORM_BUILDER", "BIZPLAY_GATEWAY"))
                .reply("Plan draft saved to BizPlay: " + providerResponse)
                .destination(state.path("destination").asText(null))
                .draftJson(documents)
                .build();
    }

    // --- turn steps --------------------------------------------------------------

    /** Sub-agent [C]: LLM field mapping + deterministic writes into the request-body document. */
    private void fillFields(ObjectNode document, ObjectNode state, String text,
                            List<String> subAgents, StringBuilder reply) {
        JsonNode mapped = fieldMapperAgentService.mapFields(text, state.path("fields"));
        subAgents.add("FIELD_MAPPER_AGENT");
        List<String> applied = new ArrayList<>(
                formValueWriterService.apply(document, state.path("fields"), state, mapped));
        applied.addAll(ensurePeriodFallback(document, state, text));
        state.remove("staged"); // consumed
        if (!applied.isEmpty()) {
            reply.append("Captured: ").append(String.join("; ", applied)).append(". ");
        }
    }

    private static final java.util.regex.Pattern ISO_DATE =
            java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * Deterministic safety net: explicit ISO dates in the message must never be lost to LLM
     * nondeterminism. If the mapper did not fill the trip period but the text contains dates,
     * write first..last through the SAME period writer path.
     */
    private List<String> ensurePeriodFallback(ObjectNode document, ObjectNode state, String text) {
        if (document.hasNonNull("bstrStartDate") || text == null) {
            return List.of();
        }
        List<String> dates = new ArrayList<>();
        java.util.regex.Matcher m = ISO_DATE.matcher(text);
        while (m.find()) {
            dates.add(m.group());
        }
        if (dates.isEmpty()) {
            return List.of();
        }
        String periodKey = null;
        for (JsonNode f : state.path("fields")) {
            if ("BSTR_PERIOD".equals(f.path("type").asText())) {
                periodKey = f.path("key").asText();
                break;
            }
        }
        if (periodKey == null) {
            return List.of();
        }
        ObjectNode value = objectMapper.createObjectNode();
        value.put("start", dates.get(0));
        value.put("end", dates.get(dates.size() - 1));
        ObjectNode mapped = objectMapper.createObjectNode();
        mapped.set(periodKey, value);
        return formValueWriterService.apply(document, state.path("fields"), state, mapped);
    }

    private String stagedPlus(ObjectNode state, String message) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode staged : state.path("staged")) {
            String s = staged.asText("");
            // Chip clicks carry no trip details; skip them when replaying context.
            if (!s.isBlank() && !s.toLowerCase().startsWith("trip type:")) {
                sb.append(s).append('\n');
            }
        }
        if (!message.toLowerCase().startsWith("trip type:") || sb.isEmpty()) {
            sb.append(message);
        }
        return sb.toString();
    }

    private TripPlanAgentResponse.PendingChoice toPendingChoice(List<PurposeOption> candidates) {
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (PurposeOption c : candidates) {
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(c.getLabel())
                    .sendText(c.getSendText())
                    .build());
        }
        return TripPlanAgentResponse.PendingChoice.builder()
                .kind("PURPOSE")
                .name("trip type")
                .options(options)
                .build();
    }

    /** Chip sendText of a traveler disambiguation pick: "Traveler: <userName> (<id>) for <input>". */
    private static final java.util.regex.Pattern TRAVELER_PICK =
            java.util.regex.Pattern.compile("^Traveler:\\s*(.+?)\\s*\\((\\d+)\\)\\s*for\\s*(.+)$");

    /**
     * Resolve traveler names that are not yet mapped to a corporationUserId, using the roster tool.
     * Unique match -> id recorded; several matches (duplicates / a department) -> chips for the user;
     * none -> reported in the reply. Runs every turn, so names stuck pending resolve as soon as the
     * roster (or the input) allows.
     */
    private List<TripPlanAgentResponse.PendingChoice> resolveTravelers(BizplayPlanAgentRequest request,
                                                                       ObjectNode state, String token,
                                                                       StringBuilder reply, List<String> subAgents) {
        List<String> pending = new ArrayList<>();
        for (JsonNode n : state.path("travelers")) {
            String name = n.asText("");
            if (!name.isBlank() && !state.path("resolvedTravelers").has(name)) {
                pending.add(name);
            }
        }
        if (pending.isEmpty()) {
            return List.of();
        }
        Long corporationId = request.getCorporationId() != null
                ? request.getCorporationId()
                : corporationIdFromToken(token);
        if (corporationId == null) {
            reply.append("(Staff lookup skipped: corporationId unknown.) ");
            return List.of();
        }
        JsonNode roster = bizplayGatewayService.getCorporationUsers(corporationId, token);
        List<TravelerResolverService.Resolution> resolutions = travelerResolverService.resolve(pending, roster);
        subAgents.add("TRAVELER_RESOLVER");

        List<TripPlanAgentResponse.PendingChoice> chips = new ArrayList<>();
        for (TravelerResolverService.Resolution r : resolutions) {
            if (r.matched() != null) {
                // Keep the ROSTER name in state (a romanized/partial input like "Kim Doha" would
                // never match the roster again downstream — e.g. in the manual WYSIWYG save).
                applyTravelerPick(state, r.matched().userName(), r.matched().corporationUserId(),
                        r.input(), new StringBuilder());
                if (!r.input().equals(r.matched().userName())) {
                    reply.append("Matched traveler '").append(r.input()).append("' to ")
                            .append(r.matched().userName()).append(". ");
                }
            } else if (r.candidates() != null && !r.candidates().isEmpty()) {
                List<TripPlanAgentResponse.Option> options = new ArrayList<>();
                for (TravelerResolverService.Candidate c : r.candidates()) {
                    String dept = c.departmentName() == null ? "?" : c.departmentName();
                    String pos = c.positionName() == null ? "?" : c.positionName();
                    options.add(TripPlanAgentResponse.Option.builder()
                            .label(c.userName() + " · " + dept + " · " + pos)
                            .sendText("Traveler: " + c.userName() + " (" + c.corporationUserId() + ") for " + r.input())
                            .build());
                }
                chips.add(TripPlanAgentResponse.PendingChoice.builder()
                        .kind("TRAVELER").name(r.input()).options(options).build());
                reply.append("Multiple staff match '").append(r.input()).append("' — please pick. ");
            } else {
                reply.append("'").append(r.input()).append("' was not found in the staff roster. ");
            }
        }
        return chips;
    }

    /** Apply a traveler chip pick: swap the queried input for the chosen person and record the id. */
    private void applyTravelerPick(ObjectNode state, String userName, long id, String input, StringBuilder reply) {
        ArrayNode travelers = state.withArray("travelers");
        for (int i = travelers.size() - 1; i >= 0; i--) {
            if (travelers.get(i).asText("").equalsIgnoreCase(input)) {
                travelers.remove(i);
            }
        }
        boolean exists = false;
        for (JsonNode n : travelers) {
            if (n.asText("").equalsIgnoreCase(userName)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            travelers.add(userName);
        }
        ObjectNode resolved = state.withObject("resolvedTravelers");
        resolved.remove(input);
        resolved.put(userName, id);
        addTravelerId(state, id);
        reply.append("Added ").append(userName).append(" as traveler. ");
    }

    private void addTravelerId(ObjectNode state, long id) {
        ArrayNode ids = state.withArray("travelerIds");
        for (JsonNode n : ids) {
            if (n.asLong() == id) {
                return;
            }
        }
        ids.add(id);
    }

    /** Read currentCorpId from the (unverified) JWT payload — enough for a roster lookup key. */
    private Long corporationIdFromToken(String token) {
        String jwt = (token != null && !token.isBlank()) ? token : bizplayProperties.getDevToken();
        if (jwt == null || jwt.isBlank()) {
            return null;
        }
        try {
            String[] parts = jwt.trim().split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            long id = objectMapper.readTree(payload).path("currentCorpId").asLong(0);
            return id > 0 ? id : null;
        } catch (Exception e) {
            log.warn("Could not decode corporationId from token: {}", e.getMessage());
            return null;
        }
    }

    /** Keys the captured save body carries ONLY on the drafter's (first) document. */
    private static final String[] DRAFTER_ONLY_KEYS = {"bstrStatusType", "externalLinks", "totalBstrAmount"};

    /**
     * One document per traveler, mirroring the BizPlay save body: documents[0] is the master (kept
     * current by the writers, full key set); each further traveler gets a clone WITHOUT the
     * drafter-only keys and with their own draftUserId. Re-synced every turn so later edits to the
     * master propagate to all travelers.
     */
    private void syncTravelerDocuments(ArrayNode documents, JsonNode state, String corpUserId) {
        if (documents.isEmpty()) {
            return;
        }
        ObjectNode master = (ObjectNode) documents.get(0);
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : state.path("travelerIds")) {
            if (n.canConvertToLong() && !ids.contains(n.asLong())) {
                ids.add(n.asLong());
            }
        }
        if (ids.isEmpty()) {
            return; // names not yet resolved -> single-document draft
        }
        // The requesting user (drafter) leads; put them first when they are among the travelers.
        long drafter = parseLongOr(corpUserId, ids.get(0));
        if (ids.remove(drafter)) {
            ids.add(0, drafter);
        }
        master.put("draftUserId", ids.get(0));
        while (documents.size() > 1) {
            documents.remove(documents.size() - 1);
        }
        ensureDraftApprovalLine(master);
        for (int i = 1; i < ids.size(); i++) {
            ObjectNode follower = master.deepCopy();
            for (String key : DRAFTER_ONLY_KEYS) {
                follower.remove(key);
            }
            follower.put("draftUserId", ids.get(i));
            // The capture shows approval lines ONLY on the drafter's document; followers are empty.
            follower.set("approvalLines", objectMapper.createArrayNode());
            documents.add(follower);
        }
    }

    private long parseLongOr(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void setDraftUser(ObjectNode document, String corpUserId) {
        try {
            document.put("draftUserId", Long.parseLong(corpUserId.trim()));
        } catch (NumberFormatException e) {
            document.put("draftUserId", corpUserId.trim());
        }
        ensureDraftApprovalLine(document);
    }

    /**
     * "Set approval order" picks from the UI: rebuild the master document's approvalLines as the
     * drafter's DRAFT line followed by the picked lines in order. Line shape mirrors the DRAFT
     * line the working capture carries (EMPLOYEE, no department); only the ids and kinds come
     * from the user's picks — nothing is invented.
     */
    private void applyPickedApprovalLines(ObjectNode master, JsonNode approvalLines) {
        if (approvalLines == null || !approvalLines.isArray() || approvalLines.isEmpty()) {
            return;
        }
        ArrayNode lines = objectMapper.createArrayNode();
        master.set("approvalLines", lines);
        ensureDraftApprovalLine(master);
        int order = 1;
        for (JsonNode pick : approvalLines) {
            if (!pick.path("corporationUserId").canConvertToLong()) {
                continue;
            }
            ObjectNode line = lines.addObject();
            line.put("approvalKindType", pick.path("approvalKindType").asText("APPROVAL"));
            line.put("approvalOrder", order++);
            line.put("corporationUserId", pick.path("corporationUserId").asLong());
            line.putNull("departmentId");
            line.put("departmentApproval", false);
            line.put("paperApprovalLineType1", "EMPLOYEE");
        }
    }

    /**
     * The working capture always carries a DRAFT approval line for the drafting user (approvers
     * beyond it are hand-picked in the BizPlay UI — paperApprovalLineSettingDto is null, so they
     * cannot be derived from the form). Built purely from the drafter id — no invented data.
     */
    private void ensureDraftApprovalLine(ObjectNode document) {
        ArrayNode lines = document.withArray("approvalLines");
        for (JsonNode l : lines) {
            if ("DRAFT".equals(l.path("approvalKindType").asText())) {
                return;
            }
        }
        ObjectNode draft = lines.addObject();
        draft.put("approvalKindType", "DRAFT");
        draft.put("approvalOrder", 0);
        draft.set("corporationUserId", document.path("draftUserId").deepCopy());
        draft.putNull("departmentId");
        draft.put("departmentApproval", false);
        draft.put("paperApprovalLineType1", "EMPLOYEE");
    }

    private List<String> travelerNames(JsonNode state) {
        List<String> names = new ArrayList<>();
        for (JsonNode n : state.path("travelers")) {
            names.add(n.asText());
        }
        return names.isEmpty() ? null : names;
    }

    private List<Long> travelerIdList(JsonNode state) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : state.path("travelerIds")) {
            if (n.canConvertToLong()) {
                ids.add(n.asLong());
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    // --- session + state ---------------------------------------------------------

    private ConversationalAgentSession resolveSession(BizplayPlanAgentRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            ConversationalAgentSession created = new ConversationalAgentSession();
            created.setCorpNo(request.getCorpNo());
            created.setAgentType(ConversationalAgentSession.AgentType.TRIP_PLAN);
            // NOTE: leave id null — sessionRepo.save() only INSERTs when the id is unset.
            return created;
        }
        UUID id;
        try {
            id = UUID.fromString(sessionId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomNotFoundException("Invalid sessionId: " + sessionId);
        }
        ConversationalAgentSession existing = sessionRepo.findById(id)
                .orElseThrow(() -> new CustomNotFoundException("Session not found: " + sessionId));
        if (!request.getCorpNo().equals(existing.getCorpNo())) {
            throw new CustomNotFoundException(
                    "Session " + sessionId + " does not belong to corpNo=" + request.getCorpNo() + ".");
        }
        return existing;
    }

    /** draft_json IS the request-body array (empty until the trip type is chosen). */
    private ArrayNode documents(ConversationalAgentSession session) {
        JsonNode draft = session.getDraftJson();
        return (draft instanceof ArrayNode a) ? a : objectMapper.createArrayNode();
    }

    /** Agent bookkeeping rides in chat_event_json under a dedicated non-chat role. */
    private ObjectNode loadState(ConversationalAgentSession session) {
        JsonNode events = session.getChatEventJson();
        if (events != null && events.isArray()) {
            for (JsonNode e : events) {
                if (STATE_ROLE.equals(e.path("role").asText())) {
                    try {
                        JsonNode parsed = objectMapper.readTree(e.path("content").asText("{}"));
                        if (parsed instanceof ObjectNode o) {
                            return o;
                        }
                    } catch (Exception ex) {
                        log.warn("Could not parse agent state; starting fresh: {}", ex.getMessage());
                    }
                }
            }
        }
        return objectMapper.createObjectNode();
    }

    private void saveState(ConversationalAgentSession session, ObjectNode state) {
        JsonNode existing = session.getChatEventJson();
        ArrayNode events = (existing instanceof ArrayNode a) ? a : objectMapper.createArrayNode();
        String content;
        try {
            content = objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            content = "{}";
        }
        for (JsonNode ev : events) {
            if (STATE_ROLE.equals(ev.path("role").asText())) {
                ((ObjectNode) ev).put("content", content);
                session.setChatEventJson(events);
                return;
            }
        }
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("role", STATE_ROLE);
        entry.put("content", content);
        entry.put("created_date", LocalDateTime.now().toString());
        events.add(entry);
        session.setChatEventJson(events);
    }

    private void appendTurn(ConversationalAgentSession session, String role, String content) {
        JsonNode existing = session.getChatEventJson();
        ArrayNode events = (existing instanceof ArrayNode a) ? a : objectMapper.createArrayNode();
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", role);
        turn.put("content", content == null ? "" : content);
        turn.put("created_date", LocalDateTime.now().toString());
        events.add(turn);
        session.setChatEventJson(events);
    }
}
