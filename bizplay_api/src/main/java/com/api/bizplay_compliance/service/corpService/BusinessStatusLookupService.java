package com.api.bizplay_compliance.service.corpService;

public interface BusinessStatusLookupService {

    BusinessStatus lookupBusinessStatus(String businessNumber);

    record BusinessStatus(
            String businessNumber,
            String businessStatus,
            String businessStatusCode,
            String taxType,
            String endDate
    ) {
        public boolean isRegistered() {
            return taxType == null || !taxType.contains("등록되지 않은");
        }

        public boolean isInactive() {
            return hasText(endDate)
                    || containsAny(businessStatus, "폐업", "휴업")
                    || containsAny(taxType, "폐업", "휴업");
        }

        private static boolean containsAny(String value, String... candidates) {
            if (!hasText(value)) {
                return false;
            }

            for (String candidate : candidates) {
                if (value.contains(candidate)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
