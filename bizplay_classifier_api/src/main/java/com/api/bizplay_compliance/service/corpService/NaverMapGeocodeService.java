package com.api.bizplay_compliance.service.corpService;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class NaverMapGeocodeService implements LocationGeocodeService {

    private final RestClient restClient;
    private final String apiKeyId;
    private final String apiKey;

    public NaverMapGeocodeService(
            RestClient.Builder restClientBuilder,
            @Value("${app.naver-geocode.base-url:https://maps.apigw.ntruss.com}") String baseUrl,
            @Value("${app.naver-geocode.api-key-id}") String apiKeyId,
            @Value("${app.naver-geocode.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.apiKeyId = apiKeyId;
        this.apiKey = apiKey;
    }

    @Override
    public GeocodedLocation geocode(String query) {
        NaverGeocodeResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/map-geocode/v2/geocode")
                        .queryParam("query", query)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .header("x-ncp-apigw-api-key-id", apiKeyId)
                .header("x-ncp-apigw-api-key", apiKey)
                .retrieve()
                .body(NaverGeocodeResponse.class);

        if (response == null || response.addresses() == null || response.addresses().isEmpty()) {
            throw new IllegalStateException("Naver geocode response is empty for query: " + query);
        }

        NaverGeocodeAddress firstAddress = response.addresses().get(0);
        return new GeocodedLocation(
                query,
                firstAddress.roadAddress(),
                firstAddress.jibunAddress(),
                parseCoordinate(firstAddress.latitude()),
                parseCoordinate(firstAddress.longitude())
        );
    }

    private double parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Invalid geocode coordinate.");
        }
        return Double.parseDouble(value);
    }

    private record NaverGeocodeResponse(
            String status,
            List<NaverGeocodeAddress> addresses,
            String errorMessage
    ) {
    }

    private record NaverGeocodeAddress(
            @JsonProperty("roadAddress")
            String roadAddress,
            @JsonProperty("jibunAddress")
            String jibunAddress,
            @JsonProperty("y")
            String latitude,
            @JsonProperty("x")
            String longitude
    ) {
    }
}
