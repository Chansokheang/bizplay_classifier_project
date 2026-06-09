package com.api.bizplay_conversational.service.reportBodyBuilderService;

import com.api.bizplay_conversational.model.entity.AttachmentDraft;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.ExpenseDetailDraft;
import com.api.bizplay_conversational.model.entity.ExpenseSectionDraft;
import com.api.bizplay_conversational.model.entity.TravelerDraft;
import com.api.bizplay_conversational.model.entity.TripInformationDraft;
import com.api.bizplay_conversational.model.entity.TripReportDraft;
import com.api.bizplay_conversational.model.response.ExpenseAnalysisResult;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.PlanTravelerResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportBodyBuilderServiceImple implements ReportBodyBuilderService {

    /** Default per-section labels (the section "Type" header) when not otherwise set. */
    private static final String TYPE_COST = "Accommodation";
    private static final String TYPE_TRANSPORTATION = "Transportation";
    private static final String TYPE_ETC = "Miscellaneous";

    private final ObjectMapper objectMapper;

    @Override
    public void bootstrapFromPlan(ConversationalAgentSession session, PlanResponse plan) {
        TripReportDraft draft = readDraft(session);
        draft.setAgentSessionId(session.getId());
        if (plan == null) {
            writeDraft(session, draft);
            return;
        }
        draft.setTripPlanId(plan.getId());
        draft.setCorpNo(plan.getCorpNo() != null ? plan.getCorpNo() : session.getCorpNo());
        draft.setPlanType(plan.getPlanType());

        TripInformationDraft trip = draft.getTripInformation();
        if (trip == null) {
            trip = new TripInformationDraft();
            draft.setTripInformation(trip);
        }
        trip.setPurpose(plan.getPurpose());
        trip.setBusinessPeriod(plan.getBusinessPeriod());
        trip.setBusinessStartDate(plan.getBusinessStartDate());
        trip.setBusinessEndDate(plan.getBusinessEndDate());
        trip.setDestination(plan.getDestination());
        trip.setTitle(plan.getTitle());
        trip.setContent(plan.getContent());
        trip.setBusinessTripClassification(plan.getBusinessTripClassification());

        List<TravelerDraft> travelers = new ArrayList<>();
        if (plan.getTravelers() != null) {
            for (PlanTravelerResponse t : plan.getTravelers()) {
                if (t == null) {
                    continue;
                }
                TravelerDraft td = new TravelerDraft();
                td.setName(t.getName());
                td.setDepartment(t.getDepartment());
                td.setPosition(t.getPosition());
                td.setOrigin(t.getOrigin());
                td.setDestination(t.getDestination());
                td.setReturnPoint(t.getReturnPoint());
                travelers.add(td);
            }
        }
        trip.setTravelers(travelers);

        writeDraft(session, draft);
    }

    @Override
    public int mergeExpenseAnalysis(ConversationalAgentSession session, ExpenseAnalysisResult analysis, String sourceFileId) {
        if (analysis == null || analysis.getLines() == null || analysis.getLines().isEmpty()) {
            return 0;
        }
        TripReportDraft draft = readDraft(session);

        int merged = 0;
        Set<ExpenseSectionDraft> touched = new LinkedHashSet<>();
        for (ExpenseAnalysisResult.Line line : analysis.getLines()) {
            if (line == null) {
                continue;
            }
            ExpenseSectionDraft section = sectionFor(draft, line.getSection());
            if (section == null) {
                log.info("Skipping expense line with unknown section '{}'.", line.getSection());
                continue;
            }
            ensureSectionType(section, line.getSection());
            ExpenseDetailDraft detail = toDetail(line, section.getDetails().size() + 1);
            section.getDetails().add(detail);
            touched.add(section);
            merged++;
        }

        // Attach the source receipt to each section it contributed to (per-section Attachemnt).
        if (isPresent(sourceFileId)) {
            for (ExpenseSectionDraft section : touched) {
                attachTo(section.getAttachments(), sourceFileId);
            }
        }

        if (merged > 0) {
            writeDraft(session, draft);
        }
        return merged;
    }

    @Override
    public void mergeAttachment(ConversationalAgentSession session, String fileId) {
        if (!isPresent(fileId)) {
            return;
        }
        TripReportDraft draft = readDraft(session);
        if (draft.getAttachments() == null) {
            draft.setAttachments(new ArrayList<>());
        }
        attachTo(draft.getAttachments(), fileId);
        writeDraft(session, draft);
    }

    @Override
    public TripReportDraft snapshot(ConversationalAgentSession session) {
        return readDraft(session);
    }

    @Override
    public void stampMissingFields(ConversationalAgentSession session, List<String> missing) {
        TripReportDraft draft = readDraft(session);
        draft.setMissingFields(missing == null ? new ArrayList<>() : new ArrayList<>(missing));
        writeDraft(session, draft);
    }

    // --- helpers -----------------------------------------------------------------

    private ExpenseSectionDraft sectionFor(TripReportDraft draft, String section) {
        if (section == null) {
            return null;
        }
        return switch (section.trim().toUpperCase(Locale.ROOT)) {
            case "COST" -> draft.getCostInformation();
            case "TRANSPORTATION" -> draft.getTransportationInformation();
            case "ETC" -> draft.getEtc();
            default -> null;
        };
    }

    private void ensureSectionType(ExpenseSectionDraft section, String sectionCode) {
        if (isPresent(section.getType())) {
            return;
        }
        section.setType(switch (sectionCode.trim().toUpperCase(Locale.ROOT)) {
            case "COST" -> TYPE_COST;
            case "TRANSPORTATION" -> TYPE_TRANSPORTATION;
            default -> TYPE_ETC;
        });
    }

    private ExpenseDetailDraft toDetail(ExpenseAnalysisResult.Line line, int sequenceNo) {
        ExpenseDetailDraft d = new ExpenseDetailDraft();
        d.setSequenceNo(sequenceNo);
        d.setTaxCode(line.getTaxCode());
        // The draft detail has a single "Type" field; if the analyzer only produced a category
        // (e.g. "flight"), use it as the type so no information is lost.
        d.setType(isPresent(line.getType()) ? line.getType() : line.getCategory());
        d.setUsePurpose(line.getUse());
        d.setAccount(line.getAccount());
        d.setBudgetDepartment(line.getBudgetDepartment());
        d.setTransportationMethod(line.getTransportationMethod());
        d.setOrigin(line.getOrigin());
        d.setDestination(line.getDestination());
        d.setStartDate(parseDate(line.getStartDate()));
        d.setEndDate(parseDate(line.getEndDate()));
        d.setUsageDate(parseDate(line.getUsageDate()));
        d.setEvidenceDate(parseDate(line.getProofDate()));
        d.setDescription(line.getDescription());
        d.setVendor(line.getVendor());
        d.setSupplyPrice(line.getSupplyPrice());
        d.setTax(line.getTax());
        d.setApplicationAmount(line.getAmountUsed());
        d.setPolicyAmount(line.getRegulatedAmount());
        d.setExcessReason(line.getApplicationAmountReasonForExcess());
        d.setNote(line.getNote());
        d.setApprovalNumber(line.getApprovalNumber());
        return d;
    }

    private void attachTo(List<AttachmentDraft> attachments, String fileId) {
        if (attachments == null) {
            return;
        }
        for (AttachmentDraft a : attachments) {
            if (fileId.equals(a.getFileId())) {
                return; // already recorded
            }
        }
        AttachmentDraft a = new AttachmentDraft();
        a.setType("File");
        a.setFileId(fileId);
        attachments.add(a);
    }

    private LocalDate parseDate(String raw) {
        if (!isPresent(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Ignoring unparseable expense date: {}", raw);
            return null;
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private TripReportDraft readDraft(ConversationalAgentSession session) {
        JsonNode existing = session.getDraftJson();
        if (existing != null && existing.isObject() && existing.size() > 0) {
            try {
                return objectMapper.treeToValue(existing, TripReportDraft.class);
            } catch (JsonProcessingException e) {
                log.warn("Could not parse report draft_json for session {}; starting fresh: {}",
                        session.getId(), e.getMessage());
            }
        }
        return new TripReportDraft();
    }

    private void writeDraft(ConversationalAgentSession session, TripReportDraft draft) {
        session.setDraftJson(objectMapper.valueToTree(draft));
    }
}
