package com.api.bizplay_conversational.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayFormResponse;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.model.response.PurposeOption;
import com.api.bizplay_conversational.model.response.PurposeResolutionResult;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.api.bizplay_conversational.service.bizplayPlanAgentService.BizplayPlanAgentService;
import com.api.bizplay_conversational.service.formSkeletonService.FormSkeletonService;
import com.api.bizplay_conversational.service.purposeSegmentAgentService.PurposeSegmentAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.bizplay_conversational.model.request.AgentPromptRequest;
import com.api.bizplay_conversational.model.request.SettlementStarterRequest;
import com.api.bizplay_conversational.model.response.AgentPromptResponse;
import com.api.bizplay_conversational.model.response.SettlementStarterResponse;
import com.api.bizplay_conversational.service.agentPromptService.AgentPromptService;

import java.util.Arrays;
import java.util.List;

/**
 * Phase-1 surface of the BizPlay form-driven trip-plan flow: purpose catalog (①), purpose/segment
 * resolution (sub-agent), and the dynamic form skeleton (② → ③-shaped draft document). The end
 * user's BizPlay token is passed via the X-Bizplay-Token header (dev fallback configurable).
 */
@Slf4j
@Tag(name = "BizPlay Form Integration", description = "Dynamic, form-driven trip plan drafting against the BizPlay cloud API.")
@RestController
@RequestMapping("/api/v1/agent-conversations/bizplay")
@RequiredArgsConstructor
public class BizplayFormController {

    private final BizplayGatewayService bizplayGatewayService;
    private final PurposeSegmentAgentService purposeSegmentAgentService;
    private final FormSkeletonService formSkeletonService;
    private final BizplayPlanAgentService bizplayPlanAgentService;
    private final com.api.bizplay_conversational.service.planEnrichmentService.PlanEnrichmentService planEnrichmentService;
    private final com.api.bizplay_conversational.service.destinationResolverAgentService.DestinationResolverAgentService destinationResolverAgentService;
    private final com.api.bizplay_conversational.service.bizplaySettlementAgentService.BizplaySettlementAgentService bizplaySettlementAgentService;
    private final AgentPromptService agentPromptService;
    private final com.api.bizplay_conversational.config.BizplayProperties bizplayProperties;
    private final com.api.bizplay_conversational.config.BizplayEndpoints bizplayEndpoints;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Read currentCorpId from the (unverified) JWT payload — a lookup key, not authentication. */
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

    /** Endpoint paths as this API serves them — one place, so a rename cannot leave a stale hint. */
    private static final String BASE = "/api/v1/agent-conversations/bizplay";

