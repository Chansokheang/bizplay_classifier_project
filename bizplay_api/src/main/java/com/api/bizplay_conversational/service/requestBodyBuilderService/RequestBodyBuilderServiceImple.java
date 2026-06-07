package com.api.bizplay_conversational.service.requestBodyBuilderService;

import com.api.bizplay_conversational.model.entity.AttachmentDraft;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.TravelerDraft;
import com.api.bizplay_conversational.model.entity.TripInformationDraft;
import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.DraftEditPlan;
import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.api.bizplay_conversational.service.staffService.StaffService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestBodyBuilderServiceImple implements RequestBodyBuilderService {

    private final ObjectMapper objectMapper;
    private final StaffService staffService;

    @Override
    public void mergeStaff(ConversationalAgentSession session, StaffLookupResult result) {
        if (result == null || result.getStaffId() == null) {
            return;
        }
        TripPlanDraft draft = readDraft(session);
        TripInformationDraft tripInfo = ensureTripInformation(draft, session);
        List<TravelerDraft> travelers = tripInfo.getTravelers();

        String staffId = result.getStaffId().toString();
        for (TravelerDraft existing : travelers) {
            boolean sameId = existing.getStaffId() != null && staffId.equals(existing.getStaffId().toString());
            boolean sameName = existing.getStaffId() == null
                    && result.getStaffName() != null
                    && result.getStaffName().equalsIgnoreCase(existing.getName());
            if (sameId || sameName) {
                // Backfill identity fields on an existing (e.g. text-analysis-created) traveler.
                if (existing.getStaffId() == null) {
                    existing.setStaffId(result.getStaffId());
                }
                if (existing.getDepartment() == null) {
                    existing.setDepartment(result.getDepartmentName());
                }
                if (existing.getPosition() == null) {
                    existing.setPosition(result.getPosition());
                }
                writeDraft(session, draft);
                return;
            }
        }

        TravelerDraft traveler = new TravelerDraft();
        traveler.setStaffId(result.getStaffId());
        traveler.setName(result.getStaffName());
        traveler.setDepartment(result.getDepartmentName());
        traveler.setPosition(result.getPosition());
        travelers.add(traveler);

        writeDraft(session, draft);
    }

    @Override
    public void mergeSpreadsheet(ConversationalAgentSession session, SpreadsheetAnalysisResult spreadsheet) {
        if (spreadsheet == null || spreadsheet.getMatched() == null || spreadsheet.getMatched().isEmpty()) {
            return;
        }
        TripPlanDraft draft = readDraft(session);
        TripInformationDraft tripInfo = ensureTripInformation(draft, session);
        List<TravelerDraft> travelers = tripInfo.getTravelers();

        for (SpreadsheetAnalysisResult.ResolvedStaff staff : spreadsheet.getMatched()) {
            if (staff == null || !staff.isMatched()) {
                continue; // only add staff that resolved against the DB
            }
            String name = staff.getStaffName() != null ? staff.getStaffName() : staff.getExtractedName();
            if (name == null || name.isBlank()) {
                continue;
            }
            TravelerDraft target = findOrCreateTraveler(tripInfo, name.trim());
            if (staff.getStaffId() != null && target.getStaffId() == null) {
                try {
                    target.setStaffId(java.util.UUID.fromString(staff.getStaffId()));
                } catch (IllegalArgumentException ignored) {
                    // non-UUID id, leave null
                }
            }
            if (target.getDepartment() == null && staff.getDepartmentName() != null) {
                target.setDepartment(staff.getDepartmentName());
            }
            if (target.getPosition() == null && staff.getPosition() != null) {
                target.setPosition(staff.getPosition());
            }
        }

        writeDraft(session, draft);
    }

    @Override
    public void mergeAttachment(ConversationalAgentSession session, String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        TripPlanDraft draft = readDraft(session);
        if (draft.getCorpNo() == null) {
            draft.setCorpNo(session.getCorpNo());
        }
        if (draft.getAttachments() == null) {
            draft.setAttachments(new java.util.ArrayList<>());
        }
        for (AttachmentDraft existing : draft.getAttachments()) {
            if (fileId.equals(existing.getFileId())) {
                return; // already recorded
            }
        }
        AttachmentDraft attachment = new AttachmentDraft();
        attachment.setType("File");
        attachment.setFileId(fileId);
        draft.getAttachments().add(attachment);

        writeDraft(session, draft);
    }

    @Override
    public List<String> applyEdits(ConversationalAgentSession session, DraftEditPlan plan) {
        List<String> applied = new java.util.ArrayList<>();
        if (plan == null || plan.getEdits() == null || plan.getEdits().isEmpty()) {
            return applied;
        }
        TripPlanDraft draft = readDraft(session);
        TripInformationDraft trip = ensureTripInformation(draft, session);

        for (DraftEditPlan.Edit e : plan.getEdits()) {
            if (e == null || !isPresent(e.getOp())) {
                continue;
            }
            String op = e.getOp().trim().toLowerCase(java.util.Locale.ROOT);
            String field = e.getField() == null ? null : e.getField().trim().toLowerCase(java.util.Locale.ROOT);
            String value = e.getValue();
            String name = e.getTraveler();
            try {
                switch (op) {
                    case "set_trip_field" -> add(applied, setTripField(trip, field, value));
                    case "clear_trip_field" -> add(applied, setTripField(trip, field, null));
                    case "set_traveler_field" -> add(applied, setOneTravelerField(trip, name, field, value));
                    case "clear_traveler_field" -> add(applied, setOneTravelerField(trip, name, field, null));
                    case "set_all_travelers_field" -> add(applied, setAllTravelersField(trip, field, value));
                    case "add_traveler" -> add(applied, addTravelerByName(session, trip, name));
                    case "remove_traveler" -> add(applied, removeTravelerByName(trip, name));
                    default -> log.info("Update agent: ignoring unknown op '{}'.", op);
                }
            } catch (RuntimeException ex) {
                log.warn("Update agent: failed to apply op '{}' (field={}, traveler={}): {}", op, field, name, ex.getMessage());
            }
        }

        if (!applied.isEmpty()) {
            writeDraft(session, draft);
        }
        return applied;
    }

    private void add(List<String> applied, String description) {
        if (description != null) {
            applied.add(description);
        }
    }

    /** Set or clear (value=null) a trip-level field. Returns a description, or null if not applied. */
    private String setTripField(TripInformationDraft trip, String field, String value) {
        if (field == null) {
            return null;
        }
        boolean clear = !isPresent(value);
        String v = clear ? null : value.trim();
        switch (field) {
            case "destination" -> trip.setDestination(v);
            case "type", "purpose" -> {
                if (clear) {
                    trip.setPurpose(null);
                } else {
                    String t = normalizeTripType(v);
                    if (t == null) {
                        return null; // not one of the three valid types -> ignore
                    }
                    trip.setPurpose(t);
                    v = t;
                }
            }
            case "title" -> trip.setTitle(v);
            case "content" -> trip.setContent(v);
            case "period" -> trip.setBusinessPeriod(v);
            case "start_date" -> {
                if (clear) {
                    trip.setBusinessStartDate(null);
                } else {
                    trip.setBusinessStartDate(parseDate(v));
                }
            }
            case "end_date" -> {
                if (clear) {
                    trip.setBusinessEndDate(null);
                } else {
                    trip.setBusinessEndDate(parseDate(v));
                }
            }
            default -> {
                return null;
            }
        }
        return clear ? ("cleared trip " + field) : ("trip " + field + " set to " + v);
    }

    private String setOneTravelerField(TripInformationDraft trip, String name, String field, String value) {
        TravelerDraft t = findTravelerByName(trip, name);
        if (t == null) {
            return null;
        }
        boolean clear = !isPresent(value);
        String v = clear ? null : value.trim();
        if (!setTravelerFieldOn(t, field, v, clear)) {
            return null;
        }
        // Destination is trip-wide: setting it for one traveler updates the whole trip, so the
        // trip-level destination stays authoritative and inheritCommonRoute does not overwrite it
        // back to a stale value.
        if ("destination".equals(field) && !clear) {
            trip.setDestination(v);
        }
        return clear ? ("cleared " + field + " for " + t.getName())
                : (t.getName() + " " + field + " set to " + v);
    }

    private String setAllTravelersField(TripInformationDraft trip, String field, String value) {
        if (!isPresent(value) || trip.getTravelers() == null || trip.getTravelers().isEmpty()) {
            return null;
        }
        String v = value.trim();
        // Destination is trip-wide: also set the trip-level destination so the whole trip is consistent.
        if ("destination".equals(field)) {
            trip.setDestination(v);
        }
        int count = 0;
        for (TravelerDraft t : trip.getTravelers()) {
            if (setTravelerFieldOn(t, field, v, false)) {
                count++;
            }
        }
        return count == 0 ? null : ("all travelers " + field + " set to " + v);
    }

    /** Apply a field on one traveler. Returns true if the field name was valid. */
    private boolean setTravelerFieldOn(TravelerDraft t, String field, String value, boolean clear) {
        if (field == null) {
            return false;
        }
        switch (field) {
            case "origin" -> t.setOrigin(value);
            case "destination" -> t.setDestination(value);
            case "return_point", "returnpoint" -> t.setReturnPoint(value);
            case "transportation", "transportationmethod" -> t.setTransportationMethod(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    private String addTravelerByName(ConversationalAgentSession session, TripInformationDraft trip, String name) {
        if (!isPresent(name)) {
            return null;
        }
        StaffLookupResult staff = staffService.lookup(session.getCorpNo(), name.trim());
        if (staff == null || !staff.isMatched()) {
            log.info("Update agent: cannot add '{}' (not found in staff DB).", name);
            return null;
        }
        TravelerDraft target = findOrCreateTraveler(trip, staff.getStaffName());
        backfillStaffIdentity(target, staff);
        return "added " + staff.getStaffName();
    }

    private String removeTravelerByName(TripInformationDraft trip, String name) {
        if (!isPresent(name) || trip.getTravelers() == null) {
            return null;
        }
        String target = name.trim();
        TravelerDraft removed = null;
        for (TravelerDraft t : trip.getTravelers()) {
            if (target.equalsIgnoreCase(t.getName())) {
                removed = t;
                break;
            }
        }
        if (removed == null) {
            return null;
        }
        trip.getTravelers().remove(removed);
        return "removed " + removed.getName();
    }

    private TravelerDraft findTravelerByName(TripInformationDraft trip, String name) {
        if (!isPresent(name) || trip.getTravelers() == null) {
            return null;
        }
        for (TravelerDraft t : trip.getTravelers()) {
            if (name.trim().equalsIgnoreCase(t.getName())) {
                return t;
            }
        }
        return null;
    }

    /** Normalize a free-text type to one of the three canonical values, or null if unrecognized. */
    private String normalizeTripType(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("overseas") || s.contains("oversea") || s.contains("international")
                || s.contains("abroad") || s.contains("해외")) {
            return "Overseas business trip";
        }
        if (s.contains("educat") || s.contains("training") || s.contains("seminar")
                || s.contains("workshop") || s.contains("course") || s.contains("교육")) {
            return "Educational business trip";
        }
        if (s.contains("general") || s.contains("일반")) {
            return "General business trip";
        }
        return null;
    }

    private java.time.LocalDate parseDate(String raw) {
        return java.time.LocalDate.parse(raw.trim());
    }

    @Override
    public void inheritCommonRoute(ConversationalAgentSession session) {
        TripPlanDraft draft = readDraft(session);
        TripInformationDraft trip = draft.getTripInformation();
        if (trip == null) {
            return;
        }

        // Default the trip type once, only when still unset — never clobber an extracted type.
        if (!isPresent(trip.getPurpose())) {
            trip.setPurpose("General business trip");
        }

        List<TravelerDraft> travelers = trip.getTravelers();
        if (travelers != null && !travelers.isEmpty()) {
            // Destination is TRIP-WIDE: the trip-level destination is authoritative and is applied to
            // every traveler (overwrite), so "change destination to X" updates the whole trip. The
            // other route fields are shared only as defaults: filled when blank, but may differ per
            // traveler, so they are never overwritten. Ambiguous shared values are not propagated.
            String destination = isPresent(trip.getDestination())
                    ? trip.getDestination().trim()
                    : singleDistinct(travelers, TravelerDraft::getDestination);
            boolean destinationTripWide = isPresent(trip.getDestination());
            String origin = singleDistinct(travelers, TravelerDraft::getOrigin);
            String returnPoint = singleDistinct(travelers, TravelerDraft::getReturnPoint);
            String transportation = singleDistinct(travelers, TravelerDraft::getTransportationMethod);

            for (TravelerDraft t : travelers) {
                if (isPresent(destination) && (destinationTripWide || !isPresent(t.getDestination()))) {
                    t.setDestination(destination);
                }
                if (!isPresent(t.getOrigin()) && isPresent(origin)) {
                    t.setOrigin(origin);
                }
                if (!isPresent(t.getReturnPoint()) && isPresent(returnPoint)) {
                    t.setReturnPoint(returnPoint);
                }
                if (!isPresent(t.getTransportationMethod()) && isPresent(transportation)) {
                    t.setTransportationMethod(transportation);
                }
            }
        }

        // Auto-generate a title from the trip's content when none is set (fill-blank only, so a
        // user-provided title is never overwritten). Runs last, after destination/route are settled.
        ensureTitle(trip);

        writeDraft(session, draft);
    }

    /**
     * Build a human-readable title from the draft when {@code title} is blank, e.g.
     * "Overseas business trip to Cambodia (2026-06-20 ~ 2026-06-25)". Falls back gracefully when
     * fields are missing. Never overwrites a title the user already set.
     */
    private void ensureTitle(TripInformationDraft trip) {
        if (isPresent(trip.getTitle())) {
            return;
        }
        // Destination: prefer the trip-level value, else a single shared traveler destination.
        String destination = isPresent(trip.getDestination())
                ? trip.getDestination().trim()
                : (trip.getTravelers() == null ? null
                        : singleDistinct(trip.getTravelers(), TravelerDraft::getDestination));

        // Lead with the trip type (purpose) when set; otherwise a neutral "Business trip".
        String lead = isPresent(trip.getPurpose()) ? trip.getPurpose().trim() : "Business trip";
        StringBuilder title = new StringBuilder(lead);
        if (isPresent(destination)) {
            title.append(" to ").append(destination);
        }

        String dates = formatDateRange(trip);
        if (isPresent(dates)) {
            title.append(" (").append(dates).append(')');
        }

        String result = title.toString().trim();
        if (isPresent(result)) {
            trip.setTitle(result);
        }
    }

    /** A compact date range for the title, or null when no dates are available. */
    private String formatDateRange(TripInformationDraft trip) {
        java.time.LocalDate start = trip.getBusinessStartDate();
        java.time.LocalDate end = trip.getBusinessEndDate();
        if (start != null && end != null) {
            return start + " ~ " + end;
        }
        if (start != null) {
            return start.toString();
        }
        if (end != null) {
            return end.toString();
        }
        return isPresent(trip.getBusinessPeriod()) ? trip.getBusinessPeriod().trim() : null;
    }

    /** The single distinct non-blank value across travelers, or null if none or several differ. */
    private String singleDistinct(List<TravelerDraft> travelers, java.util.function.Function<TravelerDraft, String> getter) {
        String found = null;
        for (TravelerDraft t : travelers) {
            String v = getter.apply(t);
            if (v == null || v.isBlank()) {
                continue;
            }
            v = v.trim();
            if (found == null) {
                found = v;
            } else if (!found.equalsIgnoreCase(v)) {
                return null; // travelers disagree -> ambiguous, do not propagate
            }
        }
        return found;
    }

    @Override
    public TripPlanDraft snapshot(ConversationalAgentSession session) {
        return readDraft(session);
    }

    @Override
    public void stampMissingFields(ConversationalAgentSession session, List<String> missing) {
        TripPlanDraft draft = readDraft(session);
        draft.setMissingFields(missing == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(missing));
        writeDraft(session, draft);
    }

    @Override
    public void mergeTextAnalysis(ConversationalAgentSession session, TextAnalysisResult analysis, boolean authoritative) {
        if (analysis == null) {
            return;
        }
        TripPlanDraft draft = readDraft(session);
        TripInformationDraft tripInfo = ensureTripInformation(draft, session);

        // Trip-level fields. An authoritative source (the user's message) overwrites; a supplementary
        // source (a PDF) only fills blanks so it can never override a higher-priority value.
        if (isPresent(analysis.getTripDestination()) && (authoritative || !isPresent(tripInfo.getDestination()))) {
            tripInfo.setDestination(analysis.getTripDestination().trim());
        }
        if (isPresent(analysis.getBusinessPeriod()) && (authoritative || !isPresent(tripInfo.getBusinessPeriod()))) {
            tripInfo.setBusinessPeriod(analysis.getBusinessPeriod().trim());
        }

        // The form uses a SINGLE field here: Purpose (the 출장목적 dropdown), which holds exactly one
        // of the three trip types. It never holds a free-text description, and Content is not used.
        if (isPresent(analysis.getTripType()) && (authoritative || !isPresent(tripInfo.getPurpose()))) {
            tripInfo.setPurpose(analysis.getTripType().trim());
        } else if (authoritative && isPresent(tripInfo.getPurpose()) && !isValidTripType(tripInfo.getPurpose())) {
            // Clear stray free text left in Purpose by an older draft when no new type was classified.
            tripInfo.setPurpose(null);
        }
        if (isPresent(analysis.getTitle()) && (authoritative || !isPresent(tripInfo.getTitle()))) {
            tripInfo.setTitle(analysis.getTitle().trim());
        }
        if (authoritative || tripInfo.getBusinessStartDate() == null) {
            setDateIfPresent(analysis.getBusinessStartDate(), tripInfo::setBusinessStartDate);
        }
        if (authoritative || tripInfo.getBusinessEndDate() == null) {
            setDateIfPresent(analysis.getBusinessEndDate(), tripInfo::setBusinessEndDate);
        }

        // Per-traveler routes.
        applyTravelerRoutes(session.getCorpNo(), tripInfo, analysis, authoritative);

        writeDraft(session, draft);
    }

    @Override
    public void mergeTravelersOnly(ConversationalAgentSession session, TextAnalysisResult analysis) {
        if (analysis == null || analysis.getTravelers() == null || analysis.getTravelers().isEmpty()) {
            return;
        }
        TripPlanDraft draft = readDraft(session);
        TripInformationDraft tripInfo = ensureTripInformation(draft, session);
        // Travelers only: never authoritative for trip-level fields (those stay from the message).
        applyTravelerRoutes(session.getCorpNo(), tripInfo, analysis, false);
        writeDraft(session, draft);
    }

    /**
     * Merge the traveler routes from an analysis into the trip. Named travelers are resolved against
     * the staff DB (unmatched names dropped, matched ones backfilled); a nameless route is treated as
     * trip-wide movement applied to every existing traveler that is still missing those fields.
     */
    private void applyTravelerRoutes(String corpNo, TripInformationDraft tripInfo,
                                     TextAnalysisResult analysis, boolean authoritative) {
        if (analysis.getTravelers() == null) {
            return;
        }
        for (TextAnalysisResult.TravelerRoute route : analysis.getTravelers()) {
            if (route == null) {
                continue;
            }
            if (isPresent(route.getName())) {
                // Named traveler: resolve against the staff DB (same as the spreadsheet path).
                // Unmatched names are dropped; matched names get their identity backfilled.
                StaffLookupResult staff = staffService.lookup(corpNo, route.getName().trim());
                if (staff == null || !staff.isMatched()) {
                    log.info("Text analysis traveler '{}' not found in staff DB; skipping.", route.getName());
                    continue;
                }
                TravelerDraft target = findOrCreateTraveler(tripInfo, staff.getStaffName());
                backfillStaffIdentity(target, staff);
                // Overwrite named-route fields only for an authoritative source; otherwise fill blanks.
                applyRoute(target, route, authoritative);
            } else {
                // Nameless route = trip-wide movement ("from Seoul to Toronto"): apply it to
                // every existing traveler that is still missing those fields. Do NOT create a
                // nameless phantom traveler.
                for (TravelerDraft t : tripInfo.getTravelers()) {
                    applyRoute(t, route, false);
                }
            }
        }
    }

    /** Fill traveler identity (staffId/department/position) from a matched staff record, without clobbering. */
    private void backfillStaffIdentity(TravelerDraft target, StaffLookupResult staff) {
        if (target.getStaffId() == null && staff.getStaffId() != null) {
            target.setStaffId(staff.getStaffId());
        }
        if (target.getDepartment() == null && staff.getDepartmentName() != null) {
            target.setDepartment(staff.getDepartmentName());
        }
        if (target.getPosition() == null && staff.getPosition() != null) {
            target.setPosition(staff.getPosition());
        }
    }

    /**
     * Copy route fields onto a traveler.
     * @param overwrite when true, set the field even if already populated (explicit named route);
     *                  when false, only fill blanks (trip-wide default).
     */
    private void applyRoute(TravelerDraft target, TextAnalysisResult.TravelerRoute route, boolean overwrite) {
        if (isPresent(route.getOrigin()) && (overwrite || !isPresent(target.getOrigin()))) {
            target.setOrigin(route.getOrigin().trim());
        }
        if (isPresent(route.getDestination()) && (overwrite || !isPresent(target.getDestination()))) {
            target.setDestination(route.getDestination().trim());
        }
        if (isPresent(route.getReturnPoint()) && (overwrite || !isPresent(target.getReturnPoint()))) {
            target.setReturnPoint(route.getReturnPoint().trim());
        }
        if (isPresent(route.getTransportationMethod()) && (overwrite || !isPresent(target.getTransportationMethod()))) {
            target.setTransportationMethod(route.getTransportationMethod().trim());
        }
    }

    // --- helpers -----------------------------------------------------------------

    private TravelerDraft findOrCreateTraveler(TripInformationDraft tripInfo, String name) {
        List<TravelerDraft> travelers = tripInfo.getTravelers();
        if (isPresent(name)) {
            for (TravelerDraft t : travelers) {
                if (name.trim().equalsIgnoreCase(t.getName())) {
                    return t;
                }
            }
        }
        TravelerDraft created = new TravelerDraft();
        if (isPresent(name)) {
            created.setName(name.trim());
        }
        travelers.add(created);
        return created;
    }

    /** The plan category is fixed for the trip-plan workflow; the create API requires it (@NotBlank). */
    private static final String DEFAULT_PLAN_TYPE = "Business Trip Plan";

    private TripInformationDraft ensureTripInformation(TripPlanDraft draft, ConversationalAgentSession session) {
        if (draft.getCorpNo() == null) {
            draft.setCorpNo(session.getCorpNo());
        }
        if (draft.getPlanType() == null || draft.getPlanType().isBlank()) {
            draft.setPlanType(DEFAULT_PLAN_TYPE);
        }
        TripInformationDraft tripInfo = draft.getTripInformation();
        if (tripInfo == null) {
            tripInfo = new TripInformationDraft();
            draft.setTripInformation(tripInfo);
        }
        return tripInfo;
    }

    private void setDateIfPresent(String raw, java.util.function.Consumer<java.time.LocalDate> setter) {
        if (!isPresent(raw)) {
            return;
        }
        try {
            setter.accept(java.time.LocalDate.parse(raw.trim()));
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Ignoring unparseable date from text analysis: {}", raw);
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /** The three valid business-trip types (출장목적). Anything else in Purpose is stray free text. */
    private static final java.util.Set<String> VALID_TRIP_TYPES = java.util.Set.of(
            "general business trip",
            "educational business trip",
            "overseas business trip");

    private boolean isValidTripType(String value) {
        return isPresent(value) && VALID_TRIP_TYPES.contains(value.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Deserialize draft_json into the typed TripPlanDraft skeleton, tolerating empty/invalid drafts. */
    private TripPlanDraft readDraft(ConversationalAgentSession session) {
        JsonNode existing = session.getDraftJson();
        if (existing != null && existing.isObject() && existing.size() > 0) {
            try {
                return objectMapper.treeToValue(existing, TripPlanDraft.class);
            } catch (JsonProcessingException e) {
                log.warn("Could not parse draft_json for session {}; starting a fresh draft: {}",
                        session.getId(), e.getMessage());
            }
        }
        return new TripPlanDraft();
    }

    private void writeDraft(ConversationalAgentSession session, TripPlanDraft draft) {
        session.setDraftJson(objectMapper.valueToTree(draft));
    }
}
