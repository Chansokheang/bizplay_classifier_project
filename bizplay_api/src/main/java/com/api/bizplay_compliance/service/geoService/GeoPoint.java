package com.api.bizplay_compliance.service.geoService;

/**
 * A free-text location resolved to its city and country (English names). Either field may be null
 * when it could not be determined. Used by R10's location-alignment dimension to compare a plan's
 * venue/destination against an expense's printed place on a city/country basis rather than raw text.
 */
public record GeoPoint(String city, String country) {

    public boolean hasCity() {
        return city != null && !city.isBlank();
    }

    public boolean hasCountry() {
        return country != null && !country.isBlank();
    }

    public boolean isEmpty() {
        return !hasCity() && !hasCountry();
    }
}
