package com.api.bizplay_compliance.service.corpService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class NtsBusinessStatusLookupService implements BusinessStatusLookupService {

    private final RestClient restClient;
    private final String serviceKey;

    public NtsBusinessStatusLookupService(
            RestClient.Builder restClientBuilder,
            @Value("${app.nts-businessman.base-url:https://api.odcloud.kr/api/nts-businessman/v1}") String baseUrl,
            @Value("${app.nts-businessman.service-key}") String serviceKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.serviceKey = serviceKey;
    }

    @Override
    public BusinessStatus lookupBusinessStatus(String businessNumber) {
        NtsBusinessStatusResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/status")
                        .queryParam("serviceKey", serviceKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.ALL)
                .body(new NtsBusinessStatusRequest(List.of(businessNumber)))
                .retrieve()
                .body(NtsBusinessStatusResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("NTS businessman status response is empty.");
        }

        NtsBusinessStatusItem item = response.data().get(0);
        return new BusinessStatus(
                item.businessNumber(),
                item.businessStatus(),
                item.businessStatusCode(),
                item.taxType(),
                item.endDate()
        );
    }

    private record NtsBusinessStatusRequest(List<String> b_no) {
    }

    private record NtsBusinessStatusResponse(
            Integer request_cnt,
            String status_code,
            List<NtsBusinessStatusItem> data
    ) {
    }

    private record NtsBusinessStatusItem(
            @com.fasterxml.jackson.annotation.JsonProperty("b_no")
            String businessNumber,
            @com.fasterxml.jackson.annotation.JsonProperty("b_stt")
            String businessStatus,
            @com.fasterxml.jackson.annotation.JsonProperty("b_stt_cd")
            String businessStatusCode,
            @com.fasterxml.jackson.annotation.JsonProperty("tax_type")
            String taxType,
            @com.fasterxml.jackson.annotation.JsonProperty("end_dt")
            String endDate
    ) {
    }
}
