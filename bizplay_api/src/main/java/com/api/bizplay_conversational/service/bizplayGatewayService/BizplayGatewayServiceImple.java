package com.api.bizplay_conversational.service.bizplayGatewayService;

import com.api.bizplay_conversational.config.BizplayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BizplayGatewayServiceImple implements BizplayGatewayService {

    private final BizplayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** Tiny TTL cache: catalogs/papers are corp master data and change rarely. */
    private record CacheEntry(JsonNode body, long expiresAtMillis) {
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public BizplayGatewayServiceImple(BizplayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public JsonNode getPurposeCatalog(String corpUserId, String token) {
        if (corpUserId == null || corpUserId.isBlank()) {
            throw new IllegalArgumentException("corpUserId is required.");
        }
        String url = properties.getBaseUrl() + "/api/v2/bstrPurpose/corporation-user/"
                + corpUserId.trim() + "/BSTR_PLAN";
        return getCached("catalog:" + corpUserId, url, token);
    }

    @Override
    public JsonNode getPapers(long purposeId, Long segmentId, String token) {
        String url = properties.getBaseUrl() + "/api/v2/paper/purpose/" + purposeId
                + (segmentId != null ? "?segmentId=" + segmentId : "");
        return getCached("papers:" + purposeId + ":" + segmentId, url, token);
    }

    @Override
    public JsonNode getCorporationUsers(long corporationId, String token) {
        String url = properties.getBaseUrl() + "/api/v2/popup/user/all/" + corporationId;
        return getCached("users:" + corporationId, url, token);
    }

    @Override
    public JsonNode getUser(long corporationUserId, String token) {
        String url = properties.getBaseUrl() + "/api/v2/eacc-user/" + corporationUserId;
        return getCached("user:" + corporationUserId, url, token);
    }

    @Override
    public String postPlanDraft(JsonNode documents, String token) {
        if (documents == null || !documents.isArray() || documents.isEmpty()) {
            throw new IllegalArgumentException("The draft has no documents to save.");
        }
        String url = properties.getBaseUrl() + "/api/v2/approval/bics/bstr/plan/draft";
        String bearer = resolveToken(token);
        try {
            String response = restClient.post()
                    .uri(url)
                    .header("accept", "*/*")
                    .header("X-RR-MODE", "NONE")
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(documents))
                    .retrieve()
                    .body(String.class);
            log.info("BizPlay plan draft saved: {}", response);
            return response;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // Surface the provider's real error body (their 500s carry a JSON message).
            log.warn("BizPlay plan-draft save failed: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IllegalStateException("BizPlay rejected the plan draft (HTTP "
                    + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("BizPlay plan-draft save failed: {}", e.getMessage());
            throw new IllegalStateException("BizPlay plan-draft save failed: " + rootMessage(e));
        }
    }

    // --- internals ---------------------------------------------------------------

    private JsonNode getCached(String cacheKey, String url, String token) {
        CacheEntry hit = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAtMillis() > now) {
            return hit.body();
        }
        JsonNode body = get(url, token);
        cache.put(cacheKey, new CacheEntry(body, now + properties.getCacheTtlSeconds() * 1000));
        return body;
    }

    private JsonNode get(String url, String token) {
        String bearer = resolveToken(token);
        try {
            String raw = restClient.get()
                    .uri(url)
                    .header("accept", "*/*")
                    .header("X-RR-MODE", "NONE")
                    .header("Authorization", "Bearer " + bearer)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw == null ? "null" : raw);
        } catch (Exception e) {
            log.warn("BizPlay API call failed: GET {} -> {}", url, e.getMessage());
            throw new IllegalStateException("BizPlay API call failed: " + rootMessage(e));
        }
    }

    /** Per-request token wins; the configured dev token is a local-testing fallback only. */
    private String resolveToken(String token) {
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String dev = properties.getDevToken();
        if (dev != null && !dev.isBlank()) {
            return dev.trim();
        }
        throw new IllegalArgumentException(
                "No BizPlay token: pass the user's token via the X-Bizplay-Token header (or set app.bizplay.dev-token for local dev).");
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
