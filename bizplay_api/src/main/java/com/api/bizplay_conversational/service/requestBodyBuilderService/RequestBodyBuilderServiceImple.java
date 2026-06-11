package com.api.bizplay_conversational.service.requestBodyBuilderService;

import com.api.bizplay_conversational.model.entity.AttachmentDraft;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.PendingTravelerDraft;
import com.api.bizplay_conversational.model.entity.TravelerDraft;
import com.api.bizplay_conversational.model.entity.TripInformationDraft;
import com.api.bizplay_conversational.model.entity.TripPlanDraft;
import com.api.bizplay_conversational.model.response.DraftEditPlan;
import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.model.response.TextAnalysisResult;
import com.api.bizplay_conversational.model.response.TravelerResolution;
import com.api.bizplay_conversational.service.staffService.StaffService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
        int pendingBefore = draft.getPendingTravelers() == null ? 0 : draft.getPendingTravelers().size();

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
                    case "add_traveler" -> add(applied, addTravelerByName(session, draft, trip, name));
                    case "remove_traveler" -> add(applied, removeTravelerByName(trip, name));
                    default -> log.info("Update agent: ignoring unknown op '{}'.", op);
                }
            } catch (RuntimeException ex) {
                log.warn("Update agent: failed to apply op '{}' (field={}, traveler={}): {}", op, field, name, ex.getMessage());
            }
        }

        // Persist when an edit was applied OR a duplicate-name traveler was newly held pending
        // (so the disambiguation survives to the next turn even though nothing was "applied").
        int pendingAfter = draft.getPendingTravelers() == null ? 0 : draft.getPendingTravelers().size();
        if (!applied.isEmpty() || pendingAfter != pendingBefore) {
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

    private String addTravelerByName(ConversationalAgentSession session, TripPlanDraft draft,
                                     TripInformationDraft trip, String name) {
        if (!isPresent(name)) {
            return null;
        }
        String inputName = name.trim();
        // Resolve against ALL staff matches (not just the first): duplicate names must be disambiguated
        // by the user, the same as the PDF/text-analysis traveler path.
        List<StaffLookupResult> candidates = staffService.findCandidates(session.getCorpNo(), inputName);
        if (candidates.isEmpty()) {
            log.info("Update agent: cannot add '{}' (not found in staff DB).", inputName);
            return null;
        }
        if (candidates.size() > 1) {
            // Duplicate names: hold for the user to pick which one; do NOT add a traveler yet.
            log.info("Update agent: '{}' matched {} staff; holding for disambiguation.", inputName, candidates.size());
            TextAnalysisResult.TravelerRoute route = new TextAnalysisResult.TravelerRoute();
            route.setName(inputName);
            addPendingTraveler(draft, route, inputName, candidates);
            return null;
        }
        StaffLookupResult staff = candidates.get(0);
        TravelerDraft target = findOrCreateTraveler(trip, staff.getStaffName());
        backfillStaffIdentity(target, staff);
        return "added " + staff.getStaffName();
    }

    @Override
    public TravelerResolution pendingResolution(ConversationalAgentSession session) {
        TravelerResolution resolution = new TravelerResolution();
        TripPlanDraft draft = readDraft(session);
        if (draft.getPendingTravelers() == null) {
            return resolution;
        }
        for (PendingTravelerDraft p : draft.getPendingTravelers()) {
            List<PendingTravelerDraft.Candidate> cands = p.getCandidates();
            if (cands == null || cands.size() <= 1) {
                continue; // not-found or already-unique pendings are auto-resolved elsewhere
            }
            List<StaffLookupResult> staffCandidates = new ArrayList<>();
            for (PendingTravelerDraft.Candidate c : cands) {
                staffCandidates.add(StaffLookupResult.builder()
                        .matched(true)
                        .staffId(c.getStaffId())
                        .staffName(c.getName())
                        .departmentName(c.getDepartment())
                        .position(c.getPosition())
                        .build());
            }
            resolution.getAmbiguous().add(new TravelerResolution.Ambiguous(p.getName(), staffCandidates));
        }
        return resolution;
    }

    @Override
    public List<String> pendingTravelerNames(ConversationalAgentSession session) {
        TripPlanDraft draft = readDraft(session);
        if (draft.getPendingTravelers() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (PendingTravelerDraft p : draft.getPendingTravelers()) {
            if (isPresent(p.getName())) {
                names.add(p.getName());
            }
        }
        return names;
    }

    @Override
    public String removePendingTraveler(ConversationalAgentSession session, String name) {
        if (!isPresent(name)) {
            return null;
        }
        TripPlanDraft draft = readDraft(session);
        String target = name.trim();
        String removed = null;
        if (draft.getPendingTravelers() != null) {
            java.util.Iterator<PendingTravelerDraft> it = draft.getPendingTravelers().iterator();
            while (it.hasNext()) {
                PendingTravelerDraft p = it.next();
                if (target.equalsIgnoreCase(p.getName())) {
                    removed = p.getName();
                    it.remove();
                }
            }
        }
        // Defensive: drop any unresolved placeholder traveler (no staffId) added under this name.
        TripInformationDraft trip = draft.getTripInformation();
        if (trip != null && trip.getTravelers() != null) {
            trip.getTravelers().removeIf(t -> target.equalsIgnoreCase(t.getName()) && t.getStaffId() == null);
        }
        if (removed != null) {
            writeDraft(session, draft);
        }
        return removed;
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
    public TravelerResolution mergeTextAnalysis(ConversationalAgentSession session, TextAnalysisResult analysis, boolean authoritative) {
        TravelerResolution resolution = new TravelerResolution();
        if (analysis == null) {
            return resolution;
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
        applyTravelerRoutes(session.getCorpNo(), draft, tripInfo, analysis, authoritative, resolution);

        writeDraft(session, draft);
        return resolution;
    }

    @Override
    public TravelerResolution mergeTravelersOnly(ConversationalAgentSession session, TextAnalysisResult analysis) {
        TravelerResolution resolution = new TravelerResolution();
        if (analysis == null || analysis.getTravelers() == null || analysis.getTravelers().isEmpty()) {
            return resolution;
        }
        TripPlanDraft draft = readDraft(session);
        TripInformationDraft tripInfo = ensureTripInformation(draft, session);
        // Travelers only: never authoritative for trip-level fields (those stay from the message).
        applyTravelerRoutes(session.getCorpNo(), draft, tripInfo, analysis, false, resolution);
        writeDraft(session, draft);
        return resolution;
    }

    /**
     * Merge the traveler routes from an analysis into the trip. Named travelers are resolved against
     * the staff DB: a UNIQUE match is added (identity backfilled); NO match is reported as not-found;
     * MULTIPLE matches (duplicate names) are held as pending picks. A nameless route is trip-wide
     * movement applied to every existing traveler that is still missing those fields.
     */
    private void applyTravelerRoutes(String corpNo, TripPlanDraft draft, TripInformationDraft tripInfo,
                                     TextAnalysisResult analysis, boolean authoritative, TravelerResolution resolution) {
        if (analysis.getTravelers() == null) {
            return;
        }
        for (TextAnalysisResult.TravelerRoute route : analysis.getTravelers()) {
            if (route == null) {
                continue;
            }
            if (isPresent(route.getName())) {
                String inputName = route.getName().trim();
                List<StaffLookupResult> candidates = staffService.findCandidates(corpNo, inputName);
                if (candidates.isEmpty()) {
                    // Not in the staff DB yet. Report it now, but HOLD it (with its route) so that once
                    // the staff is registered, a later turn re-resolves and adds it with this route.
                    log.info("Traveler '{}' not found in staff DB; holding pending.", inputName);
                    addOnce(resolution.getNotFound(), inputName);
                    addPendingTraveler(draft, route, inputName, candidates);
                    continue;
                }
                if (candidates.size() > 1) {
                    // Ambiguous: hold for the user to pick; do NOT add a traveler yet.
                    log.info("Traveler '{}' matched {} staff; holding for disambiguation.", inputName, candidates.size());
                    resolution.getAmbiguous().add(new TravelerResolution.Ambiguous(inputName, candidates));
                    addPendingTraveler(draft, route, inputName, candidates);
                    continue;
                }
                StaffLookupResult staff = candidates.get(0);
                TravelerDraft target = findOrCreateTraveler(tripInfo, staff.getStaffName());
                backfillStaffIdentity(target, staff);
                applyRoute(target, route, authoritative);
            } else {
                // Nameless route = trip-wide movement ("from Seoul to Toronto").
                for (TravelerDraft t : tripInfo.getTravelers()) {
                    applyRoute(t, route, false);
                }
            }
        }
    }

    /** Record an ambiguous traveler (its candidates + route) as pending, deduped by input name. */
    private void addPendingTraveler(TripPlanDraft draft, TextAnalysisResult.TravelerRoute route,
                                    String inputName, List<StaffLookupResult> candidates) {
        if (draft.getPendingTravelers() == null) {
            draft.setPendingTravelers(new ArrayList<>());
        }
        for (PendingTravelerDraft existing : draft.getPendingTravelers()) {
            if (inputName.equalsIgnoreCase(existing.getName())) {
                return; // already pending
            }
        }
        PendingTravelerDraft p = new PendingTravelerDraft();
        p.setName(inputName);
        p.setOrigin(blankToNull(route.getOrigin()));
        p.setDestination(blankToNull(route.getDestination()));
        p.setReturnPoint(blankToNull(route.getReturnPoint()));
        p.setTransportationMethod(blankToNull(route.getTransportationMethod()));
        for (StaffLookupResult c : candidates) {
            PendingTravelerDraft.Candidate cand = new PendingTravelerDraft.Candidate();
            cand.setStaffId(c.getStaffId());
            cand.setName(c.getStaffName());
            cand.setDepartment(c.getDepartmentName());
            cand.setPosition(c.getPosition());
            p.getCandidates().add(cand);
        }
        draft.getPendingTravelers().add(p);
    }

    @Override
    public List<String> resolvePendingTravelers(ConversationalAgentSession session, String message) {
        TripPlanDraft draft = readDraft(session);
        if (draft.getPendingTravelers() == null || draft.getPendingTravelers().isEmpty()) {
            return List.of();
        }
        TripInformationDraft tripInfo = ensureTripInformation(draft, session);
        String corpNo = session.getCorpNo();
        String msg = message == null ? "" : message.toLowerCase(Locale.ROOT);

        List<String> resolved = new ArrayList<>();
        boolean changed = false;
        java.util.Iterator<PendingTravelerDraft> it = draft.getPendingTravelers().iterator();
        while (it.hasNext()) {
            PendingTravelerDraft p = it.next();
            // Re-resolve against the CURRENT staff DB (it may have changed since this was held).
            List<StaffLookupResult> fresh = staffService.findCandidates(corpNo, p.getName());
            if (fresh.isEmpty()) {
                continue; // still not registered -> keep pending
            }
            StaffLookupResult chosen = fresh.size() == 1 ? fresh.get(0) : pickFromCandidates(fresh, msg);
            if (chosen == null) {
                // Now matches several -> refresh the stored candidates and keep pending for a pick.
                refreshCandidates(p, fresh);
                changed = true;
                continue;
            }
            // Add the traveler and apply the route held from the original extraction.
            TravelerDraft target = findOrCreateTraveler(tripInfo, chosen.getStaffName());
            if (target.getStaffId() == null) {
                target.setStaffId(chosen.getStaffId());
            }
            if (!isPresent(target.getDepartment())) {
                target.setDepartment(chosen.getDepartmentName());
            }
            if (!isPresent(target.getPosition())) {
                target.setPosition(chosen.getPosition());
            }
            if (!isPresent(target.getOrigin())) {
                target.setOrigin(p.getOrigin());
            }
            if (!isPresent(target.getDestination())) {
                target.setDestination(p.getDestination());
            }
            if (!isPresent(target.getReturnPoint())) {
                target.setReturnPoint(p.getReturnPoint());
            }
            if (!isPresent(target.getTransportationMethod())) {
                target.setTransportationMethod(p.getTransportationMethod());
            }
            resolved.add(chosen.getStaffName());
            it.remove();
            changed = true;
        }
        if (changed) {
            writeDraft(session, draft);
        }
        return resolved;
    }

    /**
     * Choose the candidate the user's message uniquely identifies — by staff id, department, or
     * position. Returns null when nothing or several candidates match (still ambiguous).
     */
    private StaffLookupResult pickFromCandidates(List<StaffLookupResult> candidates, String msgLower) {
        StaffLookupResult found = null;
        for (StaffLookupResult c : candidates) {
            if (c.getStaffId() != null && msgLower.contains(c.getStaffId().toString().toLowerCase(Locale.ROOT))) {
                return c; // an exact staff id is unambiguous
            }
            boolean hit = (isPresent(c.getDepartmentName()) && msgLower.contains(c.getDepartmentName().toLowerCase(Locale.ROOT)))
                    || (isPresent(c.getPosition()) && msgLower.contains(c.getPosition().toLowerCase(Locale.ROOT)));
            if (hit) {
                if (found != null) {
                    return null; // matched more than one candidate -> still ambiguous
                }
                found = c;
            }
        }
        return found;
    }

    /** Replace a pending traveler's stored candidates with a freshly-resolved set. */
    private void refreshCandidates(PendingTravelerDraft p, List<StaffLookupResult> fresh) {
        p.getCandidates().clear();
        for (StaffLookupResult c : fresh) {
            PendingTravelerDraft.Candidate cand = new PendingTravelerDraft.Candidate();
            cand.setStaffId(c.getStaffId());
            cand.setName(c.getStaffName());
            cand.setDepartment(c.getDepartmentName());
            cand.setPosition(c.getPosition());
            p.getCandidates().add(cand);
        }
    }

    private void addOnce(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) {
                return;
            }
        }
        list.add(value);
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

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
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
