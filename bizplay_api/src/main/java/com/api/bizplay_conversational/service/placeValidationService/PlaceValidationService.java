package com.api.bizplay_conversational.service.placeValidationService;

/**
 * Korea-only destination validation for DOMESTIC (국내) trips. Deterministic gazetteer of
 * Korean regions first (zero latency), then the Naver geocoder already integrated in the
 * compliance module as the authority. Never blocks the flow — an unknown place only yields
 * a warning the orchestrator appends to the reply.
 */
public interface PlaceValidationService {

    Result validateKorean(String destination);

    record Result(Status status, String normalized) {
        public enum Status {
            /** Recognized as a Korean place (gazetteer hit or geocoder result). */
            VALID,
            /** The geocoder found nothing — likely a typo or not a Korean place. */
            UNKNOWN,
            /** Validation unavailable (geocoder unreachable / not configured) — stay silent. */
            SKIPPED
        }
    }
}