    /** One call, as data: {"method": "GET", "path": "/api/v2/..."}. */
    private static java.util.Map<String, String> call(String method, String path) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("method", method);
        m.put("path", path);
        return m;
    }

    /** A capability that takes more than one call, in the order they are made. */
    @SafeVarargs
    private static java.util.List<java.util.Map<String, String>> calls(
            java.util.Map<String, String>... c) {
        return java.util.List.of(c);
    }

    /**
     * Which widget answers which intent. A map rather than a switch so the contract endpoint can
     * publish it — a client should be able to read the whole rule, not infer it turn by turn.
     */
    private static final java.util.Map<String, String> INTENT_UI = java.util.Map.ofEntries(
            java.util.Map.entry("MANUAL_EXPENSE_PROMPT", "expense-form"),
            java.util.Map.entry("MANUAL_EXPENSE_PROMPT_FULL", "expense-form"),
            java.util.Map.entry("EXPENSE_IMAGE_REQUIRED", "file-upload"),
            java.util.Map.entry("AWAIT_PERIOD", "calendar"),
            java.util.Map.entry("AWAIT_PLAN_PERIOD", "calendar"),
            java.util.Map.entry("EVIDENCE_PERIOD_PENDING", "calendar"),
            java.util.Map.entry("EXPENSE_PREVIEW", "expense-preview"),
            java.util.Map.entry("SETTLEMENT_READY", "settlement-preview"),
            java.util.Map.entry("RECEIPT_BROWSE", "receipt-table"),
            java.util.Map.entry("RECEIPT_BROWSE_NOT_ISSUED", "receipt-table"),
            java.util.Map.entry("SUBMIT_REQUESTED", "confirm-submit"));

    /**
     * Every intent each agent can put on a turn, with what it means and which choice kinds ride
     * with it. The widget and the endpoints an intent implies are NOT repeated here - they are
     * derived from the same maps the turns use (INTENT_UI, keysForIntent, keysForKind), so the
     * catalogue cannot disagree with a live response.
     *
     * <p>Entries are {intent, means, choice kinds}. An empty kinds string means the question is
     * answered in free text (a name, an amount, yes/no) - those turns carry no pendingChoices.
     */
    private static final java.util.Map<String, java.util.List<String[]>> INTENT_CATALOG =
            java.util.Map.of(
                    "plan", java.util.List.of(
                            new String[]{"PURPOSE_SELECTION", "Choosing the trip purpose", "PURPOSE"},
                            new String[]{"SEGMENT_SELECTION", "Choosing the purpose's segment", "SEGMENT"},
                            new String[]{"FORM_LOAD", "The trip form was loaded", ""},
                            new String[]{"DESTINATION_ASK", "Asking country and city", "DESTINATION"},
                            new String[]{"TRAVELER_PICK", "Confirming which person the traveller is", "TRAVELER"},
                            new String[]{"TRAVELER_MORE_ASK", "Asking whether anyone else travels", ""},
                            new String[]{"TRAVELER_REMOVED", "A traveller was taken off the plan", ""},
                            new String[]{"ROUTE_ASK", "Asking the travel route", "ROUTE"},
                            new String[]{"FIELD_COMPLETION", "Asking for a remaining form field", ""},
                            new String[]{"FIELD_EDITED", "A value was changed on the draft", ""},
                            new String[]{"APPROVAL_LINE_ASK", "Asking who approves the plan", "APPROVAL_LINE"},
                            new String[]{"SUBMIT_REQUESTED", "The user asked to file the plan", ""},
                            new String[]{"CREATE_PLAN", "FILED in BizPlay (the reply carries the document number)", ""},
                            new String[]{"CREATE_PLAN_MANUAL", "Filed from a document the client supplied", ""},
                            new String[]{"DATA_QUERY", "Answered a question from BizPlay data", ""},
                            new String[]{"DRAFT_QUERY", "Answered a question about the draft", ""},
                            new String[]{"GUARDRAIL_BLOCKED", "Off-topic request; nothing changed", ""}),
                    "settlement", java.util.List.of(
                            new String[]{"AWAIT_PERIOD", "Asking the period to search", "EVIDENCE_PERIOD"},
                            new String[]{"AWAIT_PLAN_PERIOD", "Asking another period to look for plans", "EVIDENCE_PERIOD"},
                            new String[]{"PLAN_SEARCH", "Listing the trips that can be settled", "PLAN"},
                            new String[]{"PLAN_PICK_PENDING", "Waiting for one of those trips to be picked", "PLAN"},
                            new String[]{"PLAN_IMPORT", "A plan was imported into a settlement", "TRANKIND"},
                            new String[]{"PENDING_PLANS", "Plans still awaiting approval (not settleable)", "PLAN_PENDING"},
                            new String[]{"TRANKIND_PENDING", "Asking which expense type to add", "TRANKIND"},
                            new String[]{"TRANKIND_PICKED", "An expense type was picked", "CARD_TYPE"},
                            new String[]{"CARD_TYPES_PENDING", "Asking which card types to search", "CARD_TYPE"},
                            new String[]{"EVIDENCE_PERIOD_PENDING", "Asking the evidence period", "EVIDENCE_PERIOD"},
                            new String[]{"EVIDENCE_LOAD", "Listing unattached receipts", "RECEIPT"},
                            new String[]{"EVIDENCE_PICK_PENDING", "Waiting for a receipt to be picked", "RECEIPT"},
                            new String[]{"EVIDENCE_ATTACH", "A receipt was attached to the settlement", ""},
                            new String[]{"MANUAL_EXPENSE_CHOOSE", "Asking how to enter an expense by hand", "MANUAL_EXPENSE_MODE"},
                            new String[]{"MANUAL_EXPENSE_PROMPT", "Asking for the expense (basic fields)", ""},
                            new String[]{"MANUAL_EXPENSE_PROMPT_FULL", "Asking for the expense and its details", ""},
                            new String[]{"EXPENSE_SLOT_PENDING", "Asking one detail of the expense", "EXPENSE_SLOT"},
                            new String[]{"EXPENSE_PREVIEW", "Showing the receipt before registering it", "EXPENSE_CONFIRM"},
                            new String[]{"EXPENSE_IMAGE_REQUIRED", "The receipt image is still missing", ""},
                            new String[]{"EXPENSE_IMAGE_SUBMIT", "Asked to upload the file already chosen", ""},
                            new String[]{"MANUAL_EXPENSE_CREATED", "The expense was registered in BizPlay", ""},
                            new String[]{"MANUAL_EXPENSE_ADDED", "The expense and its image were registered", ""},
                            new String[]{"EXPENSE_UPDATED", "A registered expense was corrected", ""},
                            new String[]{"EXPENSE_CANCELLED", "The expense being entered was abandoned", ""},
                            new String[]{"TITLE_SET", "The document title was set", ""},
                            new String[]{"RECEIPT_BROWSE", "Browsing receipts not yet on a document", "RECEIPT"},
                            new String[]{"RECEIPT_BROWSE_NOT_ISSUED", "Browsing receipts that are not ISSUED", "RECEIPT"},
                            new String[]{"SETTLEMENT_READY", "Everything is on the settlement; ready to file", "SUBMIT"},
                            new String[]{"APPROVAL_LINE_ASK", "Confirming the approval line before filing", "APPROVAL_LINE"},
                            new String[]{"APPROVER_PICKED", "An approver was set", ""},
                            new String[]{"CREATE_SETTLEMENT", "FILED in BizPlay (the reply carries the document number)", ""},
                            new String[]{"SETTLEMENT_SAVED", "Saved on our side, not filed", ""},
                            new String[]{"SESSION", "The session was read back", ""},
                            new String[]{"DRAFT_QUERY", "Answered a question about the settlement", ""},
                            new String[]{"STOP_PICK_PENDING", "Asking whether to stop", "STOP"},
                            new String[]{"GUARDRAIL_BLOCKED", "Off-topic request; nothing changed", ""}));

    /** The shapes {@code pendingChoices[].render} can take, for the contract endpoint. */
    private static final java.util.List<String> RENDER_SHAPES =
            java.util.List.of("chips", "dropdown", "table", "route-picker", "approval-line", "lookup");

    private static final java.util.Map<String, List<java.util.Map<String, String>>> EMPTY_CALLS =
            java.util.Map.of();

    /**
     * OUR endpoints for one agent, with corpNo already filled in. Keyed by capability, and every
     * key here has the same key in {@link #upstreamFor} — the same capability seen from our side
     * and from BizPlay's.
     */
    private java.util.Map<String, java.util.List<java.util.Map<String, String>>> resourcesFor(
            String agent, String q) {
        java.util.Map<String, java.util.List<java.util.Map<String, String>>> res =
                new java.util.LinkedHashMap<>();
        if ("plan".equals(agent)) {
            res.put("purposes", calls(call("GET", BASE + "/purposes" + q)));
            res.put("destinationOptions", calls(call("GET", BASE + "/agents/plan/destination-options" + q)));
            res.put("destinationPick", calls(call("POST", BASE + "/agents/plan/destination-pick" + q)));
            res.put("routeOptions", calls(call("GET", BASE + "/agents/plan/route-options" + q)));
            res.put("approvers", calls(call("GET", BASE + "/corporation-users" + q)));
            res.put("plansByStatus", calls(call("GET", BASE + "/plans/by-status" + q)));
            res.put("editPlanField", calls(call("PATCH", BASE + "/agents/plan/{sessionId}/field" + q)));
            res.put("planSession", calls(call("GET", BASE + "/agents/plan/{sessionId}" + q)));
            res.put("filePlan", calls(call("POST", BASE + "/agents/plan/{sessionId}/create" + q)));
            res.put("whoami", calls(call("GET", BASE + "/whoami" + q)));
        } else {
            res.put("plans", calls(call("GET", BASE + "/plans" + q)));
            res.put("tranKinds", calls(call("GET", BASE + "/agents/settlement/{sessionId}" + q)));
            res.put("currencies", calls(call("GET", BASE + "/agents/settlement/currencies" + q)));
            res.put("taxCodes", calls(call("GET", BASE + "/agents/settlement/tax-codes" + q)));
            res.put("terminals", calls(call("GET", BASE + "/agents/settlement/terminals" + q
                    + "&vehicleType={vehicleType}")));
            res.put("receipts", calls(call("GET", BASE + "/agents/settlement/receipts" + q)));
            res.put("receiptDetail", calls(call("GET", BASE + "/agents/settlement/receipts/{receiptId}" + q)));
            res.put("uploadReceiptImage", calls(call("POST", BASE + "/agents/settlement/receipts/{receiptId}/image" + q)));
            res.put("registerExpense", calls(call("POST", BASE + "/agents/settlement/{sessionId}/manual-expense/create" + q)));
            res.put("attachExpenseImage", calls(call("POST", BASE + "/agents/settlement/{sessionId}/manual-expense/attach" + q)));
            res.put("editExpenseField", calls(call("PATCH", BASE + "/agents/settlement/{sessionId}/expense/{receiptId}/field" + q)));
            res.put("settlementSession", calls(call("GET", BASE + "/agents/settlement/{sessionId}" + q)));
            res.put("saveSettlementDraft", calls(call("POST", BASE + "/agents/settlement/{sessionId}/save" + q)));
            res.put("fileSettlement", calls(call("POST", BASE + "/agents/settlement/{sessionId}/create" + q)));
            res.put("filedSettlements", calls(call("GET", BASE + "/settlements" + q)));
            res.put("settlementDetail", calls(call("GET", BASE + "/settlements/{approvalId}" + q)));
            res.put("savedSettlements", calls(call("GET", BASE + "/agents/settlement/saved" + q)));
            // The settlement asks for its approval line before filing, so it needs the directory too.
            res.put("approvers", calls(call("GET", BASE + "/corporation-users" + q)));
            res.put("whoami", calls(call("GET", BASE + "/whoami" + q)));
        }
        return res;
    }

    /**
     * The BizPlay endpoints behind those, for a client holding its own bearer. Paths only — the
     * caller prefixes its own host. Taken from the catalogue the gateway itself calls, so a rename
     * moves both at once.
     */
    private java.util.Map<String, java.util.List<java.util.Map<String, String>>> upstreamFor(String agent) {
        java.util.Map<String, java.util.List<java.util.Map<String, String>>> up =
                new java.util.LinkedHashMap<>();
        String planQuery = "?travelerId={travelerId}&searchPeriodType=BSTR_START_DATE"
                + "&startDate={from}&endDate={to}";
        if ("plan".equals(agent)) {
            // The paper definition answers on the UNTYPED path with its segment; the typed variant
            // (/paper/purpose/{bstrType}/{purposeId}) returns 400 on this tenant, so it is not
            // advertised — the gateway still falls back to it internally.
            up.put("purposes", calls(call("GET", bizplayEndpoints.getPurposeCatalog()),
                    call("GET", bizplayEndpoints.getPapers() + "?segmentId={segmentId}")));
            up.put("destinationOptions", calls(call("GET", bizplayEndpoints.getRegionList()),
                    call("GET", bizplayEndpoints.getRegionCities()),
                    call("GET", bizplayEndpoints.getRegionUsedList()),
                    call("GET", bizplayEndpoints.getRegionUsedCities())));
            up.put("routeOptions", calls(call("GET", bizplayEndpoints.getDestinationList())));
            up.put("approvers", calls(call("GET", bizplayEndpoints.getCorporationUsers())));
            up.put("plansByStatus", calls(call("GET", bizplayEndpoints.getPlanList() + planQuery),
                    call("GET", bizplayEndpoints.getPendingPlanList() + planQuery)));
            up.put("filePlan", calls(call("POST", bizplayEndpoints.getPlanDraft())));
            up.put("whoami", calls(call("GET", bizplayEndpoints.getUserProfile())));
        } else {
            up.put("plans", calls(call("GET", bizplayEndpoints.getPlanList() + planQuery),
                    call("GET", bizplayEndpoints.getPendingPlanList() + planQuery)));
            up.put("tranKinds", calls(call("GET", bizplayEndpoints.getTrankindList())));
            up.put("currencies", calls(call("GET", bizplayEndpoints.getCurrencyCodes())));
            up.put("taxCodes", calls(call("GET", bizplayEndpoints.getTaxCodeList())));
            up.put("terminals", calls(call("GET", bizplayEndpoints.getEtcCardTerminal()),
                    call("GET", bizplayEndpoints.getVehicleNodes())));
            up.put("receipts", calls(call("POST", bizplayEndpoints.getGeneralExpense())));
            up.put("receiptDetail", calls(call("GET", bizplayEndpoints.getReceiptById()),
                    call("GET", bizplayEndpoints.getIssuedBulk())));
            up.put("uploadReceiptImage", calls(call("POST", bizplayEndpoints.getFileboxUpload()),
                    call("PATCH", bizplayEndpoints.getReceiptImage())));
            up.put("registerExpense", calls(call("POST", bizplayEndpoints.getEtcCard()),
                    call("POST", bizplayEndpoints.getFileboxUpload()),
                    call("GET", bizplayEndpoints.getIssuedBulk()),
                    call("POST", bizplayEndpoints.getPolicyRenewalLimit())));
            up.put("attachExpenseImage", calls(call("POST", bizplayEndpoints.getFileboxUpload()),
                    call("PATCH", bizplayEndpoints.getReceiptImage())));
            up.put("editExpenseField", calls(call("PATCH", bizplayEndpoints.getEtcCardUpdate())));
            up.put("fileSettlement", calls(call("POST", bizplayEndpoints.getSettlementDraft()),
                    call("GET", bizplayEndpoints.getBranchOfficesActive())));
            up.put("filedSettlements", calls(call("POST", bizplayEndpoints.getSettlementList())));
            up.put("settlementDetail", calls(call("GET", bizplayEndpoints.getPlanDetail())));
            up.put("approvers", calls(call("GET", bizplayEndpoints.getCorporationUsers())));
            up.put("whoami", calls(call("GET", bizplayEndpoints.getUserProfile())));
        }
        return up;
    }

    /** The first path of a capability — for the single-call fields ({@code action}, {@code optionsUrl}). */
    private String firstPath(java.util.Map<String, java.util.List<java.util.Map<String, String>>> map,
                             String key) {
        java.util.List<java.util.Map<String, String>> c = key == null ? null : map.get(key);
        return (c == null || c.isEmpty()) ? null : c.get(0).get("path");
    }

    /**
     * The capabilities THIS turn is about — the lists behind its questions, the sources of its form
     * fields, and whatever its widget submits to. A turn that asks which trip type names the purpose
     * endpoints and nothing else; a turn that reports a saved document names nothing.
     */
    private java.util.Set<String> keysForTurn(BizplayPlanAgentResponse response) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        String intent = response.getIntent() == null ? "" : response.getIntent();
        java.util.List<com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice> choices =
                response.getPendingChoices() == null ? java.util.List.of() : response.getPendingChoices();
        for (com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice c : choices) {
            if (c.getSource() != null) {
                keys.add(c.getSource().contains(":")
                        ? c.getSource().substring(0, c.getSource().indexOf(':')) : c.getSource());
            }
            keys.addAll(keysForKind(c.getKind()));
        }
        if (response.getFormFields() != null) {
            for (java.util.Map<String, Object> f : response.getFormFields()) {
                Object src = f.get("source");
                if (src != null) {
                    keys.add(String.valueOf(src));
                }
            }
        }
        keys.addAll(keysForIntent(intent));
        return keys;
    }

    /** The capability a choice of this kind is answered from. */
    private java.util.Set<String> keysForKind(String kind) {
        return switch (kind == null ? "" : kind) {
            case "PURPOSE", "SEGMENT" -> java.util.Set.of("purposes");
            case "DESTINATION" -> java.util.Set.of("destinationOptions");
            case "ROUTE" -> java.util.Set.of("routeOptions");
            case "APPROVAL_LINE", "TRAVELER", "STAFF", "APPROVER" -> java.util.Set.of("approvers");
            case "PLAN", "PLAN_PENDING" -> java.util.Set.of("plans");
            case "TRANKIND" -> java.util.Set.of("tranKinds");
            case "RECEIPT" -> java.util.Set.of("receipts");
            case "EXPENSE_SLOT" -> java.util.Set.of("terminals");
            default -> java.util.Set.of();
        };
    }

    /** What this intent needs regardless of its choices (a widget's target, a list it just showed). */
    private java.util.Set<String> keysForIntent(String intent) {
        return switch (intent == null ? "" : intent) {
            case "MANUAL_EXPENSE_PROMPT", "MANUAL_EXPENSE_PROMPT_FULL", "EXPENSE_PREVIEW" ->
                    java.util.Set.of("registerExpense");
            case "EXPENSE_IMAGE_REQUIRED" -> java.util.Set.of("attachExpenseImage");
            case "RECEIPT_BROWSE", "RECEIPT_BROWSE_NOT_ISSUED" ->
                    java.util.Set.of("receipts", "receiptDetail");
            case "SUBMIT_REQUESTED" -> java.util.Set.of("filePlan");
            case "SETTLEMENT_READY" -> java.util.Set.of("fileSettlement");
            case "PURPOSE_SELECTION", "SEGMENT_SELECTION" -> java.util.Set.of("purposes");
            case "PLAN_SEARCH", "PENDING_PLANS" -> java.util.Set.of("plans");
            default -> java.util.Set.of();
        };
    }

    /** A map cut down to those keys. Empty -> null, so the field is omitted from the JSON. */
    private java.util.Map<String, java.util.List<java.util.Map<String, String>>> scoped(
            java.util.Map<String, java.util.List<java.util.Map<String, String>>> all,
            java.util.Set<String> keys) {
        java.util.Map<String, java.util.List<java.util.Map<String, String>>> out =
                new java.util.LinkedHashMap<>();
        for (String k : keys) {
            java.util.List<java.util.Map<String, String>> v = all.get(k);
            if (v != null) {
                out.put(k, v);
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** One agent's intent catalogue, with the widget and endpoint keys each intent implies. */
    private java.util.List<java.util.Map<String, Object>> intentCatalog(String agent) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (String[] row : INTENT_CATALOG.getOrDefault(agent, java.util.List.of())) {
            java.util.List<String> kinds = row[2].isBlank() ? java.util.List.of()
                    : java.util.List.of(row[2].split(","));
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(keysForIntent(row[0]));
            for (String kind : kinds) {
                keys.addAll(keysForKind(kind));
            }
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("intent", row[0]);
            entry.put("means", row[1]);
            entry.put("choices", kinds);
            entry.put("ui", INTENT_UI.get(row[0]));
            entry.put("resourceKeys", java.util.List.copyOf(keys));
            out.add(entry);
        }
        return out;
    }

    /** Which widget answers this turn; null when the reply and its choices are the whole turn. */
    private String uiFor(String intent) {
        return intent == null ? null : INTENT_UI.get(intent);
    }

    /** The endpoint that widget posts to, when it posts anywhere. */
    private String actionFor(String ui, java.util.Map<String, java.util.List<java.util.Map<String, String>>> res) {
        if (ui == null) {
            return null;
        }
        return switch (ui) {
            case "expense-form" -> firstPath(res, "registerExpense");
            case "file-upload" -> firstPath(res, "attachExpenseImage");
            case "confirm-submit" -> res.containsKey("filePlan")
                    ? firstPath(res, "filePlan") : firstPath(res, "fileSettlement");
            default -> null;
        };
    }

    /**
     * How one choice list is meant to be drawn. The rule is the one this project's own UI had to
     * learn the hard way: options with columns are a table, a list too long for buttons is a
     * dropdown, a question with no options but an endpoint is a lookup, and the two flows with a
     * purpose-built widget say so by name.
     */
    private String renderFor(com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice c) {
        String kind = c.getKind() == null ? "" : c.getKind();
        int size = c.getOptions() == null ? 0 : c.getOptions().size();
        boolean columns = c.getOptions() != null
                && c.getOptions().stream().anyMatch(o -> o.getMeta() != null && o.getMeta().size() > 1);
        if (size == 0 && (c.getSource() != null || c.getOptionsUrl() != null)) {
            return "lookup";
        }
        return switch (kind) {
            case "ROUTE" -> "route-picker";
            case "APPROVAL_LINE" -> "approval-line";
            case "PLAN", "PLAN_PENDING", "RECEIPT" -> columns ? "table" : "chips";
            default -> size > 24 ? "dropdown" : "chips";
        };
    }

    /**
     * The BizPlay endpoint a client calls DIRECTLY for a long list, so their screen needs no proxy
     * to the AI server (company feedback: the UI may not reach us, only their backend). Each of our
     * four lookups wraps exactly one BizPlay API; this hands the client that API, the row field to
     * show, and the filter we would have applied. Null for any other choice.
     */
    private java.util.Map<String, Object> bizplayLookup(String source, String filter,
            com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice c) {
        if (source == null) {
            return null;
        }
        java.util.Map<String, Object> lookup = new java.util.LinkedHashMap<>();
        lookup.put("method", "GET");
        switch (source) {
            case "terminals" -> {
                lookup.put("path", bizplayEndpoints.getEtcCardTerminal());
                lookup.put("labelField", "name");
                if (filter != null && !filter.isBlank()) {
                    lookup.put("filter", java.util.Map.of("vehicleType", filter));
                }
            }
            case "currencies" -> {
                lookup.put("path", bizplayEndpoints.getCurrencyCodes());
                lookup.put("labelField", "name");
            }
            case "routeOptions" -> {
                lookup.put("path", bizplayEndpoints.getDestinationList());
                lookup.put("labelField", "name");
            }
            case "destinationOptions" -> {
                // The list the FORM uses, as the resolver reported it: the 시/도 list (master or
                // 급지-registered), the flat country list, country then city, or the registered
                // countries with their registered cities (an empty registered city list means
                // every city of that country is allowed - then the full city list applies).
                String kind = filter == null ? "SIDO" : filter;
                lookup.put("labelField", "name");
                switch (kind) {
                    case "SIDO_USED" -> lookup.put("path",
                            bizplayEndpoints.getRegionUsedList().replace("{regionType}", "SIDO"));
                    case "COUNTRY" -> lookup.put("path",
                            bizplayEndpoints.getRegionList().replace("{regionType}", "COUNTRY"));
                    case "COUNTRY_CITY" -> {
                        lookup.put("path", bizplayEndpoints.getRegionList().replace("{regionType}", "COUNTRY"));
                        lookup.put("then", java.util.Map.of("method", "GET",
                                "path", bizplayEndpoints.getRegionCities(),
                                "pathParamFrom", "countryCode", "labelField", "name"));
                    }
                    case "COUNTRY_USED_CITY_USED" -> {
                        lookup.put("path", bizplayEndpoints.getRegionUsedList().replace("{regionType}", "COUNTRY"));
                        lookup.put("then", java.util.Map.of("method", "GET",
                                "path", bizplayEndpoints.getRegionUsedCities(),
                                "pathParamFrom", "countryCode", "labelField", "name",
                                "emptyMeans", "all cities of that country are allowed - use "
                                        + bizplayEndpoints.getRegionCities()));
                    }
                    default -> lookup.put("path",
                            bizplayEndpoints.getRegionList().replace("{regionType}", "SIDO"));
                }
            }
            default -> {
                return null;
            }
        }
        lookup.put("send", "the chosen row's " + lookup.get("labelField") + " as the next message");
        return lookup;
    }

    /**
     * Answer the company's question — "어떤 intent에서 어떤 API를 호출해야 하는지" — in the response
     * itself rather than in documentation.
     *
     * <p>Every question the agent asks already carries its own {@code pendingChoices}, so a client
     * never has to call anything to hold a conversation. What it cannot know without being told is
     * where the lists behind its OWN widgets live. So each turn names only the capabilities that
     * turn is about, from both sides: {@code resources} (ours) and {@code upstream} (BizPlay's),
     * keyed identically. {@code GET /agents/contract} publishes the whole surface for a developer
     * reading it once.
     */
    /** Default for {@code ?inlineLimit=}: how many rows a list may carry inline, per list. */
    private static final int DEFAULT_INLINE_LIMIT = 50;

    private BizplayPlanAgentResponse withContract(BizplayPlanAgentResponse response, String agent,
                                                  String corpNo, Integer inlineLimit) {
        if (response == null) {
            return null;
        }
        // The client's inline cap, per list: BizPlay sets it to fit its screen; absent, 50.
        int limit = inlineLimit == null || inlineLimit < 0 ? DEFAULT_INLINE_LIMIT : inlineLimit;
        String q = "?corpNo=" + java.net.URLEncoder.encode(corpNo == null ? "" : corpNo,
                java.nio.charset.StandardCharsets.UTF_8);
        java.util.Map<String, java.util.List<java.util.Map<String, String>>> allRes = resourcesFor(agent, q);
        java.util.Map<String, java.util.List<java.util.Map<String, String>>> allUp = upstreamFor(agent);

        java.util.List<java.util.Map<String, Object>> fields = response.getFormFields();
        if (fields != null) {
            for (java.util.Map<String, Object> f : fields) {
                String src = f.get("source") == null ? null : String.valueOf(f.get("source"));
                if (src == null) {
                    continue;
                }
                String url = firstPath(allRes, src);
                if (url != null) {
                    f.put("optionsUrl", url);
                }
                if (allUp.containsKey(src)) {
                    f.put("upstream", allUp.get(src));
                }
            }
        }

        // An agent that knows its own question wins: it can tell a period ask from an ordinary
        // one, which the intent alone cannot. Everything else is still keyed on the intent.
        String ui = response.getUi() != null ? response.getUi() : uiFor(response.getIntent());
        java.util.List<com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice> choices =
                response.getPendingChoices();
        BizplayPlanAgentResponse withChoices = response;
        if (choices != null && !choices.isEmpty()) {
            java.util.List<com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice> decorated =
                    new java.util.ArrayList<>();
            for (com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice c : choices) {
                // A choice that names a source has a fuller list behind an endpoint (the 통화 chips
                // are 5 of 179); otherwise the inline options ARE the whole list.
                // "source:detail" - the detail is the vehicle type for terminals, the list
                // kind for destinations; the part before the colon is the capability.
                String source = c.getSource();
                String filter = null;
                if (source != null && source.contains(":")) {
                    filter = source.substring(source.indexOf(':') + 1);
                    source = source.substring(0, source.indexOf(':'));
                }
                if (source == null) {
                    source = switch (c.getKind() == null ? "" : c.getKind()) {
                        case "DESTINATION" -> "destinationOptions";
                        case "ROUTE" -> "routeOptions";
                        case "APPROVAL_LINE" -> "approvers";
                        default -> null;
                    };
                }
                String url = firstPath(allRes, source);
                if (url != null && filter != null) {
                    url = url.replace("{vehicleType}", java.net.URLEncoder.encode(
                            filter, java.nio.charset.StandardCharsets.UTF_8));
                }
                com.api.bizplay_conversational.model.response.TripPlanAgentResponse.PendingChoice built =
                        c.toBuilder()
                        .render(c.getRender() != null ? c.getRender() : renderFor(c))
                        .lookup(c.getLookup() != null ? c.getLookup() : bizplayLookup(source, filter, c))
                        .optionsUrl(c.getOptionsUrl() != null ? c.getOptionsUrl() : url)
                        .upstream(c.getUpstream() != null ? c.getUpstream()
                                : (source == null ? null : allUp.get(source)))
                        .build();
                // Over the client's inline cap: a list that can be fetched elsewhere (a BizPlay
                // lookup, or our mirror) is sent as the lookup alone. A long list with neither
                // stays inline - there is nowhere else to get it.
                int rows = built.getOptions() == null ? 0 : built.getOptions().size();
                if (rows > limit && (built.getLookup() != null || built.getOptionsUrl() != null)) {
                    log.info("[INLINE] {} list of {} rows exceeds inlineLimit {} - sent as lookup only.",
                            built.getKind(), rows, limit);
                    built = built.toBuilder().options(null).render("lookup").build();
                }
                decorated.add(built);
            }
            withChoices = response.toBuilder().pendingChoices(decorated).build();
        }
        java.util.Set<String> keys = keysForTurn(withChoices);
        return withChoices.toBuilder()
                .resources(scoped(allRes, keys))
                .upstream(scoped(allUp, keys))
                .ui(ui)
                .action(actionFor(ui, allRes))
                .build();
    }

    @Operation(summary = "The contract a client codes against: every endpoint of ours and of "
            + "BizPlay for both agents, the widget each intent asks for, and the shapes a choice "
            + "list can take. Per-turn responses carry only what that turn is about; this is the "
            + "whole surface, for reading once while building.")
    @GetMapping("/agents/contract")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> agentContract(
            @RequestParam("corpNo") String corpNo) {
        String q = "?corpNo=" + java.net.URLEncoder.encode(corpNo, java.nio.charset.StandardCharsets.UTF_8);
        java.util.Map<String, Object> plan = new java.util.LinkedHashMap<>();
        plan.put("endpoint", call("POST", BASE + "/agents/plan"));
        plan.put("intents", intentCatalog("plan"));
        plan.put("resources", resourcesFor("plan", q));
        plan.put("upstream", upstreamFor("plan"));
        java.util.Map<String, Object> settlement = new java.util.LinkedHashMap<>();
        settlement.put("endpoint", call("POST", BASE + "/agents/settlement"));
        settlement.put("intents", intentCatalog("settlement"));
        settlement.put("resources", resourcesFor("settlement", q));
        settlement.put("upstream", upstreamFor("settlement"));

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("answering", "POST the chosen option's sendText — or whatever the user typed — back "
                + "to the same agent endpoint with the same sessionId.");
        out.put("agents", java.util.Map.of("plan", plan, "settlement", settlement));
        out.put("ui", INTENT_UI);
        out.put("render", RENDER_SHAPES);
        out.put("intentNote", "agents[*].intents is the whole catalogue an agent can emit. "
                + "choices[] names the pendingChoices kinds that ride with that intent - an empty "
                + "list means the question is answered in free text, so the turn carries no "
                + "pendingChoices. Values may be added over time: render an unknown intent from "
                + "reply + pendingChoices.");
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @Operation(summary = "Chat turn of the form-driven plan agent (purpose chips -> dynamic form -> field filling -> follow-ups)")
    @PostMapping("/agents/plan")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> planChat(
            @RequestBody BizplayPlanAgentRequest request,
            @RequestParam(value = "inlineLimit", required = false) Integer inlineLimit,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/plan - corpNo={}, corpUserId={}, sessionId={}",
                request.getCorpNo(), request.getCorpUserId(), request.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok(withContract(
                bizplayPlanAgentService.chat(request, token), "plan", request.getCorpNo(), inlineLimit)));
    }

    @Operation(summary = "⑨-b One settlement document by approvalId (GET /api/v2/approval/bstr/{id}). "
            + "paper.paperKind.paperKindType == EXPENSE_REPORT confirms the id really is a settlement.")
    @GetMapping("/settlements/{approvalId}")
    public ResponseEntity<ApiResponse<JsonNode>> settlementDetail(
            @org.springframework.web.bind.annotation.PathVariable("approvalId") long approvalId,
            @RequestParam("corpNo") String corpNo,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/settlements/{} - corpNo={}", approvalId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getPlanDetail(approvalId, token)));
    }

    @Operation(summary = "Who the caller is, per their BizPlay token: name, corporationUserId, "
            + "department, connected corporations. With no X-Bizplay-Token header this describes "
            + "the dev fallback token — which is exactly what the UI shows as the default user.")
    @GetMapping("/whoami")
    public ResponseEntity<ApiResponse<com.fasterxml.jackson.databind.JsonNode>> whoami(
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getUserProfile(token)));
    }

    @Operation(summary = "Destination suggestion chips for one trip form — the regions the "
            + "paper's OWN flags allow (급지 policy list / 시도 list), from the provider's region "
            + "APIs. Empty regions with source=any means anything goes and the UI may show its "
            + "generic suggestions.")
    @GetMapping("/agents/plan/destination-options")
    public ResponseEntity<ApiResponse<com.fasterxml.jackson.databind.JsonNode>> destinationOptions(
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "segment", required = false) String segment,
            @RequestParam(value = "purposeId", required = false) Long purposeId,
            @RequestParam(value = "segmentId", required = false) Long segmentId,
            @RequestParam(value = "citiesOf", required = false) String citiesOf,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            if (citiesOf != null && !citiesOf.isBlank()) {
                // Second step of the country→city cascade on non-policy overseas forms.
                return ResponseEntity.ok(ApiResponse.ok(
                        destinationResolverAgentService.citiesOfCountry(citiesOf, token)));
            }
            return ResponseEntity.ok(ApiResponse.ok(destinationResolverAgentService.destinationOptions(
                    purpose, segment, purposeId, segmentId, token)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "Semantic destination pick: which listed option does the typed message "
            + "MEAN? Body {message, options:[labels…]} → {index: 0-based or null}. LLM-judged — "
            + "handles other languages, typos and descriptions ('capital of Japan').")
    @PostMapping("/agents/plan/destination-pick")
    public ResponseEntity<ApiResponse<com.fasterxml.jackson.databind.JsonNode>> destinationPick(
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            java.util.List<String> options = new java.util.ArrayList<>();
            for (JsonNode o : body.path("options")) {
                options.add(o.asText(""));
            }
            String message = body.path("message").asText("");
            boolean ko = message.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
            Integer idx = destinationResolverAgentService.pickDestination(options, message,
                    body.path("context").asText(""), ko);
            com.fasterxml.jackson.databind.node.ObjectNode out =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            if (idx == null) {
                out.putNull("index");
            } else {
                out.put("index", idx);
            }
            return ResponseEntity.ok(ApiResponse.ok(out));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "⑨ Settlement (출장정산서) documents saved in BizPlay for a period — the "
            + "source of truth for the Expense Report table.")
    @GetMapping("/settlements")
    public ResponseEntity<ApiResponse<JsonNode>> settlements(
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String from = (startDate == null || startDate.isBlank()) ? today.minusMonths(1).toString() : startDate;
        String to = (endDate == null || endDate.isBlank()) ? today.toString() : endDate;
        log.info("GET /bizplay/settlements - corpNo={}, {}~{}", corpNo, from, to);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getSettlementList(from, to, token)));
    }

    @Operation(summary = "Chat turn of the settlement (출장정산) agent: period question -> plan search "
            + "(④) -> plan import (⑤) -> evidence period/card-type questions -> receipt attach (⑥). "
            + "The session draft_json holds the sample-shaped settlement document.")
    @PostMapping("/agents/settlement")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> settlementChat(
            @RequestBody BizplayPlanAgentRequest request,
            @RequestParam(value = "inlineLimit", required = false) Integer inlineLimit,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/settlement - corpNo={}, corpUserId={}, sessionId={}",
                request.getCorpNo(), request.getCorpUserId(), request.getSessionId());
        return ResponseEntity.ok(ApiResponse.ok(withContract(
                bizplaySettlementAgentService.chat(request, token), "settlement", request.getCorpNo(),
                inlineLimit)));
    }

    @Operation(summary = "LLM intent judge for the approval-line step: what does the user's "
            + "message MEAN — pick a person, assign a role, done picking, save now, not yet, "
            + "remove someone, or something else. No word lists.")
    @PostMapping("/agents/plan/approval-intent")
    public ResponseEntity<ApiResponse<JsonNode>> approvalIntent(
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            return ResponseEntity.ok(ApiResponse.ok(bizplayPlanAgentService.approvalIntent(corpNo, body)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "The corporation's registered travel destinations (출장지) — what a "
            + "route leg's departure/arrival ids, addresses and coordinates come from. Offer them "
            + "as a picker; typing the route in words works too.")
    @GetMapping("/agents/plan/route-options")
    public ResponseEntity<ApiResponse<JsonNode>> planRouteOptions(
            @RequestParam("corpNo") String corpNo,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String bizplayToken) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            return ResponseEntity.ok(ApiResponse.ok(planEnrichmentService.routeOptions(bizplayToken)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "Record a client-handled turn into the plan session transcript — "
            + "no pipeline runs; keeps the conversation history complete for context.")
    @PostMapping("/agents/plan/{sessionId}/note")
    public ResponseEntity<ApiResponse<String>> notePlanTurn(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body) {
        bizplayPlanAgentService.noteTurn(sessionId, corpNo,
                body.path("user").asText(null), body.path("assistant").asText(null));
        return ResponseEntity.ok(ApiResponse.ok("noted"));
    }

    @Operation(summary = "Correct ONE field of a plan draft in place - the same change a user "
            + "would otherwise phrase in chat. Body {key, value}; key is a form field key "
            + "(\"basic:BASIC_TITLE\", \"item:18403\") or a slot name (destination, "
            + "destinationDetail, startDate, endDate, transportType). Returns the updated draft.")
    @PatchMapping("/agents/plan/{sessionId}/field")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> editPlanField(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String bizplayToken) {
        com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.set(corpNo);
        try {
            return ResponseEntity.ok(ApiResponse.ok(bizplayPlanAgentService.editField(
                    sessionId, corpNo, body.path("key").asText(null),
                    body.path("value").asText(""), bizplayToken)));
        } finally {
            com.api.bizplay_conversational.service.agentPromptService.AgentTenantContext.clear();
        }
    }

    @Operation(summary = "Create this plan: POST the session's draft_json to BizPlay (DRAFT_ONLY save). "
            + "Optional body {approvalLines:[{corporationUserId, approvalKindType?}]} carries the "
            + "\"Set approval order\" picks.")
    @PostMapping("/agents/plan/{sessionId}/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createPlan(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/plan/{}/create - corpNo={}", sessionId, corpNo);
        JsonNode approvalLines = body == null ? null : body.path("approvalLines");
        return ResponseEntity.ok(ApiResponse.ok(
                bizplayPlanAgentService.createPlan(sessionId, corpNo, token, approvalLines)));
    }

    @Operation(summary = "Submit this settlement: POST the session's draft_json to BizPlay's own "
            + "settlement endpoint (/bstr/report/draft — never the plan path). Optional body "
            + "{approvalLines:[{corporationUserId, approvalKindType?}]} carries the approver picks.")
    @PostMapping("/agents/settlement/{sessionId}/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createSettlement(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/settlement/{}/create - corpNo={}", sessionId, corpNo);
        JsonNode approvalLines = body == null ? null : body.path("approvalLines");
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.createSettlement(sessionId, corpNo, token, approvalLines)));
    }

    @Operation(summary = "List this corp's saved settlements (summary rows) for the settlements table.")
    @GetMapping("/agents/settlement/saved")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> listSavedSettlements(
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /bizplay/agents/settlement/saved - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.listSettlements(corpNo)));
    }

    @Operation(summary = "Finalize the settlement in OUR DB only — marks the session APPROVED and "
            + "persists its draft_json, independent of the BizPlay report/draft POST.")
    @PostMapping("/agents/settlement/{sessionId}/save")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> saveSettlementToDb(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo) {
        log.info("POST /bizplay/agents/settlement/{}/save - corpNo={}", sessionId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.saveSettlement(corpNo, sessionId)));
    }

    @Operation(summary = "Load a settlement session's current state (draft_json + status). Restores the "
            + "registered-expenses table after the chat is closed and reopened.")
    @GetMapping("/agents/settlement/{sessionId}")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> getSettlementSession(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /bizplay/agents/settlement/{} - corpNo={}", sessionId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.getSession(corpNo, sessionId)));
    }

    @Operation(summary = "세금코드 목록 — the corporation's own tax-code master (id, taxCode, "
            + "taxName, deductionStatus). The expense card's 세금코드 row is picked from this list.")
    @GetMapping("/agents/settlement/tax-codes")
    public ResponseEntity<ApiResponse<JsonNode>> settlementTaxCodes(
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/tax-codes");
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getTaxCodes(token)));
    }


    @Operation(summary = "통화 목록 (외화/원화 구분, company feedback #5): the provider's own currency "
            + "master — [{nation, currencyCodeName, name}] — for the manual-expense form's dropdown. "
            + "KRW is first; the rest follow as the provider lists them.")
    @GetMapping("/agents/settlement/currencies")
    public ResponseEntity<ApiResponse<JsonNode>> settlementCurrencies(
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/currencies");
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getCurrencyCodes(token)));
    }


    @Operation(summary = "Edit ONE field of an expense already on the settlement (the preview card's "
            + "pencil). Applies the change to the RECEIPT in BizPlay (PATCH /receipt/etc-card/{id}), "
            + "then rebuilds the settlement line from the server's copy with 규정조회 and the "
            + "세금코드 re-resolved. Keys: merchant, amount, date, vehicleType, depart, arrival, "
            + "seatClass, routeType, usedStartDate, usedEndDate, roomType, partnerHotel, starRating, "
            + "personCount, foodDivisionType.")
    @PatchMapping("/agents/settlement/{sessionId}/expense/{receiptId}/field")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> editSettlementExpenseField(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @org.springframework.web.bind.annotation.PathVariable("receiptId") long receiptId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode patch,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        String key = patch.path("key").asText("");
        String value = patch.path("value").asText("");
        log.info("PATCH /bizplay/agents/settlement/{}/expense/{}/field - {}={}",
                sessionId, receiptId, key, value);
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.editExpenseField(
                sessionId, corpNo, receiptId, key, value, token)));
    }


    @Operation(summary = "Manual expense ⑧ STEP 1 — register the receipt with the base fields only "
            + "(POST /receipt/etc-card). Body {approvalDate, mestName, approvalAmount, …}. Returns the "
            + "created receipt (stashed) + the type-specific detail fields to collect in step 2.")
    @PostMapping("/agents/settlement/{sessionId}/manual-expense/create")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createSettlementManualReceipt(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @RequestBody JsonNode expense,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense/create - corpNo={}", sessionId, corpNo);
        return ResponseEntity.ok(ApiResponse.ok(
                bizplaySettlementAgentService.createManualReceipt(sessionId, corpNo, expense, token)));
    }

    @Operation(summary = "Attach the receipt image to the expense the CHAT collected, and register "
            + "it. Every 기타증빙 needs its image; a file cannot ride a chat turn, so the "
            + "conversation holds the expense and this multipart call supplies the file.")
    @PostMapping(value = "/agents/settlement/{sessionId}/manual-expense/attach",
            consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> attachHeldExpenseImage(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @org.springframework.web.bind.annotation.RequestPart(value = "image", required = false)
                    org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token)
            throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense/attach - corpNo={} image={}",
                sessionId, corpNo, image == null || image.isEmpty() ? "none" : image.getOriginalFilename());
        // Company feedback ⑦: the image is optional. Posting this endpoint with no file
        // registers the waiting expense without one.
        boolean noFile = image == null || image.isEmpty();
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.registerHeldExpense(
                sessionId, corpNo, noFile ? null : image.getBytes(),
                noFile ? null : image.getOriginalFilename(), token)));
    }

    @Operation(summary = "Manual expense ⑧ COMPLETE — register the whole receipt in one etc-card POST "
            + "(base + TranKind + detail + image). multipart: 'expense' (JSON base) + optional 'detail' "
            + "(JSON ReceiptEtcDto) + optional 'image' (file).")
    @PostMapping(value = "/agents/settlement/{sessionId}/manual-expense/complete", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> completeSettlementManualFull(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @org.springframework.web.bind.annotation.RequestPart("expense") String expenseJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "detail", required = false) String detailJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "image", required = false) org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense/complete - corpNo={}", sessionId, corpNo);
        JsonNode fields = objectMapper.readTree(expenseJson);
        JsonNode detail = (detailJson == null || detailJson.isBlank()) ? null : objectMapper.readTree(detailJson);
        // Every 기타증빙 carries its 증빙: the API refuses an image-less create, so a curl client
        // cannot do what the form no longer allows.
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("A receipt image is required for a 기타증빙 expense.");
        }
        byte[] imageBytes = image.getBytes();
        String imageName = image.getOriginalFilename();
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.addManualExpense(
                sessionId, corpNo, fields, detail, imageBytes, imageName, token)));
    }

    @Operation(summary = "Manual expense ⑧ STEP 2 — complete the receipt from step 1: PATCH "
            + "/receipt-etc/{id} with the additional detail + optional image, and map it into the draft. "
            + "multipart: optional 'detail' (JSON ReceiptEtcDto) + optional 'image' (file).")
    @PostMapping(value = "/agents/settlement/{sessionId}/manual-expense/detail", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> completeSettlementManualReceipt(
            @org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId,
            @RequestParam("corpNo") String corpNo,
            @org.springframework.web.bind.annotation.RequestPart(value = "detail", required = false) String detailJson,
            @org.springframework.web.bind.annotation.RequestPart(value = "image", required = false) org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/{}/manual-expense/detail - corpNo={}", sessionId, corpNo);
        JsonNode detail = (detailJson == null || detailJson.isBlank()) ? null : objectMapper.readTree(detailJson);
        byte[] imageBytes = (image == null || image.isEmpty()) ? null : image.getBytes();
        String imageName = image == null ? null : image.getOriginalFilename();
        return ResponseEntity.ok(ApiResponse.ok(bizplaySettlementAgentService.completeManualReceipt(
                sessionId, corpNo, detail, imageBytes, imageName, token)));
    }

    @Operation(summary = "Transport terminals/stations for the manual-expense depart/arrival dropdowns. "
            + "Optional vehicleType filter (AIR = airports, KTX = rail stations, BUS = bus terminals). "
            + "Returns [{id, name}].")
    @GetMapping("/agents/settlement/terminals")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> settlementTerminals(
            @RequestParam(value = "vehicleType", required = false) String vehicleType,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/terminals - vehicleType={}", vehicleType);
        JsonNode all = bizplayGatewayService.getEtcCardTerminals(token);
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        if (all != null && all.isArray()) {
            for (JsonNode t : all) {
                if (vehicleType != null && !vehicleType.isBlank()
                        && !vehicleType.equalsIgnoreCase(t.path("vehicleType").asText())) {
                    continue;
                }
                out.add(java.util.Map.of("id", t.path("id").asLong(), "name", t.path("name").asText("")));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    // --- personal-card general-expense browser (search receipts by date + status) -----------

    @Operation(summary = "Browse personal-card general expenses. status=NOT_DRAFTED (issued, default) or "
            + "NOT_ISSUED (incomplete). Paginated (default size 10). Returns the receipt-row array.")
    @GetMapping("/agents/settlement/receipts")
    public ResponseEntity<ApiResponse<JsonNode>> browseReceipts(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "status", required = false, defaultValue = "NOT_DRAFTED") String status,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/receipts - {}~{} status={} page={} size={}",
                startDate, endDate, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getGeneralExpenses(
                startDate, endDate, List.of(status), page, size, token)));
    }

    @Operation(summary = "One receipt's detail. NOT_ISSUED → GET /receipt/{id}; ISSUED → issued/bulk/{id}.")
    @GetMapping("/agents/settlement/receipts/{receiptId}")
    public ResponseEntity<ApiResponse<JsonNode>> receiptDetail(
            @org.springframework.web.bind.annotation.PathVariable("receiptId") long receiptId,
            @RequestParam(value = "status", required = false, defaultValue = "ISSUED") String status,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/agents/settlement/receipts/{} - status={}", receiptId, status);
        JsonNode detail = "NOT_ISSUED".equalsIgnoreCase(status)
                ? bizplayGatewayService.getReceiptById(receiptId, token)
                : bizplayGatewayService.getIssuedReceiptsBulk(List.of(receiptId), token);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @Operation(summary = "Upload a file (filebox, pdf2Img) and attach it to a receipt "
            + "(PATCH /receipt/image/{id}). multipart: 'image' (file).")
    @PostMapping(value = "/agents/settlement/receipts/{receiptId}/image", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<JsonNode>> attachReceiptImage(
            @org.springframework.web.bind.annotation.PathVariable("receiptId") long receiptId,
            @org.springframework.web.bind.annotation.RequestPart("image") org.springframework.web.multipart.MultipartFile image,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) throws java.io.IOException {
        log.info("POST /bizplay/agents/settlement/receipts/{}/image", receiptId);
        long fileId = bizplayGatewayService.uploadReceiptFile(image.getBytes(), image.getOriginalFilename(), token);
        bizplayGatewayService.attachReceiptImages(receiptId, List.of(fileId), token);
        com.fasterxml.jackson.databind.node.ObjectNode result = objectMapper.createObjectNode();
        result.put("fileId", fileId);
        result.put("receiptId", receiptId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // --- settlement conversation starter (per-corp greeting + example prompts) --------------

    @Operation(summary = "Get this corp's settlement conversation starter — the chat greeting and "
            + "example prompts (custom override when set, else the built-in default).")
    @GetMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> getSettlementStarter(
            @RequestParam("corpNo") String corpNo) {
        log.info("GET /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    @Operation(summary = "Set up this corp's settlement conversation starter: greeting and/or example "
            + "prompts. A provided value is saved; a blank/empty one resets that piece to the default. "
            + "Body {greeting, suggestions:[...]}.")
    @PutMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> setSettlementStarter(
            @RequestParam("corpNo") String corpNo,
            @RequestBody SettlementStarterRequest request) {
        log.info("PUT /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        applySettlementStarter(corpNo, request);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    /** POST alias of PUT — "create" reads naturally for first-time setup. */
    @Operation(summary = "Create this corp's settlement conversation starter (alias of PUT).")
    @PostMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> createSettlementStarter(
            @RequestParam("corpNo") String corpNo,
            @RequestBody SettlementStarterRequest request) {
        log.info("POST /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        applySettlementStarter(corpNo, request);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    @Operation(summary = "Reset this corp's settlement conversation starter (greeting + prompts) to defaults.")
    @DeleteMapping("/agents/settlement/starter")
    public ResponseEntity<ApiResponse<SettlementStarterResponse>> resetSettlementStarter(
            @RequestParam("corpNo") String corpNo) {
        log.info("DELETE /bizplay/agents/settlement/starter - corpNo={}", corpNo);
        agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE);
        agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS);
        return ResponseEntity.ok(ApiResponse.ok(settlementStarter(corpNo)));
    }

    /** Save the provided pieces: greeting and suggestions each saved when given, reset when blank/empty. */
    private void applySettlementStarter(String corpNo, SettlementStarterRequest request) {
        if (request == null) {
            return;
        }
        if (request.getGreeting() != null) {
            if (request.getGreeting().isBlank()) {
                agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE);
            } else {
                AgentPromptRequest g = new AgentPromptRequest();
                g.setPrompt(request.getGreeting().trim());
                agentPromptService.put(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE, g);
            }
        }
        if (request.getSuggestions() != null) {
            List<String> cleaned = request.getSuggestions().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .toList();
            if (cleaned.isEmpty()) {
                agentPromptService.reset(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS);
            } else {
                AgentPromptRequest s = new AgentPromptRequest();
                s.setPrompt(String.join("\n", cleaned));
                agentPromptService.put(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS, s);
            }
        }
    }

    /** The corp's effective settlement starter (custom-or-default greeting + prompts). */
    private SettlementStarterResponse settlementStarter(String corpNo) {
        AgentPromptResponse greeting =
                agentPromptService.get(corpNo, AgentPromptService.SETTLEMENT_STARTER_MESSAGE);
        AgentPromptResponse suggestions =
                agentPromptService.get(corpNo, AgentPromptService.SETTLEMENT_STARTER_SUGGESTIONS);
        String raw = suggestions.getEffectivePrompt() == null ? "" : suggestions.getEffectivePrompt();
        List<String> lines = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return SettlementStarterResponse.builder()
                .greeting(greeting.getEffectivePrompt())
                .suggestions(lines)
                .greetingSource(greeting.getSource())
                .suggestionsSource(suggestions.getSource())
                .build();
    }

    @Operation(summary = "Trip-plan requests by approval state. status=DRAFTED (default) lists the "
            + "plans still waiting for an approver - those CANNOT be settled; status=APPROVED lists "
            + "the settleable ones. Defaults to the last month. Rows are deduped by approvalId.")
    @GetMapping("/plans/by-status")
    public ResponseEntity<ApiResponse<JsonNode>> plansByStatus(
            @RequestParam("corpNo") String corpNo,
            @RequestParam(value = "travelerId", required = false) Long travelerId,
            @RequestParam(value = "status", required = false, defaultValue = "DRAFTED") String status,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String from = (startDate == null || startDate.isBlank()) ? today.minusMonths(1).toString() : startDate;
        String to = (endDate == null || endDate.isBlank()) ? today.toString() : endDate;
        long who = travelerId != null ? travelerId
                : Long.parseLong(bizplayProperties.getDefaultCorpUserId().trim());
        log.info("GET /bizplay/plans/by-status - corpNo={}, travelerId={}, status={}, {}~{}",
                corpNo, who, status, from, to);
        // DRAFTED rows exist ONLY on the unscoped path; the scoped one answers with APPROVED only.
        JsonNode raw = "APPROVED".equalsIgnoreCase(status)
                ? bizplayGatewayService.getPlanList(who, from, to, token)
                : bizplayGatewayService.getPendingPlanList(who, from, to, token);
        com.fasterxml.jackson.databind.node.ArrayNode out = objectMapper.createArrayNode();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode p : raw) {
                if (!status.equalsIgnoreCase(p.path("approvalStatusType").asText(""))) {
                    continue;
                }
                // The unscoped path ignores the period and repeats a plan per approval line.
                String start = p.path("bstrStartDate").asText("");
                String end = p.path("bstrEndDate").asText("");
                start = start.length() >= 10 ? start.substring(0, 10) : start;
                end = end.length() >= 10 ? end.substring(0, 10) : end;
                if (start.isEmpty() || end.isEmpty() || start.compareTo(to) > 0 || end.compareTo(from) < 0) {
                    continue;
                }
                if (seen.add(p.path("approvalId").asLong())) {
                    out.add(p);
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @Operation(summary = "Manual (non-chat) plan create: values from the UI form are written into "
            + "the retrieved form's ③-shaped skeleton and POSTed to BizPlay.")
    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<BizplayPlanAgentResponse>> createManualPlan(
            @RequestBody com.api.bizplay_conversational.model.request.BizplayManualPlanRequest request,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/plans - corpUserId={}, purposeId={}, segmentId={}",
                request.getCorpUserId(), request.getPurposeId(), request.getSegmentId());
        return ResponseEntity.ok(ApiResponse.ok(bizplayPlanAgentService.createManualPlan(request, token)));
    }

    @Operation(summary = "Staff roster of a corporation (for the Set-approval-order picker). "
            + "corporationId falls back to the token's currentCorpId.")
    @GetMapping("/corporation-users")
    public ResponseEntity<ApiResponse<JsonNode>> corporationUsers(
            @RequestParam(value = "corporationId", required = false) Long corporationId,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        Long corpId = corporationId != null ? corporationId : corporationIdFromToken(token);
        if (corpId == null) {
            throw new IllegalArgumentException("corporationId is required (none in token).");
        }
        log.info("GET /bizplay/corporation-users - corporationId={}", corpId);
        return ResponseEntity.ok(ApiResponse.ok(bizplayGatewayService.getCorporationUsers(corpId, token)));
    }

    @Operation(summary = "List selectable Travel-Purpose × Trip-Type options for a corporation user")
    @GetMapping("/purposes")
    public ResponseEntity<ApiResponse<List<PurposeOption>>> purposes(
            @RequestParam("corpUserId") String corpUserId,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/purposes - corpUserId={}", corpUserId);
        JsonNode catalog = bizplayGatewayService.getPurposeCatalog(corpUserId, token);
        return ResponseEntity.ok(ApiResponse.ok(purposeSegmentAgentService.flattenCatalog(catalog)));
    }

    @Operation(summary = "Resolve a user's message to a purpose/segment (or candidate chips)")
    @PostMapping("/resolve-purpose")
    public ResponseEntity<ApiResponse<PurposeResolutionResult>> resolvePurpose(
            @RequestBody ResolvePurposeRequest request,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("POST /bizplay/resolve-purpose - corpUserId={}", request.getCorpUserId());
        JsonNode catalog = bizplayGatewayService.getPurposeCatalog(request.getCorpUserId(), token);
        List<PurposeOption> options = purposeSegmentAgentService.flattenCatalog(catalog);
        return ResponseEntity.ok(ApiResponse.ok(
                purposeSegmentAgentService.resolve(request.getMessage(), options)));
    }

    @Operation(summary = "Fetch the dynamic form for a purpose/segment as a save-ready draft skeleton")
    @GetMapping("/form")
    public ResponseEntity<ApiResponse<BizplayFormResponse>> form(
            @RequestParam("purposeId") long purposeId,
            @RequestParam(value = "segmentId", required = false) Long segmentId,
            @RequestHeader(value = "X-Bizplay-Token", required = false) String token) {
        log.info("GET /bizplay/form - purposeId={}, segmentId={}", purposeId, segmentId);
        JsonNode papers = bizplayGatewayService.getPapers(purposeId, segmentId, token);
        return ResponseEntity.ok(ApiResponse.ok(
                formSkeletonService.buildPlanSkeleton(papers, purposeId, segmentId)));
    }

    @Getter
    @Setter
    public static class ResolvePurposeRequest {
        private String corpUserId;
        private String message;
    }
}
