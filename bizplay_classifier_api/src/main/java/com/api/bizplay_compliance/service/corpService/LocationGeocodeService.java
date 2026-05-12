package com.api.bizplay_compliance.service.corpService;

public interface LocationGeocodeService {

    GeocodedLocation geocode(String query);

    record GeocodedLocation(
            String query,
            String roadAddress,
            String jibunAddress,
            double latitude,
            double longitude
    ) {
    }
}
