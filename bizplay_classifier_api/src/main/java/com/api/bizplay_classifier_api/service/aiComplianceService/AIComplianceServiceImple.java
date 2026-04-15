package com.api.bizplay_classifier_api.service.aiComplianceService;

import com.api.bizplay_classifier_api.model.dto.AuditRuleDTO;
import com.api.bizplay_classifier_api.model.dto.AuditRuleResultDTO;
import com.api.bizplay_classifier_api.model.dto.AuditTransactionDTO;
import com.api.bizplay_classifier_api.repository.AIComplianceRepo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIComplianceServiceImple implements AIComplianceService {

    private static final int SPLIT_WINDOW_MINUTES = 30;
    private static final long SPLIT_THRESHOLD_LOW = 49_000L;
    private static final long SPLIT_THRESHOLD_HIGH = 49_999L;
    private static final long DEFAULT_LIMIT = 200_000L;

    private final AIComplianceRepo aiComplianceRepo;

    private final Map<String, BiFunction<AuditTransactionDTO, AuditRuleDTO, AuditRuleResultDTO>> ruleCheckMap = Map.of(
            "R01", this::checkSplitPayment,
            "R02", this::checkNighttime,
            "R03", this::checkLimitExceed,
            "R04", this::checkMccProhibited,
            "R05", this::checkDuplicateReceipt,
            "R06", this::checkCardMismatch,
            "R07", this::checkBusinessRegistration,
            "R08", this::checkLocationAnomaly,
            "R09", this::checkHolidayUse,
            "R10", this::checkRequisitionMismatch
    );

    @Override
    public List<AuditRuleResultDTO> runRules(AuditTransactionDTO transaction) {
        List<AuditRuleDTO> rules = aiComplianceRepo.getActiveAuditRules();
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<AuditRuleResultDTO> detections = new ArrayList<>();
        for (AuditRuleDTO rule : rules.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()))
                .sorted(Comparator.comparing(AuditRuleDTO::getRuleCode, Comparator.nullsLast(String::compareTo)))
                .toList()) {
            BiFunction<AuditTransactionDTO, AuditRuleDTO, AuditRuleResultDTO> checkFn = ruleCheckMap.get(rule.getRuleCode());
            if (checkFn == null) {
                continue;
            }

            try {
                AuditRuleResultDTO result = checkFn.apply(transaction, rule);
                if (result != null) {
                    detections.add(result);
                }
            } catch (Exception ex) {
                log.error("[RuleEngine] Rule check failed for {}: {}", rule.getRuleCode(), ex.getMessage(), ex);
            }
        }

        return detections;
    }

    private AuditRuleResultDTO checkSplitPayment(AuditTransactionDTO tx, AuditRuleDTO rule) {
        if (tx == null || tx.getTransactionDate() == null || tx.getEmployeeId() == null || tx.getMerchantId() == null) {
            return null;
        }

        LocalDateTime windowStart = tx.getTransactionDate().minusMinutes(SPLIT_WINDOW_MINUTES);
        LocalDateTime windowEnd = tx.getTransactionDate().plusMinutes(SPLIT_WINDOW_MINUTES);

        List<AuditTransactionDTO> nearby = aiComplianceRepo.findNearbyTransactions(
                tx.getEmployeeId(),
                tx.getMerchantId(),
                tx.getId(),
                windowStart,
                windowEnd
        );

        if (nearby == null || nearby.isEmpty()) {
            return null;
        }

        boolean suspiciousAmount = isWithinSplitThreshold(tx.getAmount());
        long nearbyCount = nearby.stream()
                .filter(item -> isWithinSplitThreshold(item.getAmount()))
                .count();

        if (suspiciousAmount || nearbyCount > 0) {
            return buildResult(
                    rule,
                    "동일 직원·가맹점에서 30분 이내 %d건 결제 감지. 금액이 한도 직전 구간(49,000~49,999)에 해당합니다."
                            .formatted(nearby.size() + 1)
            );
        }

        if (nearby.size() >= 2) {
            return buildResult(
                    rule,
                    "동일 직원·가맹점에서 30분 이내 %d건 결제 감지.".formatted(nearby.size() + 1)
            );
        }

        return null;
    }

    private AuditRuleResultDTO checkNighttime(AuditTransactionDTO tx, AuditRuleDTO rule) {
        if (tx == null || tx.getTransactionDate() == null) {
            return null;
        }

        int hour = tx.getTransactionDate().getHour();
        if (hour >= 22 || hour < 6) {
            return buildResult(rule, "심야 시간대(%d시) 결제가 감지되었습니다.".formatted(hour));
        }
        return null;
    }

    private AuditRuleResultDTO checkLimitExceed(AuditTransactionDTO tx, AuditRuleDTO rule) {
        if (tx == null || tx.getAmount() == null) {
            return null;
        }

        JsonNode params = rule.getParams();
        String category = isBlank(tx.getCategory()) ? "기타" : tx.getCategory();
        long defaultLimit = params != null && params.has("default") ? params.path("default").asLong(DEFAULT_LIMIT) : DEFAULT_LIMIT;
        long limit = defaultLimit;

        if (params != null && params.has("limits")) {
            JsonNode limits = params.path("limits");
            if (limits.has(category)) {
                limit = limits.path(category).asLong(defaultLimit);
            }
        }

        if (tx.getAmount() > limit) {
            return buildResult(
                    rule,
                    "카테고리 \"%s\" 한도(%s원) 초과: %s원 결제."
                            .formatted(category, formatAmount(limit), formatAmount(tx.getAmount()))
            );
        }

        return null;
    }

    private AuditRuleResultDTO checkMccProhibited(AuditTransactionDTO tx, AuditRuleDTO rule) {
        if (tx == null || isBlank(tx.getMccCode()) || rule.getParams() == null) {
            return null;
        }

        JsonNode blocked = rule.getParams().path("blocked");
        if (!blocked.isArray()) {
            return null;
        }

        for (JsonNode blockedCode : blocked) {
            if (tx.getMccCode().equals(blockedCode.asText())) {
                return buildResult(rule, "금지 업종 코드(MCC: %s)에 해당하는 결제입니다.".formatted(tx.getMccCode()));
            }
        }

        return null;
    }

    private AuditRuleResultDTO checkDuplicateReceipt(AuditTransactionDTO tx, AuditRuleDTO rule) {
        if (tx == null || isBlank(tx.getReceiptHash())) {
            return null;
        }

        List<AuditTransactionDTO> duplicates = aiComplianceRepo.findDuplicateReceiptTransactions(
                tx.getReceiptHash(),
                tx.getId(),
                1
        );

        if (duplicates != null && !duplicates.isEmpty()) {
            UUID duplicateId = duplicates.getFirst().getId();
            return buildResult(rule, "동일한 영수증 해시가 다른 거래(%s)에서 발견되었습니다.".formatted(duplicateId));
        }

        return null;
    }

    private AuditRuleResultDTO checkCardMismatch(AuditTransactionDTO tx, AuditRuleDTO rule) {
        return null;
    }

    private AuditRuleResultDTO checkBusinessRegistration(AuditTransactionDTO tx, AuditRuleDTO rule) {
        return null;
    }

    private AuditRuleResultDTO checkLocationAnomaly(AuditTransactionDTO tx, AuditRuleDTO rule) {
        return null;
    }

    private AuditRuleResultDTO checkHolidayUse(AuditTransactionDTO tx, AuditRuleDTO rule) {
        if (tx == null || tx.getTransactionDate() == null) {
            return null;
        }

        DayOfWeek dayOfWeek = tx.getTransactionDate().getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            String dayName = dayOfWeek == DayOfWeek.SUNDAY ? "일요일" : "토요일";
            return buildResult(rule, "주말(%s) 결제가 감지되었습니다.".formatted(dayName));
        }

        return null;
    }

    private AuditRuleResultDTO checkRequisitionMismatch(AuditTransactionDTO tx, AuditRuleDTO rule) {
        return null;
    }

    private AuditRuleResultDTO buildResult(AuditRuleDTO rule, String detail) {
        return AuditRuleResultDTO.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .scoreDelta(rule.getScoreDelta())
                .detail(detail)
                .build();
    }

    private boolean isWithinSplitThreshold(Long amount) {
        if (amount == null) {
            return false;
        }
        return amount >= SPLIT_THRESHOLD_LOW && amount <= SPLIT_THRESHOLD_HIGH;
    }

    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
