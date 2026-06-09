package com.api.bizplay_conversational.service.expenseService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.CostExpense;
import com.api.bizplay_conversational.model.entity.Department;
import com.api.bizplay_conversational.model.entity.TransportationExpense;
import com.api.bizplay_conversational.model.entity.TripReport;
import com.api.bizplay_conversational.model.request.ExpenseDetailRequest;
import com.api.bizplay_conversational.model.request.ExpenseSectionRequest;
import com.api.bizplay_conversational.model.request.PlanAttachmentRequest;
import com.api.bizplay_conversational.model.request.ReportCreateRequest;
import com.api.bizplay_conversational.model.request.ReportLineUpdateRequest;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.PlanTravelerResponse;
import com.api.bizplay_conversational.model.response.ReportBatchDeleteResponse;
import com.api.bizplay_conversational.model.response.ReportLineResponse;
import com.api.bizplay_conversational.model.response.ReportResponse;
import com.api.bizplay_conversational.repository.ConversationalAgentSessionRepo;
import com.api.bizplay_conversational.repository.DepartmentRepo;
import com.api.bizplay_conversational.repository.ExpenseRepo;
import com.api.bizplay_conversational.repository.PlanRepo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseServiceImple implements ExpenseService {

    private static final String DEFAULT_DEPARTMENT_NAME = "Unassigned";
    private static final String SECTION_COST = "COST";
    private static final String SECTION_TRANSPORTATION = "TRANSPORTATION";
    private static final String SECTION_ETC = "ETC";
    private static final String URL_ATTACHMENT_PLACEHOLDER = "URL_ATTACHMENT";
    /** The approved-plan state required before a report may be created. */
    private static final String APPROVED_STATUS = "Approval complete";
    /** Allowed values for conversational_trip_report.approval_status (matches the DB CHECK). */
    private static final java.util.Set<String> APPROVAL_STATUSES = java.util.Set.of(
            "Request for approval", "Business trip cancellation", "Approval complete");
    /** Allowed section codes (matches the DB CHECK). */
    private static final java.util.Set<String> VALID_SECTIONS = java.util.Set.of(
            SECTION_COST, SECTION_TRANSPORTATION, SECTION_ETC);

    private final ExpenseRepo expenseRepo;
    private final DepartmentRepo departmentRepo;
    private final ConversationalAgentSessionRepo sessionRepo;
    private final PlanRepo planRepo;

    @Override
    @Transactional(readOnly = true)
    public ReportLineResponse getById(String id) {
        UUID reportId;
        try {
            reportId = UUID.fromString(id == null ? null : id.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomNotFoundException("Invalid report id: " + id);
        }
        TripReport tr = expenseRepo.findTripReportById(reportId.toString());
        if (tr == null) {
            throw new CustomNotFoundException("Report line not found: " + id);
        }
        return toResponse(tr);
    }

    @Override
    @Transactional
    public ReportLineResponse update(String id, ReportLineUpdateRequest request) {
        UUID reportId = parseReportId(id);
        TripReport tr = expenseRepo.findTripReportById(reportId.toString());
        if (tr == null) {
            throw new CustomNotFoundException("Report line not found: " + id);
        }
        ReportLineUpdateRequest req = request == null ? new ReportLineUpdateRequest() : request;
        ExpenseDetailRequest detail = req.getDetail() != null ? req.getDetail() : new ExpenseDetailRequest();

        // Section: keep the existing one unless a valid new one is given.
        String section = blankToNull(req.getSectionCode());
        section = section != null ? section.toUpperCase(java.util.Locale.ROOT) : tr.getSectionCode();
        if (!VALID_SECTIONS.contains(section)) {
            throw new IllegalArgumentException("sectionCode must be one of " + VALID_SECTIONS + " but was: " + req.getSectionCode());
        }

        // Approval status: keep existing unless a valid new one is given.
        String approvalStatus = blankToNull(req.getApprovalStatus());
        if (approvalStatus != null && !APPROVAL_STATUSES.contains(approvalStatus)) {
            throw new IllegalArgumentException("approvalStatus must be one of " + APPROVAL_STATUSES + " but was: " + req.getApprovalStatus());
        }
        if (approvalStatus == null) {
            approvalStatus = tr.getApprovalStatus();
        }

        // Department: a provided budget department wins (resolved under the plan's corp); else keep existing.
        UUID departmentId = tr.getDepartmentId();
        String budgetDept = blankToNull(detail.getBudgetDepartment());
        if (budgetDept != null) {
            PlanResponse plan = planRepo.findPlanResponseById(tr.getTripPlanId().toString());
            String corpNo = plan != null ? plan.getCorpNo() : null;
            if (corpNo != null) {
                departmentId = findOrCreateDepartment(corpNo, budgetDept).getId();
            }
        }

        // Replace the linked expense: insert the new row FIRST, repoint the report, THEN drop the old
        // row(s) — so the section/FK CHECK constraint is always satisfied during the transaction.
        UUID oldCostId = tr.getCostExpenseId();
        UUID oldTransportId = tr.getTransportationExpenseId();

        UUID costExpenseId = null;
        UUID transportationExpenseId = null;
        if (SECTION_TRANSPORTATION.equals(section)) {
            transportationExpenseId = insertTransportation(detail);
        } else {
            costExpenseId = insertCost(detail, section); // COST or ETC
        }

        TripReport updated = new TripReport();
        updated.setId(reportId);
        updated.setSectionCode(section);
        updated.setCostExpenseId(costExpenseId);
        updated.setTransportationExpenseId(transportationExpenseId);
        updated.setDepartmentId(departmentId);
        updated.setApprovalNumber(blankToNull(detail.getApprovalNumber()));
        updated.setApprovalStatus(approvalStatus);
        expenseRepo.updateTripReportLine(updated);

        if (oldCostId != null) {
            expenseRepo.deleteCostExpenseById(oldCostId);
        }
        if (oldTransportId != null) {
            expenseRepo.deleteTransportationExpenseById(oldTransportId);
        }

        return toResponse(expenseRepo.findTripReportById(reportId.toString()));
    }

    @Override
    @Transactional
    public ReportLineResponse updateApprovalStatus(String id, String approvalStatus) {
        UUID reportId = parseReportId(id);
        String status = approvalStatus == null ? null : approvalStatus.trim();
        if (status == null || status.isBlank() || !APPROVAL_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "approvalStatus must be one of " + APPROVAL_STATUSES + " but was: " + approvalStatus);
        }
        int updated = expenseRepo.updateApprovalStatus(reportId, status);
        if (updated == 0) {
            throw new CustomNotFoundException("Report line not found: " + id);
        }
        return toResponse(expenseRepo.findTripReportById(reportId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportLineResponse> getByCorpNo(String corpNo) {
        List<ReportLineResponse> result = new ArrayList<>();
        for (TripReport tr : expenseRepo.findTripReportsByCorpNo(corpNo)) {
            result.add(toResponse(tr));
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        UUID reportId = parseReportId(id);
        TripReport tr = expenseRepo.findTripReportById(reportId.toString());
        if (tr == null) {
            throw new CustomNotFoundException("Report line not found: " + id);
        }
        deleteLine(tr);
    }

    @Override
    @Transactional
    public ReportBatchDeleteResponse deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty.");
        }
        java.util.LinkedHashMap<String, UUID> parsed = new java.util.LinkedHashMap<>();
        for (String raw : ids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            UUID rid;
            try {
                rid = UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid report id: " + raw);
            }
            parsed.putIfAbsent(rid.toString(), rid);
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty.");
        }

        List<String> deletedIds = new ArrayList<>();
        List<String> notFoundIds = new ArrayList<>();
        for (UUID rid : parsed.values()) {
            TripReport tr = expenseRepo.findTripReportById(rid.toString());
            if (tr == null) {
                notFoundIds.add(rid.toString());
                continue;
            }
            deleteLine(tr);
            deletedIds.add(rid.toString());
        }
        return ReportBatchDeleteResponse.builder()
                .requested(parsed.size())
                .deleted(deletedIds.size())
                .deletedIds(deletedIds)
                .notFoundIds(notFoundIds)
                .build();
    }

    /** Delete a report line, then its now-orphaned linked expense row (attachments cascade via FK). */
    private void deleteLine(TripReport tr) {
        expenseRepo.deleteTripReportById(tr.getId());
        if (tr.getCostExpenseId() != null) {
            expenseRepo.deleteCostExpenseById(tr.getCostExpenseId());
        }
        if (tr.getTransportationExpenseId() != null) {
            expenseRepo.deleteTransportationExpenseById(tr.getTransportationExpenseId());
        }
    }

    private ReportLineResponse toResponse(TripReport tr) {
        CostExpense cost = tr.getCostExpenseId() != null
                ? expenseRepo.findCostExpenseById(tr.getCostExpenseId()) : null;
        TransportationExpense transport = tr.getTransportationExpenseId() != null
                ? expenseRepo.findTransportationExpenseById(tr.getTransportationExpenseId()) : null;
        String departmentName = tr.getDepartmentId() != null
                ? expenseRepo.findDepartmentNameById(tr.getDepartmentId()) : null;

        return ReportLineResponse.builder()
                .id(tr.getId())
                .tripPlanId(tr.getTripPlanId())
                .agentSessionId(tr.getAgentSessionId())
                .sectionCode(tr.getSectionCode())
                .approvalStatus(tr.getApprovalStatus())
                .departmentId(tr.getDepartmentId())
                .department(departmentName)
                .approvalNumber(tr.getApprovalNumber())
                .costExpense(cost)
                .transportationExpense(transport)
                .build();
    }

    private UUID parseReportId(String id) {
        try {
            return UUID.fromString(id == null ? null : id.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomNotFoundException("Invalid report id: " + id);
        }
    }

    @Override
    @Transactional
    public ReportResponse create(ReportCreateRequest request) {
        UUID agentSessionId = parseUuidOrNull(request.getAgentSessionId(), "AgentSessionId");
        ConversationalAgentSession session = agentSessionId == null
                ? null
                : sessionRepo.findById(agentSessionId).orElse(null);

        UUID tripPlanId = resolveTripPlanId(request, session);

        // The report can only be created once the trip plan has been approved.
        String approval = planRepo.findApprovalStatusById(tripPlanId);
        if (!APPROVED_STATUS.equals(approval)) {
            throw new IllegalArgumentException(
                    "Trip plan " + tripPlanId + " is not approved (approval_status='" + approval
                            + "'). The report can only be created once the plan is '" + APPROVED_STATUS + "'.");
        }

        // A receipt rarely names a budget department; fall back to the trip plan's traveler department.
        String planDepartment = resolvePlanDepartment(tripPlanId);

        List<UUID> reportLineIds = new ArrayList<>();
        int[] counts = new int[3]; // cost, transportation, etc

        counts[0] = addSection(request.getCostInformation(), SECTION_COST, request.getCorpNo(),
                tripPlanId, agentSessionId, planDepartment, reportLineIds);
        counts[1] = addSection(request.getTransportationInformation(), SECTION_TRANSPORTATION, request.getCorpNo(),
                tripPlanId, agentSessionId, planDepartment, reportLineIds);
        counts[2] = addSection(request.getEtc(), SECTION_ETC, request.getCorpNo(),
                tripPlanId, agentSessionId, planDepartment, reportLineIds);

        if (reportLineIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "The report has no expense lines to create (CostInformation / TransportationInformation / Etc are all empty).");
        }

        // Attach receipts. conversational_attachment.report_id points at a single trip_report row, so
        // we own all report attachments under the first created line.
        int attachmentCount = persistAttachments(request, reportLineIds.get(0));

        // Mark the drafting session as posted.
        if (session != null) {
            session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
            sessionRepo.save(session);
        }

        return ReportResponse.builder()
                .tripPlanId(tripPlanId)
                .agentSessionId(agentSessionId)
                .corpNo(request.getCorpNo())
                .costCount(counts[0])
                .transportationCount(counts[1])
                .etcCount(counts[2])
                .totalLines(reportLineIds.size())
                .attachmentCount(attachmentCount)
                .reportLineIds(reportLineIds)
                .build();
    }

    /** Insert every detail of one section as expense + trip_report rows. Returns the count. */
    private int addSection(ExpenseSectionRequest section, String sectionCode, String corpNo,
                           UUID tripPlanId, UUID agentSessionId, String planDepartment, List<UUID> reportLineIds) {
        if (section == null || section.getDetails() == null || section.getDetails().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ExpenseDetailRequest detail : section.getDetails()) {
            if (detail == null) {
                continue;
            }
            // Use the line's own budget department; if blank, fall back to the trip plan's department.
            String departmentName = blankToNull(detail.getBudgetDepartment());
            if (departmentName == null) {
                departmentName = planDepartment;
            }
            UUID departmentId = findOrCreateDepartment(corpNo, departmentName).getId();
            UUID expenseId;
            TripReport report = new TripReport();
            report.setId(UUID.randomUUID());
            report.setAgentSessionId(agentSessionId);
            report.setDepartmentId(departmentId);
            report.setTripPlanId(tripPlanId);
            report.setSectionCode(sectionCode);
            report.setApprovalNumber(blankToNull(detail.getApprovalNumber()));

            if (SECTION_TRANSPORTATION.equals(sectionCode)) {
                expenseId = insertTransportation(detail);
                report.setTransportationExpenseId(expenseId);
            } else {
                expenseId = insertCost(detail, sectionCode);
                report.setCostExpenseId(expenseId);
            }
            expenseRepo.insertTripReport(report);
            reportLineIds.add(report.getId());
            count++;
        }
        return count;
    }

    private UUID insertCost(ExpenseDetailRequest d, String expenseType) {
        CostExpense e = new CostExpense();
        e.setId(UUID.randomUUID());
        e.setExpenseType(expenseType); // COST or ETC
        e.setTaxCode(orEmpty(d.getTaxCode()));
        e.setCategory(orDefault(d.getType(), "Expense"));
        e.setUsePurpose(orEmpty(d.getUsePurpose()));
        e.setAccount(orEmpty(d.getAccount()));
        LocalDate proof = firstNonNull(d.getProofDate(), d.getUsageDate(), d.getStartDate(), LocalDate.now());
        LocalDate start = firstNonNull(d.getStartDate(), d.getProofDate(), d.getUsageDate(), LocalDate.now());
        LocalDate end = firstNonNull(d.getEndDate(), start);
        if (end.isBefore(start)) {
            end = start;
        }
        e.setStartDate(start);
        e.setEndDate(end);
        e.setEvidenceDate(proof);
        e.setDescription(blankToNull(d.getDescription()));
        e.setApplicationAmount(orZero(d.getAmountUsed()));
        e.setPolicyAmount(firstNonNull(d.getRegulatedAmount(), orZero(d.getAmountUsed())));
        e.setExcessReason(blankToNull(d.getApplicationAmountReasonForExcess()));
        e.setNote(blankToNull(d.getNote()));
        expenseRepo.insertCostExpense(e);
        return e.getId();
    }

    private UUID insertTransportation(ExpenseDetailRequest d) {
        TransportationExpense e = new TransportationExpense();
        e.setId(UUID.randomUUID());
        e.setTaxCode(orEmpty(d.getTaxCode()));
        e.setCategory(orDefault(d.getType(), "Transportation"));
        e.setUsePurpose(orEmpty(d.getUsePurpose()));
        e.setAccount(orEmpty(d.getAccount()));
        e.setTransportationMethod(orEmpty(d.getTransportationMethod()));
        e.setGrade(blankToNull(d.getGrade()));
        e.setOriginLocation(orEmpty(d.getOrigin()));
        e.setDestinationLocation(orEmpty(d.getDestination()));
        e.setUsageDate(firstNonNull(d.getUsageDate(), d.getProofDate(), d.getStartDate(), LocalDate.now()));
        e.setEvidenceDate(firstNonNull(d.getProofDate(), d.getUsageDate())); // nullable; no today fallback
        e.setVendor(orEmpty(d.getVendor()));
        e.setSupplyPrice(firstNonNull(d.getSupplyPrice(), orZero(d.getAmountUsed())));
        e.setTax(orZero(d.getTax()));
        e.setApplicationAmount(firstNonNull(d.getAmountUsed(), orZero(d.getSupplyPrice())));
        e.setPolicyAmount(firstNonNull(d.getRegulatedAmount(), orZero(d.getAmountUsed())));
        e.setExcessReason(blankToNull(d.getApplicationAmountReasonForExcess()));
        e.setDescription(blankToNull(d.getDescription()));
        e.setNote(blankToNull(d.getNote()));
        expenseRepo.insertTransportationExpense(e);
        return e.getId();
    }

    private int persistAttachments(ReportCreateRequest request, UUID ownerReportId) {
        // Collect file ids from the top-level Attachemnt and every section's Attachemnt, de-duplicated.
        List<PlanAttachmentRequest> all = new ArrayList<>();
        if (request.getAttachments() != null) {
            all.addAll(request.getAttachments());
        }
        addSectionAttachments(all, request.getCostInformation());
        addSectionAttachments(all, request.getTransportationInformation());
        addSectionAttachments(all, request.getEtc());

        Set<String> seen = new LinkedHashSet<>();
        int count = 0;
        for (PlanAttachmentRequest a : all) {
            if (a == null) {
                continue;
            }
            boolean isUrl = a.getType() != null && a.getType().equalsIgnoreCase("url");
            String fileId = isUrl ? URL_ATTACHMENT_PLACEHOLDER : blankToNull(a.getFileId());
            String url = blankToNull(a.getUrl());
            if (fileId == null && url == null) {
                continue;
            }
            String key = (fileId == null ? "" : fileId) + "|" + (url == null ? "" : url);
            if (!seen.add(key)) {
                continue;
            }
            expenseRepo.insertReportAttachment(UUID.randomUUID(), ownerReportId,
                    fileId == null ? URL_ATTACHMENT_PLACEHOLDER : fileId, url);
            count++;
        }
        return count;
    }

    private void addSectionAttachments(List<PlanAttachmentRequest> sink, ExpenseSectionRequest section) {
        if (section != null && section.getAttachments() != null) {
            sink.addAll(section.getAttachments());
        }
    }

    private UUID resolveTripPlanId(ReportCreateRequest request, ConversationalAgentSession session) {
        UUID fromRequest = parseUuidOrNull(request.getTripPlanId(), "TripPlanId");
        if (fromRequest != null) {
            return fromRequest;
        }
        if (session != null && session.getDraftJson() != null) {
            JsonNode node = session.getDraftJson().path("TripPlanId");
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                try {
                    return UUID.fromString(node.asText().trim());
                } catch (IllegalArgumentException ignored) {
                    // fall through to the error below
                }
            }
        }
        throw new IllegalArgumentException(
                "TripPlanId is required (directly, or recoverable from the drafting session's draft_json).");
    }

    /**
     * The department to use when an expense line has no budget department: the single distinct
     * department across the trip plan's travelers, or the first traveler's department if they differ.
     * Null when the plan has no travelers/department (caller falls back to the default).
     */
    private String resolvePlanDepartment(UUID tripPlanId) {
        if (tripPlanId == null) {
            return null;
        }
        List<PlanTravelerResponse> travelers = planRepo.findTravelerResponsesByPlanId(tripPlanId.toString());
        if (travelers == null || travelers.isEmpty()) {
            return null;
        }
        // Use the first non-blank traveler department (better than "Unassigned" when receipts omit it).
        for (PlanTravelerResponse t : travelers) {
            String d = t == null ? null : t.getDepartment();
            if (d != null && !d.isBlank()) {
                return d.trim();
            }
        }
        return null;
    }

    private Department findOrCreateDepartment(String corpNo, String departmentName) {
        String name = blankToNull(departmentName);
        String finalName = name != null ? name : DEFAULT_DEPARTMENT_NAME;
        return departmentRepo.findByCorpNoAndName(corpNo, finalName)
                .orElseGet(() -> {
                    Department department = new Department();
                    department.setCorpNo(corpNo);
                    department.setName(finalName);
                    return departmentRepo.save(department);
                });
    }

    // --- small helpers -----------------------------------------------------------

    private UUID parseUuidOrNull(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " must be a valid UUID: " + raw);
        }
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private String orEmpty(String v) {
        return (v == null || v.isBlank()) ? "" : v.trim();
    }

    private String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
