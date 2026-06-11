package com.api.bizplay_compliance.service.geoService;

/**
 * Resolves a free-text location (venue, landmark, airport, station, address, or city) to a
 * normalized {@link GeoPoint} (city + country) using an LLM's geographic knowledge. Used as the
 * FALLBACK for R10's location-alignment dimension: the primary path reads the city straight from the
 * attached booking PDF, and this resolver is consulted only when no document carries the city (e.g.
 * a hand-typed plan whose destination is a local venue with no booking attached). Being LLM-backed
 * (not the Korea-only Naver geocode API), it handles Korean and non-Korean locations alike.
 */
public interface GeoNormalizationService {

    /** Never returns null; the returned GeoPoint may have null fields when unresolved. */
    GeoPoint normalize(String location);
}
