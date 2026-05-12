package com.api.bizplay_compliance.service.ruleService;

import com.api.bizplay_compliance.service.corpService.BusinessStatusLookupService;
import com.api.bizplay_compliance.service.corpService.HolidayInfoLookupService;
import com.api.bizplay_compliance.service.corpService.LocationGeocodeService;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RuleEngineService {

    private static final Map<String, Integer> R03_DUMMY_LIMITS = Map.of(
            "MEAL", 100_000,
            "TRANSPORT", 150_000,
            "SUPPLIES", 300_000,
            "LODGING", 500_000,
            "OTHER", 200_000
    );
    private static final List<String> R04_DUMMY_BLOCKED_MCC_CODES = List.of(
            "5812",
            "7995",
            "6012"
    );
    private static final Map<String, DummyRequisitionData> R10_DUMMY_REQUISITIONS = Map.of(
            "REQ-2026-0507-001", new DummyRequisitionData(
                    "REQ-2026-0507-001",
                    "APR-2026-0507-001",
                    "Employee meal",
                    "MEAL",
                    "낙곱덮",
                    6_000,
                    LocalDate.of(2026, 5, 7),
                    "27611801"
            ),
            "REQ-2026-0512-001", new DummyRequisitionData(
                    "REQ-2026-0512-001",
                    "APR-2026-0512-001",
                    "Team lunch",
                    "MEAL",
                    "생고기한약돼지숯불구이",
                    70_000,
                    LocalDate.of(2026, 5, 12),
                    "03712301"
            )
    );
    private static final Map<String, DummyApprovalData> R10_DUMMY_APPROVALS = Map.of(
            "APR-2026-0507-001", new DummyApprovalData(
                    "APR-2026-0507-001",
                    "REQ-2026-0507-001",
                    "APPROVED",
                    LocalDate.of(2026, 5, 7),
                    "27611801",
                    6_000
            ),
            "APR-2026-0512-001", new DummyApprovalData(
                    "APR-2026-0512-001",
                    "REQ-2026-0512-001",
                    "APPROVED",
                    LocalDate.of(2026, 5, 12),
                    "03712301",
                    70_000
            )
    );

    @FunctionalInterface
    private interface RuleCheckFn {
        RuleResult apply(Transaction transaction, Rule rule, RuleDataAccess dataAccess);
    }

    private final Map<String, RuleCheckFn> ruleCheckMap = new ConcurrentHashMap<>();
    private final BusinessStatusLookupService businessStatusLookupService;
    private final LocationGeocodeService locationGeocodeService;
    private final HolidayInfoLookupService holidayInfoLookupService;
    private final ReceiptDuplicateDetectionService receiptDuplicateDetectionService;
    private final ConcurrentLinkedDeque<CachedAmountEntry> splitPaymentTestCache = new ConcurrentLinkedDeque<>();

    public RuleEngineService(
            BusinessStatusLookupService businessStatusLookupService,
            LocationGeocodeService locationGeocodeService,
            HolidayInfoLookupService holidayInfoLookupService,
            ReceiptDuplicateDetectionService receiptDuplicateDetectionService
    ) {
        this.businessStatusLookupService = businessStatusLookupService;
        this.locationGeocodeService = locationGeocodeService;
        this.holidayInfoLookupService = holidayInfoLookupService;
        this.receiptDuplicateDetectionService = receiptDuplicateDetectionService;
        ruleCheckMap.put("R01", this::checkSplitPayment);
        ruleCheckMap.put("R02", this::checkNighttime);
        ruleCheckMap.put("R03", this::checkLimitExceed);
        ruleCheckMap.put("R04", this::checkMccProhibited);
        ruleCheckMap.put("R05", this::checkDuplicateReceipt);
        ruleCheckMap.put("R06", this::checkCardMismatch);
        ruleCheckMap.put("R07", this::checkBusinessRegistration);
        ruleCheckMap.put("R08", this::checkLocationAnomaly);
        ruleCheckMap.put("R09", this::checkHolidayUse);
        ruleCheckMap.put("R10", this::checkRequisitionMismatch);
    }

    public String ping() {
        return "ruleEngine ok";
    }

    public RuleResult checkNighttimeTransaction(LocalDateTime transactionDate) {
        if (transactionDate == null) {
            throw new IllegalArgumentException("Transaction date is required.");
        }

        return checkNighttime(
                new Transaction("test-tx", null, null, transactionDate, 0, null, null, null),
                buildRule("R02", null, null, null),
                emptyRuleDataAccess()
        );
    }

    public RuleResult checkLimitExceedAmount(Integer amount, String category) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }

        return checkLimitExceed(
                new Transaction("test-tx", null, null, null, amount, category, null, null),
                buildRule("R03", null, null, Map.of(
                        "limits", R03_DUMMY_LIMITS,
                        "default", R03_DUMMY_LIMITS.get("OTHER"),
                        "isDummyRule", true
                )),
                emptyRuleDataAccess()
        );
    }

    public RuleResult checkProhibitedMcc(String mccCode, List<String> blockedMccCodes) {
        if (mccCode == null || mccCode.isBlank()) {
            throw new IllegalArgumentException("MCC code is required.");
        }

        List<String> resolvedBlockedMccCodes = (blockedMccCodes == null || blockedMccCodes.isEmpty())
                ? R04_DUMMY_BLOCKED_MCC_CODES
                : blockedMccCodes;

        return checkMccProhibited(
                new Transaction("test-tx", null, null, null, 0, null, mccCode, null),
                buildRule("R04", null, null, Map.of(
                        "blocked", resolvedBlockedMccCodes,
                        "isDummyRule", blockedMccCodes == null || blockedMccCodes.isEmpty()
                )),
                emptyRuleDataAccess()
        );
    }

    public RuleResult checkDuplicateReceiptUpload(String receiptNumber, MultipartFile receiptFile) {
        ReceiptDuplicateDetectionService.ReceiptComparisonResult comparisonResult =
                receiptDuplicateDetectionService.compareReceiptNumber(receiptNumber, receiptFile);

        String detail;
        if (!comparisonResult.numberDetected()) {
            detail = "Could not detect a reliable receipt number from the uploaded receipt.";
            if (comparisonResult.reason() != null && !comparisonResult.reason().isBlank()) {
                detail += " " + comparisonResult.reason();
            }
            return new RuleResult(
                    "R05",
                    "Duplicate Receipt",
                    0,
                    detail
            );
        }

        String detectedFieldText = comparisonResult.detectedFieldLabel() != null
                && !comparisonResult.detectedFieldLabel().isBlank()
                ? " Field: \"" + comparisonResult.detectedFieldLabel() + "\"."
                : "";
        String confidenceText = comparisonResult.confidence() != null
                && !comparisonResult.confidence().isBlank()
                ? " Confidence: " + comparisonResult.confidence() + "."
                : "";

        if (comparisonResult.matches()) {
            detail = "Uploaded receipt matches receipt number \""
                    + comparisonResult.expectedReceiptNumber()
                    + "\". Detected receipt number: \""
                    + comparisonResult.detectedReceiptNumber()
                    + "\"."
                    + detectedFieldText
                    + confidenceText;
        } else {
            detail = "Uploaded receipt does not match the provided receipt number. Expected: \""
                    + comparisonResult.expectedReceiptNumber()
                    + "\", detected: \""
                    + comparisonResult.detectedReceiptNumber()
                    + "\"."
                    + detectedFieldText
                    + confidenceText;
        }

        if (comparisonResult.reason() != null && !comparisonResult.reason().isBlank()) {
            detail += " " + comparisonResult.reason();
        }

        return new RuleResult(
                "R05",
                "Duplicate Receipt",
                0,
                detail
        );
    }

    public RuleResult checkCardMismatch(String cardNumber, String receiptCardNumber) {
        if ((cardNumber == null || cardNumber.isBlank()) && (receiptCardNumber == null || receiptCardNumber.isBlank())) {
            throw new IllegalArgumentException("Card number or receipt card number is required.");
        }

        return checkCardMismatch(
                new Transaction("test-tx", null, null, null, 0, null, null, null),
                buildRule("R06", null, null, Map.of(
                        "cardNumber", cardNumber,
                        "receiptCardNumber", receiptCardNumber
                )),
                emptyRuleDataAccess()
        );
    }

    public RuleResult checkCardMismatchUpload(String cardNumber, MultipartFile receiptFile) {
        ReceiptDuplicateDetectionService.CardComparisonResult comparisonResult =
                receiptDuplicateDetectionService.compareCardNumber(cardNumber, receiptFile);

        if (!comparisonResult.visibleDigitsFound()) {
            String detail = "Could not detect a reliable card number pattern from the uploaded receipt.";
            if (comparisonResult.reason() != null && !comparisonResult.reason().isBlank()) {
                detail += " " + comparisonResult.reason();
            }
            return new RuleResult("R06", "Card Mismatch", 0, detail);
        }

        String fieldText = comparisonResult.detectedFieldLabel() != null && !comparisonResult.detectedFieldLabel().isBlank()
                ? " Field: \"" + comparisonResult.detectedFieldLabel() + "\"."
                : "";
        String confidenceText = comparisonResult.confidence() != null && !comparisonResult.confidence().isBlank()
                ? " Confidence: " + comparisonResult.confidence() + "."
                : "";

        String detail;
        if (comparisonResult.matches()) {
            detail = "Uploaded receipt card pattern matches the provided card number. Detected pattern: \""
                    + comparisonResult.detectedCardPattern()
                    + "\"."
                    + fieldText
                    + confidenceText;
        } else {
            detail = "Uploaded receipt card pattern does not match the provided card number. Detected pattern: \""
                    + comparisonResult.detectedCardPattern()
                    + "\"."
                    + fieldText
                    + confidenceText;
        }

        if (comparisonResult.reason() != null && !comparisonResult.reason().isBlank()) {
            detail += " " + comparisonResult.reason();
        }

        return new RuleResult("R06", "Card Mismatch", 0, detail);
    }

    public RuleResult testSplitPaymentAmount(Integer amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }

        LocalDateTime currentTime = LocalDateTime.now();
        pruneSplitPaymentTestCache(currentTime);

        List<Integer> nearbyAmounts = splitPaymentTestCache.stream()
                .map(CachedAmountEntry::amount)
                .toList();

        splitPaymentTestCache.addLast(new CachedAmountEntry(amount, currentTime));

        if (nearbyAmounts.isEmpty()) {
            return null;
        }

        int splitAmountRangeStart = 49_000;
        int splitAmountRangeEnd = 49_999;
        boolean isCurrentTransactionInSplitAmountRange = amount >= splitAmountRangeStart && amount <= splitAmountRangeEnd;

        long suspiciousTransactionCountInWindow = nearbyAmounts.stream()
                .filter(cachedAmount -> cachedAmount >= splitAmountRangeStart && cachedAmount <= splitAmountRangeEnd)
                .count();

        if (isCurrentTransactionInSplitAmountRange || suspiciousTransactionCountInWindow > 0) {
            return new RuleResult(
                    "R01",
                    "Split Payment Detection",
                    0,
                    "Cached split-payment pattern detected within 30 minutes for " + (nearbyAmounts.size() + 1)
                            + " transactions. Amount is near the 50,000 threshold."
            );
        }

        if (nearbyAmounts.size() >= 2) {
            return new RuleResult(
                    "R01",
                    "Split Payment Detection",
                    0,
                    "Cached split-payment pattern detected within 30 minutes for " + (nearbyAmounts.size() + 1)
                            + " transactions."
            );
        }

        return null;
    }

    public BusinessStatusLookupService.BusinessStatus lookupBusinessRegistration(String businessNumber) {
        String normalizedBusinessNumber = sanitizeBusinessNumber(businessNumber);
        if (normalizedBusinessNumber == null) {
            throw new IllegalArgumentException("Business number is required.");
        }
        return businessStatusLookupService.lookupBusinessStatus(normalizedBusinessNumber);
    }

    public LocationGeocodeService.GeocodedLocation lookupAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address is required.");
        }
        return locationGeocodeService.geocode(address);
    }

    public Optional<HolidayInfoLookupService.HolidayInfo> lookupHolidayUse(LocalDate transactionDate) {
        if (transactionDate == null) {
            throw new IllegalArgumentException("Transaction date is required.");
        }

        DayOfWeek dayOfWeek = transactionDate.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return Optional.of(new HolidayInfoLookupService.HolidayInfo(
                    "WEEKEND",
                    dayOfWeek == DayOfWeek.SATURDAY ? "Saturday" : "Sunday",
                    "Y",
                    Long.parseLong(transactionDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)),
                    0
            ));
        }

        return holidayInfoLookupService.findHoliday(transactionDate);
    }

    public RuleResult checkHolidayUse(LocalDate transactionDate) {
        if (transactionDate == null) {
            throw new IllegalArgumentException("Transaction date is required.");
        }

        return checkHolidayUse(
                new Transaction("test-tx", null, null, transactionDate.atStartOfDay(), 0, null, null, null),
                buildRule("R09", null, null, null),
                emptyRuleDataAccess()
        );
    }

    public RuleResult checkRequisitionMismatch(String requisitionId, String transactionReference) {
        if ((requisitionId == null || requisitionId.isBlank())
                && (transactionReference == null || transactionReference.isBlank())) {
            throw new IllegalArgumentException("Requisition id or transaction reference is required.");
        }

        return checkRequisitionMismatch(
                new Transaction("test-tx", null, null, null, 0, null, null, null),
                buildRule("R10", null, null, Map.of(
                        "requisitionId", requisitionId,
                        "transactionReference", transactionReference
                )),
                emptyRuleDataAccess()
        );
    }

    public RuleResult runSingleRule(String ruleCode, SingleRuleRequest request) {
        RuleDataAccess dataAccess = new RequestRuleDataAccess(
                request.nearbyTransactions(),
                request.duplicateReceiptTransaction()
        );

        Rule rule = buildRule(ruleCode, request.ruleName(), request.scoreDelta(), request.params());
        return runRule(ruleCode, resolveTransaction(request), rule, dataAccess);
    }

    public List<RuleResult> runPipeline(PipelineRequest request) {
        return runPipeline(request, null);
    }

    public List<RuleResult> runPipeline(PipelineRequest request, MultipartFile receiptFile) {
        Transaction transaction = resolveTransaction(request);
        RuleDataAccess dataAccess = emptyRuleDataAccess();
        List<RuleResult> detections = new ArrayList<>();

        addIfPresent(detections, testSplitPaymentAmount(request.amount()));
        addIfPresent(detections, runRule("R02", transaction, buildRule("R02", null, null, Map.of()), dataAccess));
        addIfPresent(detections, runRule("R03", transaction, buildRule("R03", null, null, Map.of(
                "limits", R03_DUMMY_LIMITS,
                "default", R03_DUMMY_LIMITS.get("OTHER"),
                "isDummyRule", true
        )), dataAccess));
        addIfPresent(detections, runRule("R04", transaction, buildRule("R04", null, null, Map.of(
                "blocked", R04_DUMMY_BLOCKED_MCC_CODES,
                "isDummyRule", true
        )), dataAccess));

        if (receiptFile != null && !receiptFile.isEmpty() && request.receiptNumber() != null && !request.receiptNumber().isBlank()) {
            addIfPresent(detections, checkDuplicateReceiptUpload(request.receiptNumber(), receiptFile));
        }

        if (receiptFile != null && !receiptFile.isEmpty() && request.cardNumber() != null && !request.cardNumber().isBlank()) {
            addIfPresent(detections, checkCardMismatchUpload(request.cardNumber(), receiptFile));
        } else {
            addIfPresent(detections, runRule("R06", transaction, buildRule("R06", null, null, Map.of(
                    "cardNumber", request.cardNumber(),
                    "receiptCardNumber", request.receiptCardNumber()
            )), dataAccess));
        }

        addIfPresent(detections, runRule("R07", transaction, buildRule("R07", null, null, Map.of(
                "businessNumber", request.businessNumber()
        )), dataAccess));
        addIfPresent(detections, runRule("R08", transaction, buildRule("R08", null, null, Map.of(
                "address", request.address()
        )), dataAccess));
        addIfPresent(detections, runRule("R09", transaction, buildRule("R09", null, null, Map.of()), dataAccess));
        addIfPresent(detections, runRule("R10", transaction, buildRule("R10", null, null, Map.of(
                "requisitionId", request.requisitionId(),
                "transactionReference", request.transactionReference()
        )), dataAccess));

        return detections;
    }

    public RuleResult runRule(String ruleCode, Transaction transaction, Rule rule, RuleDataAccess dataAccess) {
        RuleCheckFn checkFn = ruleCheckMap.get(ruleCode);
        if (checkFn == null) {
            return null;
        }

        try {
            return checkFn.apply(transaction, rule, dataAccess);
        } catch (Exception exception) {
            System.err.println("[RuleEngine] Rule check failed: " + exception.getMessage());
            return null;
        }
    }

    public List<RuleResult> runRules(Transaction transaction, RuleDataAccess dataAccess) {
        List<Rule> rules = dataAccess.loadActiveRules();
        return runRules(transaction, rules, dataAccess);
    }

    public List<RuleResult> runRules(Transaction transaction, List<Rule> rules, RuleDataAccess dataAccess) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<RuleResult> detections = new ArrayList<>();

        for (Rule rule : rules) {
            RuleCheckFn checkFn = ruleCheckMap.get(rule.ruleCode());
            if (checkFn == null) {
                continue;
            }

            try {
                RuleResult result = checkFn.apply(transaction, rule, dataAccess);
                if (result != null) {
                    detections.add(result);
                }
            } catch (Exception exception) {
                System.err.println("[RuleEngine] Rule check failed: " + exception.getMessage());
            }
        }

        return detections;
    }

    public List<RuleResult> runDefaultRulePipeline(Transaction transaction, RuleDataAccess dataAccess) {
        return runRules(transaction, defaultRules(), dataAccess);
    }

    public List<Rule> defaultRules() {
        return List.of(
                new Rule("R01", "Split Payment Detection", "R01", 0, Map.of()),
                new Rule("R02", "Nighttime Transaction", "R02", 0, Map.of()),
                new Rule("R03", "Limit Exceed", "R03", 0, Map.of()),
                new Rule("R04", "MCC Prohibited", "R04", 0, Map.of()),
                new Rule("R05", "Duplicate Receipt", "R05", 0, Map.of()),
                new Rule("R06", "Card Mismatch", "R06", 0, Map.of()),
                new Rule("R07", "Business Registration Check", "R07", 0, Map.of()),
                new Rule("R08", "Location Anomaly", "R08", 0, Map.of()),
                new Rule("R09", "Holiday Use", "R09", 0, Map.of()),
                new Rule("R10", "Requisition Mismatch", "R10", 0, Map.of())
        );
    }

    private Rule buildRule(
            String ruleCode,
            String ruleName,
            Integer scoreDelta,
            Map<String, Object> params
    ) {
        String resolvedName = ruleName != null && !ruleName.isBlank()
                ? ruleName
                : defaultRuleName(ruleCode);

        return new Rule(
                ruleCode,
                resolvedName,
                ruleCode,
                scoreDelta != null ? scoreDelta : 0,
                params != null ? params : Map.of()
        );
    }

    private String defaultRuleName(String ruleCode) {
        return switch (ruleCode) {
            case "R01" -> "Split Payment Detection";
            case "R02" -> "Nighttime Transaction";
            case "R03" -> "Limit Exceed";
            case "R04" -> "MCC Prohibited";
            case "R05" -> "Duplicate Receipt";
            case "R06" -> "Card Mismatch";
            case "R07" -> "Business Registration Check";
            case "R08" -> "Location Anomaly";
            case "R09" -> "Holiday Use";
            case "R10" -> "Requisition Mismatch";
            default -> "Unknown Rule";
        };
    }

    /**
     * R01 - Split payment detection.
     */
    private RuleResult checkSplitPayment(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        int splitDetectionWindowMinutes = 30;
        LocalDateTime currentTransactionTime = tx.transactionDate();
        LocalDateTime splitDetectionStartTime = currentTransactionTime.minusMinutes(splitDetectionWindowMinutes);
        LocalDateTime splitDetectionEndTime = currentTransactionTime.plusMinutes(splitDetectionWindowMinutes);

        List<Transaction> transactionsInDetectionWindow = dataAccess.findNearbyTransactions(
                tx.employeeId(),
                tx.merchantId(),
                tx.id(),
                splitDetectionStartTime,
                splitDetectionEndTime
        );

        if (transactionsInDetectionWindow == null || transactionsInDetectionWindow.isEmpty()) {
            return testSplitPaymentAmount(tx.amount());
        }

        int splitAmountRangeStart = 49_000;
        int splitAmountRangeEnd = 49_999;
        boolean isCurrentTransactionInSplitAmountRange =
                tx.amount() >= splitAmountRangeStart && tx.amount() <= splitAmountRangeEnd;

        long suspiciousTransactionCountInWindow = transactionsInDetectionWindow.stream()
                .filter(transaction ->
                        transaction.amount() >= splitAmountRangeStart
                                && transaction.amount() <= splitAmountRangeEnd)
                .count();

        if (isCurrentTransactionInSplitAmountRange || suspiciousTransactionCountInWindow > 0) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Detected " + (transactionsInDetectionWindow.size() + 1)
                            + " transactions for the same employee and merchant within 30 minutes. Amount is near the 50,000 threshold."
            );
        }

        if (transactionsInDetectionWindow.size() >= 2) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Detected " + (transactionsInDetectionWindow.size() + 1)
                            + " transactions for the same employee and merchant within 30 minutes."
            );
        }

        return null;
    }
    /**
     * R02 - Nighttime transaction.
     */
    private RuleResult checkNighttime(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        int hour = tx.transactionDate().getHour();

        if (hour >= 22 || hour < 6) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Transaction occurred during the nighttime restricted window at hour " + hour + "."
            );
        }

        return null;
    }

    /**
     * R03 - Limit exceed.
     */
    private RuleResult checkLimitExceed(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        Map<String, Object> params = Optional.ofNullable(rule.params()).orElse(Map.of());
        Map<String, Integer> limits = getIntegerMap(params.get("limits"));
        if (limits.isEmpty()) {
            limits = R03_DUMMY_LIMITS;
        }

        int defaultLimit = getInteger(params.get("default"), R03_DUMMY_LIMITS.get("OTHER"));
        boolean isDummyRule = Boolean.TRUE.equals(params.get("isDummyRule")) || !params.containsKey("limits");
        String category = normalizeCategory(tx.category());
        int limit = limits.getOrDefault(category, defaultLimit);

        if (tx.amount() >= limit) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Category \"" + category + "\" limit exceeded. Limit: " + formatAmount(limit) + ", amount: "
                            + formatAmount(tx.amount()) + "."
                            + (isDummyRule ? " Dummy test rule applied." : "")
            );
        }

        return null;
    }

    /**
     * R04 - MCC prohibited.
     */
    private RuleResult checkMccProhibited(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        Map<String, Object> params = Optional.ofNullable(rule.params()).orElse(Map.of());
        List<String> blocked = getStringList(params.get("blocked"));
        if (blocked.isEmpty()) {
            blocked = R04_DUMMY_BLOCKED_MCC_CODES;
        }
        boolean isDummyRule = Boolean.TRUE.equals(params.get("isDummyRule")) || !params.containsKey("blocked");

        if (blocked.contains(tx.mccCode())) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Transaction uses a blocked merchant category code (MCC: " + tx.mccCode() + ")."
                            + (isDummyRule ? " Dummy blocked MCC list applied." : "")
            );
        }

        return null;
    }

    /**
     * R05 - Duplicate receipt.
     */
    private RuleResult checkDuplicateReceipt(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        if (tx.receiptHash() == null || tx.receiptHash().isBlank()) {
            return null;
        }

        Optional<Transaction> duplicate = dataAccess.findDuplicateReceipt(tx.receiptHash(), tx.id());
        return duplicate.map(transaction -> new RuleResult(
                rule.id(),
                rule.name(),
                rule.scoreDelta(),
                "The same receipt hash was found on another transaction (" + transaction.id() + ")."
        )).orElse(null);

    }

    /**
     * R06 - Card mismatch (placeholder).
     */
    private RuleResult checkCardMismatch(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        Map<String, Object> params = Optional.ofNullable(rule.params()).orElse(Map.of());
        String cardNumber = getString(params.get("cardNumber"));
        String receiptCardNumber = getString(params.get("receiptCardNumber"));

        if (cardNumber == null || cardNumber.isBlank() || receiptCardNumber == null || receiptCardNumber.isBlank()) {
            return null;
        }

        String normalizedCardNumber = normalizeDigits(cardNumber);
        String normalizedReceiptPattern = normalizeMaskedCardPattern(receiptCardNumber);
        CardPatternMatchResult matchResult = matchMaskedCardPattern(normalizedCardNumber, normalizedReceiptPattern);

        if (!matchResult.visibleDigitsFound()) {
            return null;
        }

        if (!matchResult.matches()) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Receipt card pattern does not match the provided card number. Card number: \""
                            + cardNumber
                            + "\", receipt pattern: \""
                            + receiptCardNumber
                            + "\"."
            );
        }

        return null;
    }

    /**
     * R07 - Business registration check.
     */
    private RuleResult checkBusinessRegistration(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        String businessNumber = resolveBusinessNumber(tx, rule.params());
        if (businessNumber == null) {
            return null;
        }

        if (businessNumber.length() != 10) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Business number format is invalid: " + businessNumber
            );
        }

        BusinessStatusLookupService.BusinessStatus businessStatus =
                businessStatusLookupService.lookupBusinessStatus(businessNumber);

        if (!businessStatus.isRegistered()) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Business number is not registered: " + businessNumber
            );
        }

        if (businessStatus.isInactive()) {
            String statusText = firstNonBlank(
                    businessStatus.businessStatus(),
                    businessStatus.taxType(),
                    "inactive"
            );
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Business number is inactive (" + statusText + "): " + businessNumber
            );
        }

        return null;
    }

    /**
     * R08 - Location anomaly.
     */
    private RuleResult checkLocationAnomaly(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        Map<String, Object> params = Optional.ofNullable(rule.params()).orElse(Map.of());
        String address = firstNonBlank(
                getString(params.get("address")),
                getString(params.get("transactionAddress")),
                getString(params.get("currentAddress"))
        );

        if (address == null) {
            return null;
        }

        try {
            locationGeocodeService.geocode(address);
            return null;
        } catch (Exception exception) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Address could not be resolved: " + address
            );
        }
    }

    /**
     * R09 - Holiday / weekend use.
     */
    private RuleResult checkHolidayUse(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        DayOfWeek dayOfWeek = tx.transactionDate().getDayOfWeek();
        LocalDate transactionDate = tx.transactionDate().toLocalDate();

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            String dayName = dayOfWeek == DayOfWeek.SATURDAY ? "Saturday" : "Sunday";
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Transaction occurred on " + dayName + "."
            );
        }

        Optional<HolidayInfoLookupService.HolidayInfo> holidayInfo = holidayInfoLookupService.findHoliday(transactionDate);
        if (holidayInfo.isPresent()) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Transaction occurred on a public holiday: " + holidayInfo.get().dateName() + "."
            );
        }

        return null;
    }

    private RuleResult checkRequisitionMismatch(Transaction tx, Rule rule, RuleDataAccess dataAccess) {
        Map<String, Object> params = Optional.ofNullable(rule.params()).orElse(Map.of());
        String requisitionId = getString(params.get("requisitionId"));
        String transactionReference = getString(params.get("transactionReference"));

        if (requisitionId == null || requisitionId.isBlank()) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Requisition id is required for the dummy R10 rule."
            );
        }

        DummyRequisitionData requisition = R10_DUMMY_REQUISITIONS.get(requisitionId);
        if (requisition == null) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "No dummy requisition data found for requisition id \"" + requisitionId + "\"."
            );
        }

        DummyApprovalData approval = R10_DUMMY_APPROVALS.get(requisition.approvalId());
        if (approval == null) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "No dummy approval data found for requisition id \"" + requisitionId + "\"."
            );
        }

        if (!"APPROVED".equalsIgnoreCase(approval.status())) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Dummy approval data is not approved for requisition id \"" + requisitionId + "\"."
            );
        }

        if (transactionReference == null || transactionReference.isBlank()) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Transaction reference is required. Expected dummy reference: \"" + approval.approvedTransactionReference() + "\"."
            );
        }

        if (!approval.approvedTransactionReference().equals(transactionReference)) {
            return new RuleResult(
                    rule.id(),
                    rule.name(),
                    rule.scoreDelta(),
                    "Transaction reference mismatch for requisition id \"" + requisitionId + "\". Expected: \""
                            + approval.approvedTransactionReference()
                            + "\", received: \""
                            + transactionReference
                            + "\". Dummy requisition: "
                            + requisition.purpose()
                            + ", merchant: "
                            + requisition.merchantName()
                            + ", amount: "
                            + formatAmount(requisition.amount())
                            + "."
            );
        }

        return null;
    }

    private String formatAmount(int amount) {
        return String.format("%,d", amount);
    }

    private Transaction resolveTransaction(SingleRuleRequest request) {
        return new Transaction(
                defaultString(request.id(), "test-tx"),
                request.employeeId(),
                request.merchantId(),
                request.transactionDate(),
                request.amount() != null ? request.amount() : 0,
                request.category(),
                request.mccCode(),
                request.receiptHash()
        );
    }

    private Transaction resolveTransaction(PipelineRequest request) {
        return new Transaction(
                defaultString(request.id(), "test-tx"),
                request.employeeId(),
                request.merchantId(),
                request.transactionDate(),
                request.amount() != null ? request.amount() : 0,
                request.category(),
                request.mccCode(),
                null
        );
    }

    private Map<String, Integer> getIntegerMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Integer> result = new ConcurrentHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof Number number) {
                result.put(key, number.intValue());
            }
        }
        return result;
    }

    private List<String> getStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof String stringValue) {
                result.add(stringValue);
            }
        }
        return result;
    }

    private int getInteger(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String resolveBusinessNumber(Transaction tx, Map<String, Object> params) {
        Map<String, Object> safeParams = params != null ? params : Map.of();

        String explicitBusinessNumber = sanitizeBusinessNumber(firstNonBlank(
                getString(safeParams.get("businessNumber")),
                getString(safeParams.get("bizNo")),
                getString(safeParams.get("b_no"))
        ));
        if (explicitBusinessNumber != null) {
            return explicitBusinessNumber;
        }

        return sanitizeBusinessNumber(tx.merchantId());
    }

    private String sanitizeBusinessNumber(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String digitsOnly = rawValue.replaceAll("\\D", "");
        return digitsOnly.isBlank() ? null : digitsOnly;
    }

    private String getString(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String defaultString(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "OTHER";
        }

        return category.trim().toUpperCase();
    }

    private String normalizeDigits(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.replaceAll("\\D", "");
    }

    private String normalizeMaskedCardPattern(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.toUpperCase()
                .replace('X', '*')
                .replace('#', '*');

        StringBuilder builder = new StringBuilder();
        for (char character : normalized.toCharArray()) {
            if (Character.isDigit(character) || character == '*') {
                builder.append(character);
            }
        }

        return builder.toString();
    }

    private CardPatternMatchResult matchMaskedCardPattern(String cardNumber, String maskedPattern) {
        if (cardNumber.isBlank() || maskedPattern.isBlank()) {
            return new CardPatternMatchResult(false, false);
        }

        List<String> visibleChunks = List.of(maskedPattern.split("\\*+"))
                .stream()
                .filter(chunk -> !chunk.isBlank())
                .toList();

        if (visibleChunks.isEmpty()) {
            return new CardPatternMatchResult(false, false);
        }

        int searchStart = 0;
        for (int index = 0; index < visibleChunks.size(); index++) {
            String chunk = visibleChunks.get(index);
            int foundAt = cardNumber.indexOf(chunk, searchStart);
            if (foundAt < 0) {
                return new CardPatternMatchResult(true, false);
            }

            if (index == 0 && !maskedPattern.startsWith("*") && foundAt != 0) {
                return new CardPatternMatchResult(true, false);
            }

            searchStart = foundAt + chunk.length();
        }

        if (!maskedPattern.endsWith("*")) {
            String suffix = visibleChunks.get(visibleChunks.size() - 1);
            if (!cardNumber.endsWith(suffix)) {
                return new CardPatternMatchResult(true, false);
            }
        }

        return new CardPatternMatchResult(true, true);
    }

    private RuleDataAccess emptyRuleDataAccess() {
        return new RequestRuleDataAccess(List.of(), null);
    }

    private void addIfPresent(List<RuleResult> detections, RuleResult result) {
        if (result != null) {
            detections.add(result);
        }
    }

    private void pruneSplitPaymentTestCache(LocalDateTime currentTime) {
        while (true) {
            CachedAmountEntry firstEntry = splitPaymentTestCache.peekFirst();
            if (firstEntry == null) {
                return;
            }

            if (Duration.between(firstEntry.transactionTime(), currentTime).toMinutes() < 30) {
                return;
            }

            splitPaymentTestCache.pollFirst();
        }
    }

    public interface RuleDataAccess {
        List<Rule> loadActiveRules();

        List<Transaction> findNearbyTransactions(
                String employeeId,
                String merchantId,
                String transactionId,
                LocalDateTime windowStart,
                LocalDateTime windowEnd
        );

        Optional<Transaction> findDuplicateReceipt(String receiptHash, String transactionId);
    }

    public record SingleRuleRequest(
            String id,
            String employeeId,
            String merchantId,
            LocalDateTime transactionDate,
            Integer amount,
            String category,
            String mccCode,
            String receiptHash,
            List<Transaction> nearbyTransactions,
            Transaction duplicateReceiptTransaction,
            Map<String, Object> params,
            Integer scoreDelta,
            String ruleName
    ) {
    }

    public record PipelineRequest(
            @Schema(example = "tx-2026-0512-001", defaultValue = "tx-2026-0512-001")
            String id,
            @Schema(example = "EMP-1001", defaultValue = "EMP-1001")
            String employeeId,
            @Schema(example = "3150140032", defaultValue = "3150140032")
            String merchantId,
            @Schema(example = "2026-05-12T12:46:52", defaultValue = "2026-05-12T12:46:52")
            LocalDateTime transactionDate,
            @Schema(example = "70000", defaultValue = "70000")
            Integer amount,
            @Schema(example = "MEAL", defaultValue = "MEAL")
            String category,
            @Schema(example = "5812", defaultValue = "5812")
            String mccCode,
            @Schema(example = "03712301", defaultValue = "03712301")
            String receiptNumber,
            @Schema(example = "5327501212342536", defaultValue = "5327501212342536")
            String cardNumber,
            @Schema(example = "5327-50**-****-2536", defaultValue = "5327-50**-****-2536")
            String receiptCardNumber,
            @Schema(example = "3158300467", defaultValue = "3158300467")
            String businessNumber,
            @Schema(example = "충북 청주시 흥덕구 복대동 1657", defaultValue = "충북 청주시 흥덕구 복대동 1657")
            String address,
            @Schema(example = "REQ-2026-0512-001", defaultValue = "REQ-2026-0512-001")
            String requisitionId,
            @Schema(example = "03712301", defaultValue = "03712301")
            String transactionReference
    ) {
    }

    private static final class RequestRuleDataAccess implements RuleDataAccess {

        private final List<Transaction> nearbyTransactions;
        private final Transaction duplicateReceiptTransaction;

        private RequestRuleDataAccess(
                List<Transaction> nearbyTransactions,
                Transaction duplicateReceiptTransaction
        ) {
            this.nearbyTransactions = nearbyTransactions != null ? nearbyTransactions : List.of();
            this.duplicateReceiptTransaction = duplicateReceiptTransaction;
        }

        @Override
        public List<Rule> loadActiveRules() {
            return List.of();
        }

        @Override
        public List<Transaction> findNearbyTransactions(
                String employeeId,
                String merchantId,
                String transactionId,
                LocalDateTime windowStart,
                LocalDateTime windowEnd
        ) {
            return nearbyTransactions.stream()
                    .filter(transaction -> !transaction.id().equals(transactionId))
                    .filter(transaction -> employeeId.equals(transaction.employeeId()))
                    .filter(transaction -> merchantId.equals(transaction.merchantId()))
                    .filter(transaction -> !transaction.transactionDate().isBefore(windowStart))
                    .filter(transaction -> !transaction.transactionDate().isAfter(windowEnd))
                    .toList();
        }

        @Override
        public Optional<Transaction> findDuplicateReceipt(String receiptHash, String transactionId) {
            if (duplicateReceiptTransaction == null) {
                return Optional.empty();
            }

            boolean isDuplicate = receiptHash.equals(duplicateReceiptTransaction.receiptHash())
                    && !transactionId.equals(duplicateReceiptTransaction.id());

            return isDuplicate ? Optional.of(duplicateReceiptTransaction) : Optional.empty();
        }
    }

    public record RuleResult(
            String ruleId,
            String ruleName,
            int scoreDelta,
            String detail
    ) {
    }

    public record Transaction(
            String id,
            String employeeId,
            String merchantId,
            LocalDateTime transactionDate,
            int amount,
            String category,
            String mccCode,
            String receiptHash
    ) {
    }

    public record Rule(
            String id,
            String name,
            String ruleCode,
            int scoreDelta,
            Map<String, Object> params
    ) {
    }

    private record CachedAmountEntry(
            int amount,
            LocalDateTime transactionTime
    ) {
    }

    private record DummyRequisitionData(
            String requisitionId,
            String approvalId,
            String purpose,
            String category,
            String merchantName,
            int amount,
            LocalDate approvedUseDate,
            String expectedTransactionReference
    ) {
    }

    private record DummyApprovalData(
            String approvalId,
            String requisitionId,
            String status,
            LocalDate approvalDate,
            String approvedTransactionReference,
            int approvedAmount
    ) {
    }

    private record CardPatternMatchResult(
            boolean visibleDigitsFound,
            boolean matches
    ) {
    }
}

