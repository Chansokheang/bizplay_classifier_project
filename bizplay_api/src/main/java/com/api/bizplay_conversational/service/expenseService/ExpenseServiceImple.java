package com.api.bizplay_conversational.service.expenseService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.ConversationalAgentSession;
import com.api.bizplay_conversational.model.entity.CostExpense;
import com.api.bizplay_conversational.model.entity.Department;
import com.api.bizplay_conversational.model.entity.TransportationExpense;
import com.api.bizplay_conversational.model.entity.TripReport;
import com.api.bizplay_conversational.model.entity.TripReportDetail;
import com.api.bizplay_conversational.model.request.ExpenseDetailRequest;
import com.api.bizplay_conversational.model.request.ExpenseSectionRequest;
import com.api.bizplay_conversational.model.request.PlanAttachmentRequest;
import com.api.bizplay_conversational.model.request.ReportCreateRequest;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.PlanTravelerResponse;
import com.api.bizplay_conversational.model.response.ReportBatchDeleteResponse;
import com.api.bizplay_conversational.model.response.ReportDetailResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String DEFAULT_APPROVAL_STATUS = "Request for approval";
    /** The approved-plan state required before a report may be created. */
    private static final String APPROVED_STATUS = "Approval complete";
    /** Allowed values for approval_status (matches the DB CHECK). */
    private static final java.util.Set<String> APPROVAL_STATUSES = java.util.Set.of(
            "Request for approval", "Business trip cancellation", "Approval complete");

    private final ExpenseRepo expenseRepo;
    private final DepartmentRepo departmentRepo;
    private final ConversationalAgentSessionRepo sessionRepo;
    private final PlanRepo planRepo;

    // --- reads -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ReportResponse getById(String id) {
        TripReport header = expenseRepo.findTripReportById(parseId(id).toString());
        if (header == null) {
            throw new CustomNotFoundException("Report not found: " + id);
        }
        return toResponse(header);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getByCorpNo(String corpNo) {
        List<ReportResponse> result = new ArrayList<>();
        for (TripReport header : expenseRepo.findTripReportsByCorpNo(corpNo)) {
            result.add(toResponse(header));
        }
        return result;
    }

    // --- create ------------------------------------------------------------------

    @Override
    @Transactional
    public ReportResponse create(ReportCreateRequest request) {
        UUID agentSessionId = parseUuidOrNull(request.getAgentSessionId(), "AgentSessionId");
        ConversationalAgentSession session = agentSessionId == null
                ? null
                : sessionRepo.findById(agentSessionId).orElse(null);

        UUID tripPlanId = resolveTripPlanId(request, session);
        assertPlanApproved(tripPlanId);

        // Header: one department for the whole report (from the line, else the plan's travelers).
        String departmentName = firstBudgetDepartment(request);
        if (departmentName == null) {
            departmentName = resolvePlanDepartment(tripPlanId);
        }
        UUID departmentId = findOrCreateDepartment(request.getCorpNo(), departmentName).getId();

        TripReport header = new TripReport();
        header.setId(UUID.randomUUID());
        header.setAgentSessionId(agentSessionId);
        header.setDepartmentId(departmentId);
        header.setTripPlanId(tripPlanId);
        header.setApprovalNumber(firstApprovalNumber(request));
        header.setApprovalStatus(DEFAULT_APPROVAL_STATUS);
        expenseRepo.insertTripReport(header);

        Map<String, UUID> attachmentByKey = new LinkedHashMap<>();
        int lines = 0;
        lines += addSection(request.getCostInformation(), SECTION_COST, header.getId(), attachmentByKey);
        lines += addSection(request.getTransportationInformation(), SECTION_TRANSPORTATION, header.getId(), attachmentByKey);
        lines += addSection(request.getEtc(), SECTION_ETC, header.getId(), attachmentByKey);
        // Top-level attachments (owned by the report, not bound to a specific line).
        if (request.getAttachments() != null) {
            for (PlanAttachmentRequest a : request.getAttachments()) {
                ensureAttachment(a, header.getId(), attachmentByKey);
            }
        }

        if (lines == 0) {
            throw new IllegalArgumentException(
                    "The report has no expense lines to create (CostInformation / TransportationInformation / Etc are all empty).");
        }

        if (session != null) {
            session.setStatus(ConversationalAgentSession.AgentStatus.POSTED);
            sessionRepo.save(session);
        }
        return toResponse(expenseRepo.findTripReportById(header.getId().toString()));
    }

    // --- update (full replace) ---------------------------------------------------

    @Override
    @Transactional
    public ReportResponse update(String id, ReportCreateRequest request) {
        UUID reportId = parseId(id);
        TripReport header = expenseRepo.findTripReportById(reportId.toString());
        if (header == null) {
            throw new CustomNotFoundException("Report not found: " + id);
        }

        // Remove the existing content: details + their expenses + attachments (header stays).
        for (TripReportDetail d : expenseRepo.findDetailsByReportId(reportId.toString())) {
            expenseRepo.deleteTripReportDetailById(d.getId());
            deleteDetailExpense(d);
        }
        expenseRepo.deleteReportAttachmentsByReportId(reportId);

        // Re-create content under the same header id.
        Map<String, UUID> attachmentByKey = new LinkedHashMap<>();
        int lines = 0;
        lines += addSection(request.getCostInformation(), SECTION_COST, reportId, attachmentByKey);
        lines += addSection(request.getTransportationInformation(), SECTION_TRANSPORTATION, reportId, attachmentByKey);
        lines += addSection(request.getEtc(), SECTION_ETC, reportId, attachmentByKey);
        if (request.getAttachments() != null) {
            for (PlanAttachmentRequest a : request.getAttachments()) {
                ensureAttachment(a, reportId, attachmentByKey);
            }
        }
        if (lines == 0) {
            throw new IllegalArgumentException("The report has no expense lines to replace it with.");
        }

        // Refresh header's department + approval_number; keep its approval_status.
        String departmentName = firstBudgetDepartment(request);
        if (departmentName == null) {
            departmentName = resolvePlanDepartment(header.getTripPlanId());
        }
        header.setDepartmentId(findOrCreateDepartment(request.getCorpNo() != null
                ? request.getCorpNo() : corpNoOfPlan(header.getTripPlanId()), departmentName).getId());
        header.setApprovalNumber(firstApprovalNumber(request));
        expenseRepo.updateTripReportHeader(header);

        return toResponse(expenseRepo.findTripReportById(reportId.toString()));
    }

    // --- approval status ---------------------------------------------------------

    @Override
    @Transactional
    public ReportResponse updateApprovalStatus(String id, String approvalStatus) {
        UUID reportId = parseId(id);
        String status = approvalStatus == null ? null : approvalStatus.trim();
        if (status == null || status.isBlank() || !APPROVAL_STATUSES.contains(status)) {
            throw new IllegalArgumentException("approvalStatus must be one of " + APPROVAL_STATUSES + " but was: " + approvalStatus);
        }
        if (expenseRepo.updateApprovalStatus(reportId, status) == 0) {
            throw new CustomNotFoundException("Report not found: " + id);
        }
        return toResponse(expenseRepo.findTripReportById(reportId.toString()));
    }

    // --- delete ------------------------------------------------------------------

    @Override
    @Transactional
    public void deleteById(String id) {
        UUID reportId = parseId(id);
        TripReport header = expenseRepo.findTripReportById(reportId.toString());
        if (header == null) {
            throw new CustomNotFoundException("Report not found: " + id);
        }
        deleteReport(header);
    }

    @Override
    @Transactional
    public ReportBatchDeleteResponse deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty.");
        }
        LinkedHashMap<String, UUID> parsed = new LinkedHashMap<>();
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
            TripReport header = expenseRepo.findTripReportById(rid.toString());
            if (header == null) {
                notFoundIds.add(rid.toString());
                continue;
            }
            deleteReport(header);
            deletedIds.add(rid.toString());
        }
        return ReportBatchDeleteResponse.builder()
                .requested(parsed.size())
                .deleted(deletedIds.size())
                .deletedIds(deletedIds)
                .notFoundIds(notFoundIds)
                .build();
    }

    /** Delete a report's expense rows, then the header (details + attachments cascade via FK). */
    private void deleteReport(TripReport header) {
        List<TripReportDetail> details = expenseRepo.findDetailsByReportId(header.getId().toString());
        expenseRepo.deleteTripReportById(header.getId());
        for (TripReportDetail d : details) {
            deleteDetailExpense(d);
        }
    }

    private void deleteDetailExpense(TripReportDetail d) {
        if (d.getCostExpenseId() != null) {
            expenseRepo.deleteCostExpenseById(d.getCostExpenseId());
        }
        if (d.getTransportationExpenseId() != null) {
            expenseRepo.deleteTransportationExpenseById(d.getTransportationExpenseId());
        }
    }

    // --- create helpers ----------------------------------------------------------

    /** Insert every detail of one section as expense + detail rows. Returns the count. */
    private int addSection(ExpenseSectionRequest section, String sectionCode, UUID reportId,
                           Map<String, UUID> attachmentByKey) {
        if (section == null || section.getDetails() == null || section.getDetails().isEmpty()) {
            return 0;
        }
        // The line's source receipt: the first attachment of its section (best-effort).
        UUID sectionAttachmentId = firstSectionAttachment(section, reportId, attachmentByKey);

        int count = 0;
        for (ExpenseDetailRequest detail : section.getDetails()) {
            if (detail == null) {
                continue;
            }
            TripReportDetail line = new TripReportDetail();
            line.setId(UUID.randomUUID());
            line.setTripReportId(reportId);
            line.setSectionCode(sectionCode);
            line.setConversationalAttachmentId(sectionAttachmentId);
            if (SECTION_TRANSPORTATION.equals(sectionCode)) {
                line.setTransportationExpenseId(insertTransportation(detail));
            } else {
                line.setCostExpenseId(insertCost(detail, sectionCode));
            }
            expenseRepo.insertTripReportDetail(line);
            count++;
        }
        return count;
    }

    private UUID firstSectionAttachment(ExpenseSectionRequest section, UUID reportId, Map<String, UUID> attachmentByKey) {
        if (section.getAttachments() == null) {
            return null;
        }
        UUID first = null;
        for (PlanAttachmentRequest a : section.getAttachments()) {
            UUID id = ensureAttachment(a, reportId, attachmentByKey);
            if (first == null) {
                first = id;
            }
        }
        return first;
    }

    /** Create (once per file) a report attachment row and return its id. Null when nothing to attach. */
    private UUID ensureAttachment(PlanAttachmentRequest a, UUID reportId, Map<String, UUID> attachmentByKey) {
        if (a == null) {
            return null;
        }
        boolean isUrl = a.getType() != null && a.getType().equalsIgnoreCase("url");
        String fileId = isUrl ? URL_ATTACHMENT_PLACEHOLDER : blankToNull(a.getFileId());
        String url = blankToNull(a.getUrl());
        if (fileId == null && url == null) {
            return null;
        }
        String key = (fileId == null ? "" : fileId) + "|" + (url == null ? "" : url);
        UUID existing = attachmentByKey.get(key);
        if (existing != null) {
            return existing;
        }
        UUID id = UUID.randomUUID();
        expenseRepo.insertReportAttachment(id, reportId, fileId == null ? URL_ATTACHMENT_PLACEHOLDER : fileId, url);
        attachmentByKey.put(key, id);
        return id;
    }

    private UUID insertCost(ExpenseDetailRequest d, String expenseType) {
        CostExpense e = new CostExpense();
        e.setId(UUID.randomUUID());
        e.setExpenseType(expenseType); // COST or ETC
        e.setTaxCode(orEmpty(d.getTaxCode()));
        e.setCategory(orDefault(d.getType(), "Expense"));
        e.setUsePurpose(orEmpty(d.getUsePurpose()));
        e.setAccount(orEmpty(d.getAccount()));
        // Leave dates null when the receipt carried none — do NOT fabricate today's date, which would
        // make the line look out-of-period in the R10 date-alignment audit (that rule skips null dates).
        LocalDate evidence = firstNonNull(d.getProofDate(), d.getUsageDate(), d.getStartDate());
        LocalDate start = firstNonNull(d.getStartDate(), d.getProofDate(), d.getUsageDate());
        LocalDate end = firstNonNull(d.getEndDate(), start);
        if (start != null && end != null && end.isBefore(start)) {
            end = start;
        }
        e.setStartDate(start);
        e.setEndDate(end);
        e.setEvidenceDate(evidence);
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
        // Null when the receipt carried no date — never default to today (see insertCost note).
        e.setUsageDate(firstNonNull(d.getUsageDate(), d.getProofDate(), d.getStartDate()));
        e.setEvidenceDate(firstNonNull(d.getProofDate(), d.getUsageDate()));
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

    // --- assembly ----------------------------------------------------------------

    private ReportResponse toResponse(TripReport header) {
        List<ReportDetailResponse> lines = new ArrayList<>();
        for (TripReportDetail d : expenseRepo.findDetailsByReportId(header.getId().toString())) {
            lines.add(toDetailResponse(d));
        }
        String department = header.getDepartmentId() != null
                ? expenseRepo.findDepartmentNameById(header.getDepartmentId()) : null;
        return ReportResponse.builder()
                .id(header.getId())
                .tripPlanId(header.getTripPlanId())
                .agentSessionId(header.getAgentSessionId())
                .departmentId(header.getDepartmentId())
                .department(department)
                .approvalStatus(header.getApprovalStatus())
                .approvalNumber(header.getApprovalNumber())
                .createdAt(header.getCreatedDate())
                .costItems(lines)
                .build();
    }

    private ReportDetailResponse toDetailResponse(TripReportDetail d) {
        CostExpense cost = d.getCostExpenseId() != null
                ? expenseRepo.findCostExpenseById(d.getCostExpenseId()) : null;
        TransportationExpense transport = d.getTransportationExpenseId() != null
                ? expenseRepo.findTransportationExpenseById(d.getTransportationExpenseId()) : null;
        String attachmentFileId = d.getConversationalAttachmentId() != null
                ? expenseRepo.findAttachmentFileIdById(d.getConversationalAttachmentId()) : null;
        return ReportDetailResponse.builder()
                .id(d.getId())
                .sectionCode(d.getSectionCode())
                .attachmentFileId(attachmentFileId)
                .costExpense(cost)
                .transportationExpense(transport)
                .build();
    }

    // --- shared helpers ----------------------------------------------------------

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
                    // fall through
                }
            }
        }
        throw new IllegalArgumentException(
                "TripPlanId is required (directly, or recoverable from the drafting session's draft_json).");
    }

    private void assertPlanApproved(UUID tripPlanId) {
        String approval = planRepo.findApprovalStatusById(tripPlanId);
        if (!APPROVED_STATUS.equals(approval)) {
            throw new IllegalArgumentException(
                    "Trip plan " + tripPlanId + " is not approved (approval_status='" + approval
                            + "'). The report can only be created once the plan is '" + APPROVED_STATUS + "'.");
        }
    }

    /** The first non-blank 예산부서 across all sections, or null. */
    private String firstBudgetDepartment(ReportCreateRequest request) {
        for (ExpenseSectionRequest s : List.of(
                nz(request.getCostInformation()), nz(request.getTransportationInformation()), nz(request.getEtc()))) {
            if (s.getDetails() == null) {
                continue;
            }
            for (ExpenseDetailRequest d : s.getDetails()) {
                if (d != null && blankToNull(d.getBudgetDepartment()) != null) {
                    return d.getBudgetDepartment().trim();
                }
            }
        }
        return null;
    }

    /** The first non-blank ApprovalNumber across all sections, or null. */
    private String firstApprovalNumber(ReportCreateRequest request) {
        for (ExpenseSectionRequest s : List.of(
                nz(request.getCostInformation()), nz(request.getTransportationInformation()), nz(request.getEtc()))) {
            if (s.getDetails() == null) {
                continue;
            }
            for (ExpenseDetailRequest d : s.getDetails()) {
                if (d != null && blankToNull(d.getApprovalNumber()) != null) {
                    return d.getApprovalNumber().trim();
                }
            }
        }
        return null;
    }

    private ExpenseSectionRequest nz(ExpenseSectionRequest s) {
        return s != null ? s : new ExpenseSectionRequest();
    }

    private String corpNoOfPlan(UUID tripPlanId) {
        PlanResponse plan = planRepo.findPlanResponseById(tripPlanId.toString());
        return plan != null ? plan.getCorpNo() : null;
    }

    private String resolvePlanDepartment(UUID tripPlanId) {
        if (tripPlanId == null) {
            return null;
        }
        List<PlanTravelerResponse> travelers = planRepo.findTravelerResponsesByPlanId(tripPlanId.toString());
        if (travelers == null) {
            return null;
        }
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
        String finalCorp = corpNo != null ? corpNo : "";
        return departmentRepo.findByCorpNoAndName(finalCorp, finalName)
                .orElseGet(() -> {
                    Department department = new Department();
                    department.setCorpNo(finalCorp);
                    department.setName(finalName);
                    return departmentRepo.save(department);
                });
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id == null ? null : id.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomNotFoundException("Invalid report id: " + id);
        }
    }

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
