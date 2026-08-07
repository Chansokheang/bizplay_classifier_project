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

    /** The corp's own BizPlay endpoint when configured (Settings > Integrations), else config. */
    private String baseUrl() {
        String url = agentPromptService.resolve(BASE_URL_SETTING, properties.getBaseUrl());
        return url == null ? properties.getBaseUrl() : url.trim().replaceAll("/+$", "");
    }

    /** Per-corp settings rows that override the configured base URL / draft product code. */
    static final String BASE_URL_SETTING = "setting:bizplay-base-url";
    static final String PRODUCT_CODE_SETTING = "setting:bizplay-product-code";

    /** The corp's draft-save product segment (/api/v2/approval/{code}/bstr/plan/draft). */
    private String productCode() {
        String code = agentPromptService.resolve(PRODUCT_CODE_SETTING, properties.getProductCode());
        return code == null || code.isBlank() ? properties.getProductCode() : code.trim();
    }

    private final BizplayProperties properties;
    private final com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** Tiny TTL cache: catalogs/papers are corp master data and change rarely. */
    private record CacheEntry(JsonNode body, long expiresAtMillis) {
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public BizplayGatewayServiceImple(BizplayProperties properties, ObjectMapper objectMapper,
                                      com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.agentPromptService = agentPromptService;
        agentPromptService.registerDefault(BASE_URL_SETTING, properties.getBaseUrl());
        agentPromptService.registerDefault(PRODUCT_CODE_SETTING, properties.getProductCode());
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
        String url = baseUrl() + "/api/v2/bstrPurpose/corporation-user/"
                + corpUserId.trim() + "/BSTR_PLAN";
        return getCached("catalog:" + corpUserId, url, token);
    }

    @Override
    public JsonNode getPapers(long purposeId, Long segmentId, String token) {
        String query = segmentId != null ? "?segmentId=" + segmentId : "";
        // Discovery call: the untyped path returns the UNION of this purpose's papers, each
        // carrying its own bstrType (DOMESTIC | OVERSEA).
        JsonNode papers = getCached("papers:" + purposeId + ":" + segmentId,
                baseUrl() + "/api/v2/paper/purpose/" + purposeId + query, token);
        // The typed path is what the real UI calls: it FILTERS to that trip area's papers and
        // fills in area-dependent configuration the untyped variant leaves null (period-time
        // rules, route reservation settings). Take the type from the plan paper's own answer —
        // never guess it, since a wrong type returns a different paper set entirely.
        String bstrType = planPaperBstrType(papers);
        if (bstrType == null) {
            return papers;
        }
        try {
            JsonNode typed = getCached("papers:" + bstrType + ":" + purposeId + ":" + segmentId,
                    baseUrl() + "/api/v2/paper/purpose/" + bstrType + "/" + purposeId + query, token);
            return planPaperBstrType(typed) != null ? typed : papers;
        } catch (RuntimeException e) {
            log.warn("Typed paper lookup failed for {}/{} — using the untyped definition: {}",
                    bstrType, purposeId, e.getMessage());
            return papers;
        }
    }

    /** bstrType of the BSTR_PLAN paper in a papers array; null when there is no plan paper. */
    private String planPaperBstrType(JsonNode papers) {
        if (papers == null || !papers.isArray()) {
            return null;
        }
        for (JsonNode paper : papers) {
            if ("BSTR_PLAN".equals(paper.path("paperKind").path("paperKindType").asText())) {
                String type = paper.path("bstrType").asText(null);
                return (type == null || type.isBlank()) ? null : type;
            }
        }
        return null;
    }

    @Override
    public JsonNode getCorporationUsers(long corporationId, String token) {
        String url = baseUrl() + "/api/v2/popup/user/all/" + corporationId;
        return getCached("users:" + corporationId, url, token);
    }

    @Override
    public JsonNode getUser(long corporationUserId, String token) {
        String url = baseUrl() + "/api/v2/eacc-user/" + corporationUserId;
        return getCached("user:" + corporationUserId, url, token);
    }

    @Override
    public String postPlanDraft(JsonNode documents, String token) {
        if (documents == null || !documents.isArray() || documents.isEmpty()) {
            throw new IllegalArgumentException("The draft has no documents to save.");
        }
        String url = baseUrl() + "/api/v2/approval/" + productCode() + "/bstr/plan/draft";
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

    @Override
    public JsonNode getPlanList(long travelerId, String startDate, String endDate, String token) {
        String url = baseUrl() + "/api/v2/approval/bstr/plan/list?travelerId=" + travelerId
                + "&searchPeriodType=BSTR_START_DATE&startDate=" + startDate + "&endDate=" + endDate;
        // Not cached: the list changes as plans are drafted; the pick must see fresh rows.
        return get(url, token);
    }

    @Override
    public JsonNode getPlanDetail(long approvalId, String token) {
        String url = baseUrl() + "/api/v2/approval/bstr/" + approvalId;
        return get(url, token);
    }

    @Override
    public JsonNode getUnattachedReceipts(long corpUserId, String startDate, String endDate,
                                          java.util.List<String> cardTypes, String token) {
        String types = (cardTypes == null || cardTypes.isEmpty())
                ? "CORP" : String.join("%2C", cardTypes);
        String url = baseUrl() + "/api/v2/receipt/" + properties.getReceiptProductCode() + "/not-attached/stream"
                + "?startDate=" + startDate + "&endDate=" + endDate
                + "&receiptStatusTypeList=YET%2CISSUED"
                + "&corpUserIds=" + corpUserId
                + "&cardTypeList=" + types
                + "&excludeExpenseAttached=true";
        String bearer = resolveToken(token);
        try {
            String raw = restClient.get()
                    .uri(java.net.URI.create(url))   // pre-encoded commas must not be re-encoded
                    .header("accept", "*/*")
                    .header("X-RR-MODE", "NONE")
                    .header("Authorization", "Bearer " + bearer)
                    .retrieve()
                    .body(String.class);
            // The endpoint streams CONCATENATED root-level JSON objects — read them in sequence.
            com.fasterxml.jackson.databind.node.ArrayNode receipts = objectMapper.createArrayNode();
            if (raw != null && !raw.isBlank()) {
                try (com.fasterxml.jackson.core.JsonParser p = objectMapper.getFactory().createParser(raw)) {
                    java.util.Iterator<JsonNode> it = objectMapper.readValues(p, JsonNode.class);
                    while (it.hasNext()) {
                        JsonNode n = it.next();
                        if (n != null && n.isObject()) {
                            receipts.add(n);
                        } else if (n != null && n.isArray()) {
                            receipts.addAll((com.fasterxml.jackson.databind.node.ArrayNode) n);
                        }
                    }
                }
            }
            return receipts;
        } catch (Exception e) {
            log.warn("BizPlay receipt stream failed: GET {} -> {}", url, e.getMessage());
            throw new IllegalStateException("BizPlay receipt lookup failed: " + rootMessage(e));
        }
    }

    // --- internals ---------------------------------------------------------------

    private JsonNode getCached(String cacheKey, String url, String token) {
        cacheKey = cacheKey + "@" + url;   // per-corp base URLs must never share cache entries
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
