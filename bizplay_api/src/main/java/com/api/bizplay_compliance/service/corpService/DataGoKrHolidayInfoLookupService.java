package com.api.bizplay_compliance.service.corpService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataGoKrHolidayInfoLookupService implements HolidayInfoLookupService {

    private static final DateTimeFormatter LOCDATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final String serviceKey;
    private final Map<String, Map<LocalDate, HolidayInfo>> monthlyHolidayCache = new ConcurrentHashMap<>();

    public DataGoKrHolidayInfoLookupService(
            RestClient.Builder restClientBuilder,
            @Value("${app.holiday-api.base-url:http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService}") String baseUrl,
            @Value("${app.holiday-api.service-key}") String serviceKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.serviceKey = serviceKey;
    }

    @Override
    public Optional<HolidayInfo> findHoliday(LocalDate date) {
        String cacheKey = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
        Map<LocalDate, HolidayInfo> monthlyHolidays = monthlyHolidayCache.computeIfAbsent(
                cacheKey,
                ignored -> loadMonthlyHolidays(date.getYear(), date.getMonthValue())
        );

        return Optional.ofNullable(monthlyHolidays.get(date));
    }

    private Map<LocalDate, HolidayInfo> loadMonthlyHolidays(int year, int month) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getHoliDeInfo")
                        .queryParam("solYear", year)
                        .queryParam("solMonth", String.format("%02d", month))
                        .queryParam("_type", "json")
                        .queryParam("ServiceKey", serviceKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return Map.of();
        }

        Object responseNode = response.get("response");
        if (!(responseNode instanceof Map<?, ?> responseMap)) {
            return Map.of();
        }

        Object bodyNode = responseMap.get("body");
        if (!(bodyNode instanceof Map<?, ?> bodyMap)) {
            return Map.of();
        }

        Object itemsNode = bodyMap.get("items");
        if (!(itemsNode instanceof Map<?, ?> itemsMap)) {
            return Map.of();
        }

        Object itemNode = itemsMap.get("item");
        if (itemNode == null) {
            return Map.of();
        }

        Map<LocalDate, HolidayInfo> result = new ConcurrentHashMap<>();

        if (itemNode instanceof List<?> itemList) {
            for (Object entry : itemList) {
                if (entry instanceof Map<?, ?> itemMap) {
                    addHoliday(result, itemMap);
                }
            }
            return result;
        }

        if (itemNode instanceof Map<?, ?> itemMap) {
            addHoliday(result, itemMap);
        }

        return result;
    }

    private void addHoliday(Map<LocalDate, HolidayInfo> target, Map<?, ?> itemMap) {
        String locdate = stringValue(itemMap.get("locdate"));
        String dateName = stringValue(itemMap.get("dateName"));
        String isHoliday = stringValue(itemMap.get("isHoliday"));

        if (locdate == null || !"Y".equalsIgnoreCase(isHoliday)) {
            return;
        }

        LocalDate date = LocalDate.parse(locdate, LOCDATE_FORMATTER);
        target.put(date, new HolidayInfo(
                stringValue(itemMap.get("dateKind")),
                dateName,
                isHoliday,
                Long.parseLong(locdate),
                parseInteger(itemMap.get("seq"), 0)
        ));
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String stringValue) {
            return stringValue;
        }

        if (value instanceof Number numberValue) {
            return String.valueOf(numberValue.longValue());
        }

        if (value instanceof Boolean booleanValue) {
            return String.valueOf(booleanValue);
        }

        if (value instanceof LinkedHashMap<?, ?>) {
            return null;
        }

        return String.valueOf(value);
    }

    private int parseInteger(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Integer.parseInt(stringValue);
        }

        return defaultValue;
    }
}
