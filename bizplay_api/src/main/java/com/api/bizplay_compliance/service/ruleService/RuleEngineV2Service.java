package com.api.bizplay_compliance.service.ruleService;

import com.api.bizplay_compliance.exception.CustomNotFoundException;
import com.api.bizplay_compliance.model.entity.ComplianceAudit;
import com.api.bizplay_compliance.model.response.ComplianceAuditBatchDeleteResponse;
import com.api.bizplay_compliance.model.response.RequisitionAuditResponse;
import com.api.bizplay_compliance.model.response.RequisitionAuditResponse.DimensionFinding;
import com.api.bizplay_compliance.repository.ComplianceAuditRepo;
import com.api.bizplay_compliance.service.geoService.GeoNormalizationService;
import com.api.bizplay_compliance.service.geoService.GeoPoint;
import com.api.bizplay_conversational.model.entity.CostExpense;
import com.api.bizplay_conversational.model.entity.TransportationExpense;
import com.api.bizplay_conversational.model.entity.TripReport;
import com.api.bizplay_conversational.model.entity.TripReportDetail;
import com.api.bizplay_conversational.model.response.PlanAttachmentResponse;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.PlanTravelerResponse;
import com.api.bizplay_conversational.repository.ExpenseRepo;
import com.api.bizplay_conversational.repository.PlanRepo;
import com.api.bizplay_conversational.service.fileExtractionService.FileExtractionService;
import com.api.bizplay_conversational.service.fileExtractionService.UploadedFile;
import com.api.bizplay_conversational.service.receiptExtractionAgentService.ReceiptExtractionAgentService;
import com.api.bizplay_conversational.model.response.ReceiptExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * v2 rule engine hosting a single rule, R10 (requisition mismatch). It audits an approved trip plan
 * (the "requisition") against its expense report + uploaded receipts and reports alignment across
 * five independent dimensions. Self-contained: it does NOT touch the v1 {@code RuleEngineService} or
 * the {@code /api/v1/rule-engine/*} endpoints.
 */
@Slf4j
@Service
public class RuleEngineV2Service {

    private static final String APPROVED_STATUS = "Approval complete";
    private static final String PASS = "PASS";
    private static final String FAIL = "FAIL";
    private static final String SKIPPED = "SKIPPED";

    private final PlanRepo planRepo;
    private final ExpenseRepo expenseRepo;
    private final FileExtractionService fileExtractionService;
    private final ReceiptExtractionAgentService receiptExtractionAgentService;
    private final ComplianceAuditRepo complianceAuditRepo;
    private final GeoNormalizationService geoNormalizationService;
    private final ObjectMapper objectMapper;

    public RuleEngineV2Service(PlanRepo planRepo,
                               ExpenseRepo expenseRepo,
                               FileExtractionService fileExtractionService,
                               ReceiptExtractionAgentService receiptExtractionAgentService,
                               ComplianceAuditRepo complianceAuditRepo,
                               GeoNormalizationService geoNormalizationService,
                               ObjectMapper objectMapper) {
        this.planRepo = planRepo;
        this.expenseRepo = expenseRepo;
        this.fileExtractionService = fileExtractionService;
        this.receiptExtractionAgentService = receiptExtractionAgentService;
        this.complianceAuditRepo = complianceAuditRepo;
        this.geoNormalizationService = geoNormalizationService;
        this.objectMapper = objectMapper;
    }

    /** One expense line, flattened from a report detail + its cost/transportation expense. */
    private record LineView(String section, String category, LocalDate date, BigDecimal amount,
                            String origin, String destination, String vendor, UUID attachmentId) {
    }

    /**
     * Run the R10 requisition-mismatch audit for one trip plan and persist one aggregate result row.
     */
    public RequisitionAuditResponse auditRequisition(String corpNo, String tripPlanId) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        if (tripPlanId == null || tripPlanId.isBlank()) {
            throw new IllegalArgumentException("tripPlanId is required.");
        }
        UUID planUuid;
        try {
            planUuid = UUID.fromString(tripPlanId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid tripPlanId: " + tripPlanId);
        }

        PlanResponse plan = planRepo.findPlanResponseById(planUuid.toString());
        if (plan == null) {
            throw new IllegalArgumentException("Trip plan not found: " + tripPlanId);
        }
        if (!corpNo.equals(plan.getCorpNo())) {
            throw new IllegalArgumentException(
                    "Trip plan " + tripPlanId + " does not belong to corpNo=" + corpNo + ".");
        }
        List<PlanTravelerResponse> travelers = planRepo.findTravelerResponsesByPlanId(planUuid.toString());

        // Flatten the report (header -> details -> cost/transportation expense) into expense lines.
        List<LineView> lines = loadExpenseLines(planUuid.toString());

        // Re-extract each backed receipt ONCE (Gemma); shared by the location + receipt-backing checks.
        Map<UUID, ReceiptExtractionResult> receipts = extractReceipts(corpNo, lines);

        List<DimensionFinding> findings = new ArrayList<>();
        findings.add(evaluateApprovalGate(plan));
        findings.add(evaluateDateAlignment(plan, lines));
        findings.add(evaluateLocationAlignment(plan, travelers, lines, receipts));
        findings.add(evaluateAmountAlignment(plan, lines));
        findings.add(evaluateReceiptBacking(lines, receipts));

        long fails = findings.stream().filter(f -> FAIL.equals(f.status())).count();
        long evaluated = findings.stream().filter(f -> !SKIPPED.equals(f.status())).count();

        String ruleStatus = fails > 0 ? "Failed" : (evaluated > 0 ? "Pass" : "SKIPPED");
        String complianceStatus = fails > 0 ? "SUSPICION" : "NORMAL";
        String confidence = fails > 0 ? "MEDIUM" : (evaluated > 0 ? "HIGH" : "LOW");
        String summary = buildSummary(ruleStatus, fails, evaluated, lines.size());

        UUID auditId = UUID.randomUUID();
        LocalDateTime createdDate = LocalDateTime.now();
        ComplianceAudit audit = ComplianceAudit.builder()
                .id(auditId)
                .corpNo(corpNo)
                .tripPlanId(planUuid)
                .reportId(null) // aggregate mode: one row per plan
                .complianceStatus(complianceStatus)
                .confidenceLevel(confidence)
                .rulesJson(objectMapper.valueToTree(findings))
                .createdDate(createdDate)
                .build();
        complianceAuditRepo.insertAudit(audit);

        return new RequisitionAuditResponse(
                auditId, corpNo, planUuid, "R10", "Requisition Mismatch",
                ruleStatus, complianceStatus, confidence, summary, findings, createdDate);
    }

    // --- reads -------------------------------------------------------------------

    /** All persisted audits for a corp (newest first). */
    public List<ComplianceAudit> listAuditsByCorpNo(String corpNo) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        return complianceAuditRepo.findByCorpNo(corpNo);
    }

    /** A single persisted audit by id. */
    public ComplianceAudit getAuditById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required.");
        }
        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid audit id: " + id);
        }
        ComplianceAudit audit = complianceAuditRepo.findById(id.trim());
        if (audit == null) {
            throw new CustomNotFoundException("Compliance audit not found: " + id);
        }
        return audit;
    }

    // --- mutations ---------------------------------------------------------------

    private static final Set<String> ALLOWED_STATUS = Set.of("NORMAL", "SUSPICION");
    private static final Set<String> ALLOWED_CONFIDENCE = Set.of("HIGH", "MEDIUM", "LOW");

    /** Analyst override of a stored audit's verdict (partial: null fields are left unchanged). */
    public ComplianceAudit updateAudit(String id, String complianceStatus, String confidenceLevel) {
        ComplianceAudit existing = getAuditById(id); // validates id + 404
        String status = trimToNull(complianceStatus);
        String confidence = trimToNull(confidenceLevel);
        if (status == null && confidence == null) {
            throw new IllegalArgumentException(
                    "At least one of complianceStatus or confidenceLevel is required.");
        }
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUS.contains(status)) {
                throw new IllegalArgumentException("complianceStatus must be one of " + ALLOWED_STATUS + ".");
            }
        }
        if (confidence != null) {
            confidence = confidence.toUpperCase(Locale.ROOT);
            if (!ALLOWED_CONFIDENCE.contains(confidence)) {
                throw new IllegalArgumentException("confidenceLevel must be one of " + ALLOWED_CONFIDENCE + ".");
            }
        }
        complianceAuditRepo.updateAuditStatus(existing.getId().toString(), status, confidence);
        return complianceAuditRepo.findById(existing.getId().toString());
    }

    /** Delete one audit by id. */
    public void deleteAudit(String id) {
        ComplianceAudit existing = getAuditById(id); // validates id + 404
        complianceAuditRepo.deleteById(existing.getId().toString());
    }

    /** Delete several audits by id. Invalid/unknown ids are skipped; reports what was deleted. */
    public ComplianceAuditBatchDeleteResponse deleteAudits(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("At least one id is required.");
        }
        List<String> deleted = new ArrayList<>();
        for (String raw : ids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id = raw.trim();
            try {
                UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                continue; // skip malformed ids rather than failing the whole batch
            }
            if (complianceAuditRepo.deleteById(id) > 0) {
                deleted.add(id);
            }
        }
        return new ComplianceAuditBatchDeleteResponse(ids.size(), deleted.size(), deleted);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // --- data loading ------------------------------------------------------------

    private List<LineView> loadExpenseLines(String tripPlanId) {
        List<LineView> lines = new ArrayList<>();
        for (TripReport header : expenseRepo.findTripReportsByTripPlanId(tripPlanId)) {
            List<TripReportDetail> details = expenseRepo.findDetailsByReportId(header.getId().toString());
            for (TripReportDetail detail : details) {
                if (detail.getCostExpenseId() != null) {
                    CostExpense c = expenseRepo.findCostExpenseById(detail.getCostExpenseId());
                    if (c != null) {
                        LocalDate date = firstNonNull(c.getEvidenceDate(), c.getStartDate());
                        lines.add(new LineView(detail.getSectionCode(), c.getCategory(), date,
                                c.getApplicationAmount(), null, null, null,
                                detail.getConversationalAttachmentId()));
                    }
                } else if (detail.getTransportationExpenseId() != null) {
                    TransportationExpense t = expenseRepo.findTransportationExpenseById(detail.getTransportationExpenseId());
                    if (t != null) {
                        LocalDate date = firstNonNull(t.getUsageDate(), t.getEvidenceDate());
                        lines.add(new LineView(detail.getSectionCode(), t.getCategory(), date,
                                t.getApplicationAmount(), t.getOriginLocation(), t.getDestinationLocation(),
                                t.getVendor(), detail.getConversationalAttachmentId()));
                    }
                }
            }
        }
        return lines;
    }

    // --- dimensions --------------------------------------------------------------

    /** #1 Approval gate: the plan must be approved for its expenses to be legitimate. */
    private DimensionFinding evaluateApprovalGate(PlanResponse plan) {
        if (APPROVED_STATUS.equals(plan.getApprovalStatus())) {
            return new DimensionFinding("APPROVAL_GATE", PASS,
                    "Trip plan is approved (approval_status='" + APPROVED_STATUS + "').");
        }
        return new DimensionFinding("APPROVAL_GATE", FAIL,
                "Trip plan is not approved (approval_status='" + plan.getApprovalStatus()
                        + "'). Expenses should not be reported against an unapproved plan.");
    }

    /** #2 Date alignment: every expense date must fall within the approved trip period. */
    private DimensionFinding evaluateDateAlignment(PlanResponse plan, List<LineView> lines) {
        LocalDate start = plan.getBusinessStartDate();
        LocalDate end = plan.getBusinessEndDate();
        if (start == null || end == null) {
            return new DimensionFinding("DATE_ALIGNMENT", SKIPPED,
                    "Trip plan has no business period (start/end date) to compare against.");
        }
        List<String> outOfRange = new ArrayList<>();
        int dated = 0;
        for (LineView line : lines) {
            if (line.date() == null) {
                continue;
            }
            dated++;
            if (line.date().isBefore(start) || line.date().isAfter(end)) {
                outOfRange.add(describe(line) + " dated " + line.date());
            }
        }
        if (dated == 0) {
            return new DimensionFinding("DATE_ALIGNMENT", SKIPPED,
                    "No expense line carries a date to check against the trip period.");
        }
        if (!outOfRange.isEmpty()) {
            return new DimensionFinding("DATE_ALIGNMENT", FAIL,
                    "Expense(s) outside the approved trip period [" + start + " .. " + end + "]: "
                            + String.join("; ", outOfRange) + ".");
        }
        return new DimensionFinding("DATE_ALIGNMENT", PASS,
                "All " + dated + " dated expense line(s) fall within [" + start + " .. " + end + "].");
    }

    /**
     * #3 Location alignment: transportation destinations should relate to the planned destination/legs.
     * Compares geographically, not by raw text: a planned VENUE ("KSHRD center") and an expense CITY
     * ("Phnom Penh") align because the trip's real city is read FROM THE SAME BOOKING PDF. The plan's
     * own attached booking document is extracted to recover its cities/country (e.g. Phnom Penh,
     * Cambodia); the expense side uses the enriched receipt city/country. When no document carries the
     * city (e.g. a hand-typed local plan with no booking), it falls back to the LLM geo normalizer,
     * which handles Korean and non-Korean places. The cheap substring check runs first; only lines
     * that fail it are compared on city/country.
     */
    private DimensionFinding evaluateLocationAlignment(PlanResponse plan, List<PlanTravelerResponse> travelers,
                                                       List<LineView> lines,
                                                       Map<UUID, ReceiptExtractionResult> receipts) {
        List<String> known = new ArrayList<>();
        addIfPresent(known, plan.getDestination());
        for (PlanTravelerResponse t : travelers) {
            addIfPresent(known, t.getDestination());
            addIfPresent(known, t.getReturnPoint());
            addIfPresent(known, t.getOrigin());
        }
        boolean anyCandidate = lines.stream().anyMatch(l -> l.destination() != null && !l.destination().isBlank());
        if (known.isEmpty() || !anyCandidate) {
            return new DimensionFinding("LOCATION_ALIGNMENT", SKIPPED,
                    "No comparable destinations (plan destination or expense destination missing).");
        }

        // Memoize normalizer calls so the same place is resolved at most once per audit.
        Map<String, GeoPoint> geoCache = new HashMap<>();
        // The plan's cities/country. Lazily computed only when a line fails the cheap substring check
        // (so a textually-matching report costs nothing).
        List<GeoPoint> knownGeo = null;

        List<String> unrelated = new ArrayList<>();
        for (LineView line : lines) {
            String dest = line.destination();
            if (dest == null || dest.isBlank()) {
                continue;
            }
            // 1. Cheap path: raw substring relation against any planned location text.
            if (known.stream().anyMatch(k -> related(k, dest))) {
                continue;
            }
            // 2. Geographic path: city/country from the documents; LLM normalizer as a fallback.
            if (knownGeo == null) {
                knownGeo = planGeoPoints(plan.getCorpNo(), plan.getId());
                if (knownGeo.isEmpty()) {
                    // No readable booking PDF on the plan -> resolve the typed locations instead.
                    for (String k : known) {
                        GeoPoint g = geoFor(k, geoCache);
                        if (!g.isEmpty()) {
                            knownGeo.add(g);
                        }
                    }
                }
            }
            GeoPoint expenseGeo = expenseGeoFor(line, receipts, geoCache);
            if (!knownGeo.isEmpty() && knownGeo.stream().anyMatch(k -> geoRelated(k, expenseGeo))) {
                continue;
            }
            unrelated.add(dest);
        }

        if (!unrelated.isEmpty()) {
            return new DimensionFinding("LOCATION_ALIGNMENT", FAIL,
                    "Expense destination(s) unrelated to the planned destination/legs: "
                            + String.join("; ", unrelated) + ".");
        }
        return new DimensionFinding("LOCATION_ALIGNMENT", PASS,
                "All expense destination(s) relate to the planned destination/legs.");
    }

    /**
     * Extract the plan's attached booking PDF(s) and collect the cities/country they name (origin and
     * destination of each transport leg). This recovers e.g. "Phnom Penh / Cambodia" for a plan whose
     * stored destination is only the venue "KSHRD center". Empty when the plan has no readable booking.
     */
    private List<GeoPoint> planGeoPoints(String corpNo, UUID planId) {
        List<GeoPoint> points = new ArrayList<>();
        if (planId == null) {
            return points;
        }
        for (PlanAttachmentResponse att : planRepo.findAttachmentResponsesByPlanId(planId.toString())) {
            ReceiptExtractionResult r = extractByFileId(corpNo, att.getFileId());
            if (r == null) {
                continue;
            }
            addGeo(points, r.getDestinationCity(), r.getDestinationCountry());
            addGeo(points, r.getOriginCity(), r.getOriginCountry());
        }
        return points;
    }

    private static void addGeo(List<GeoPoint> points, String city, String country) {
        if (notBlank(city) || notBlank(country)) {
            points.add(new GeoPoint(trimToNull(city), trimToNull(country)));
        }
    }

    /**
     * Geo point for an expense line: prefer the enriched receipt extraction (read off the receipt PDF);
     * fall back to the LLM normalizer on the stored destination text when the receipt carried no city.
     */
    private GeoPoint expenseGeoFor(LineView line, Map<UUID, ReceiptExtractionResult> receipts,
                                   Map<String, GeoPoint> cache) {
        ReceiptExtractionResult r = line.attachmentId() != null ? receipts.get(line.attachmentId()) : null;
        if (r != null && (notBlank(r.getDestinationCity()) || notBlank(r.getDestinationCountry()))) {
            return new GeoPoint(trimToNull(r.getDestinationCity()), trimToNull(r.getDestinationCountry()));
        }
        return geoFor(line.destination(), cache);
    }

    /** Resolve a free-text place to a {@link GeoPoint} via the LLM normalizer, memoized per audit. */
    private GeoPoint geoFor(String location, Map<String, GeoPoint> cache) {
        if (location == null || location.isBlank()) {
            return new GeoPoint(null, null);
        }
        return cache.computeIfAbsent(location.trim().toLowerCase(Locale.ROOT),
                k -> geoNormalizationService.normalize(location));
    }

    /** Two places are geo-related when their cities match (loose) or, failing that, their countries do. */
    private static boolean geoRelated(GeoPoint a, GeoPoint b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.hasCity() && b.hasCity() && related(a.city(), b.city())) {
            return true;
        }
        // Country-level fallback handles airport/venue/city differences within the same destination country.
        return a.hasCountry() && b.hasCountry() && a.country().equalsIgnoreCase(b.country());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * #4 Amount alignment: compare claimed total against the plan's budget. The trip plan does not
     * persist a planned budget today, so this is recorded as skipped until that data exists.
     */
    private DimensionFinding evaluateAmountAlignment(PlanResponse plan, List<LineView> lines) {
        BigDecimal claimed = lines.stream()
                .map(LineView::amount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DimensionFinding("AMOUNT_ALIGNMENT", SKIPPED,
                "Trip plan stores no planned budget to compare against. Total claimed: "
                        + claimed.toPlainString() + ".");
    }

    /** #5 Receipt backing: each expense line should be backed by a receipt whose facts match the line. */
    private DimensionFinding evaluateReceiptBacking(List<LineView> lines, Map<UUID, ReceiptExtractionResult> receipts) {
        if (lines.isEmpty()) {
            return new DimensionFinding("RECEIPT_BACKING", SKIPPED,
                    "No expense lines found for this plan's report.");
        }
        List<String> problems = new ArrayList<>();
        int verified = 0;
        for (LineView line : lines) {
            if (line.attachmentId() == null) {
                problems.add(describe(line) + " has no attached receipt");
                continue;
            }
            ReceiptExtractionResult receipt = receipts.get(line.attachmentId());
            if (receipt == null || !receipt.isHasContent()) {
                // Best-effort: a missing/unreadable receipt is a "could not verify", not a hard mismatch.
                problems.add(describe(line) + " receipt could not be read");
                continue;
            }
            verified++;
            BigDecimal receiptTotal = receipt.getTotalAmount();
            if (line.amount() != null && receiptTotal != null && line.amount().compareTo(receiptTotal) != 0) {
                problems.add(describe(line) + " amount " + line.amount().toPlainString()
                        + " ≠ receipt total " + receiptTotal.toPlainString());
            }
            LocalDate receiptDate = parseDate(receipt.getDate());
            if (line.date() != null && receiptDate != null && !line.date().isEqual(receiptDate)) {
                problems.add(describe(line) + " date " + line.date() + " ≠ receipt date " + receiptDate);
            }
        }
        boolean hardMismatch = problems.stream().anyMatch(p -> p.contains("≠") || p.contains("no attached receipt"));
        if (hardMismatch) {
            return new DimensionFinding("RECEIPT_BACKING", FAIL,
                    "Receipt backing issues: " + String.join("; ", problems) + ".");
        }
        if (verified == 0) {
            return new DimensionFinding("RECEIPT_BACKING", SKIPPED,
                    "No receipts could be verified: " + String.join("; ", problems) + ".");
        }
        if (!problems.isEmpty()) {
            return new DimensionFinding("RECEIPT_BACKING", PASS,
                    verified + " receipt(s) verified; unverifiable: " + String.join("; ", problems) + ".");
        }
        return new DimensionFinding("RECEIPT_BACKING", PASS,
                "All " + verified + " expense line(s) are backed by a matching receipt.");
    }

    // --- helpers -----------------------------------------------------------------

    /** Re-extract every backed receipt ONCE, keyed by attachment id; shared across dimensions. */
    private Map<UUID, ReceiptExtractionResult> extractReceipts(String corpNo, List<LineView> lines) {
        Map<UUID, ReceiptExtractionResult> byAttachment = new HashMap<>();
        for (LineView line : lines) {
            UUID attachmentId = line.attachmentId();
            if (attachmentId == null || byAttachment.containsKey(attachmentId)) {
                continue;
            }
            String fileId = expenseRepo.findAttachmentFileIdById(attachmentId);
            ReceiptExtractionResult r = extractByFileId(corpNo, fileId);
            if (r != null) {
                byAttachment.put(attachmentId, r);
            }
        }
        return byAttachment;
    }

    /** Fetch a stored file and run the receipt extraction agent over it. Null on any failure. */
    private ReceiptExtractionResult extractByFileId(String corpNo, String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            Optional<UploadedFile> file = fileExtractionService.get(fileId);
            if (file.isEmpty()) {
                return null;
            }
            UploadedFile f = file.get();
            return receiptExtractionAgentService.extract(corpNo, f.content(), f.filename());
        } catch (RuntimeException e) {
            log.warn("Receipt extraction failed for file {}: {}", fileId, e.getMessage());
            return null;
        }
    }

    private String buildSummary(String ruleStatus, long fails, long evaluated, int lineCount) {
        if ("SKIPPED".equals(ruleStatus)) {
            return "R10 could not be evaluated: no comparable plan/report data.";
        }
        if (fails > 0) {
            return "R10 found " + fails + " mismatch dimension(s) between the trip plan and its "
                    + lineCount + " expense line(s).";
        }
        return "R10 passed: the expense report aligns with the approved trip plan across "
                + evaluated + " evaluated dimension(s).";
    }

    private String describe(LineView line) {
        String label = line.category() != null ? line.category() : line.section();
        return (label != null ? label : "expense") + " line";
    }

    private static LocalDate firstNonNull(LocalDate a, LocalDate b) {
        return a != null ? a : b;
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value.trim());
        }
    }

    /** Loose location match: either string contains the other once normalized to lowercase. */
    private static boolean related(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        return x.contains(y) || y.contains(x);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
