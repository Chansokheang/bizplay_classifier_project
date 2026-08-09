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
    private final com.api.bizplay_conversational.config.BizplayEndpoints endpoints;
    private final com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /**
     * baseUrl + an endpoint template with its {@code {name}} path variables substituted. Query
     * strings are appended by the caller. Kept tiny on purpose — the templates carry no query part.
     */
    private String buildUrl(String template, Object... pathVars) {
        String path = template;
        for (int i = 0; i + 1 < pathVars.length; i += 2) {
            path = path.replace("{" + pathVars[i] + "}", String.valueOf(pathVars[i + 1]));
        }
        return baseUrl() + path;
    }

    /** Tiny TTL cache: catalogs/papers are corp master data and change rarely. */
    private record CacheEntry(JsonNode body, long expiresAtMillis) {
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public BizplayGatewayServiceImple(BizplayProperties properties,
                                      com.api.bizplay_conversational.config.BizplayEndpoints endpoints,
                                      ObjectMapper objectMapper,
                                      com.api.bizplay_conversational.service.agentPromptService.AgentPromptService agentPromptService) {
        this.properties = properties;
        this.endpoints = endpoints;
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
        String url = buildUrl(endpoints.getPurposeCatalog(),
                "corpUserId", corpUserId.trim(), "paperKindType", "BSTR_PLAN");
        return getCached("catalog:" + corpUserId, url, token);
    }

    @Override
    public JsonNode getPapers(long purposeId, Long segmentId, String token) {
        String query = segmentId != null ? "?segmentId=" + segmentId : "";
        // Discovery call: the untyped path returns the UNION of this purpose's papers, each
        // carrying its own bstrType (DOMESTIC | OVERSEA).
        JsonNode papers = getCached("papers:" + purposeId + ":" + segmentId,
                buildUrl(endpoints.getPapers(), "purposeId", purposeId) + query, token);
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
                    buildUrl(endpoints.getPapersTyped(), "bstrType", bstrType, "purposeId", purposeId)
                            + query, token);
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
        String url = buildUrl(endpoints.getCorporationUsers(), "corporationId", corporationId);
        return getCached("users:" + corporationId, url, token);
    }

    @Override
    public JsonNode getUser(long corporationUserId, String token) {
        String url = buildUrl(endpoints.getUser(), "corporationUserId", corporationUserId);
        return getCached("user:" + corporationUserId, url, token);
    }

    @Override
    public String postPlanDraft(JsonNode documents, String token) {
        if (documents == null || !documents.isArray() || documents.isEmpty()) {
            throw new IllegalArgumentException("The draft has no documents to save.");
        }
        String url = buildUrl(endpoints.getPlanDraft(), "productCode", productCode());
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
    public String postSettlementDraft(JsonNode documents, String token) {
        if (documents == null || !documents.isArray() || documents.isEmpty()) {
            throw new IllegalArgumentException("The settlement draft has no documents to save.");
        }
        // The settlement (정산서) endpoint — deliberately DISTINCT from the plan draft's
        // /bstr/plan/draft. The body here is the 정산서 request-body shape, never the plan shape.
        String url = buildUrl(endpoints.getSettlementDraft(), "productCode", productCode());
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
            log.info("BizPlay settlement draft saved: {}", response);
            return response;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // Surface the provider's real error body (their 500s carry a JSON message).
            log.warn("BizPlay settlement-draft save failed: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IllegalStateException("BizPlay rejected the settlement draft (HTTP "
                    + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("BizPlay settlement-draft save failed: {}", e.getMessage());
            throw new IllegalStateException("BizPlay settlement-draft save failed: " + rootMessage(e));
        }
    }

    @Override
    public java.util.List<Long> postEtcCardReceipts(JsonNode expenses, String token) {
        if (expenses == null || !expenses.isArray() || expenses.isEmpty()) {
            throw new IllegalArgumentException("No expense rows to register.");
        }
        String url = buildUrl(endpoints.getEtcCard());
        String bearer = resolveToken(token);
        try {
            String response = restClient.post()
                    .uri(url)
                    .header("accept", "*/*")
                    .header("X-RR-MODE", "NONE")
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(expenses))
                    .retrieve()
                    .body(String.class);
            java.util.List<Long> ids = new java.util.ArrayList<>();
            JsonNode parsed = objectMapper.readTree(response == null ? "[]" : response);
            for (JsonNode id : parsed) {
                if (id.canConvertToLong()) {
                    ids.add(id.asLong());
                }
            }
            log.info("BizPlay etc-card receipts created: {}", ids);
            return ids;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("BizPlay etc-card create failed: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IllegalStateException("BizPlay rejected the manual expense (HTTP "
                    + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("BizPlay etc-card create failed: {}", e.getMessage());
            throw new IllegalStateException("BizPlay manual expense create failed: " + rootMessage(e));
        }
    }

    @Override
    public long uploadReceiptFile(byte[] content, String filename, String token) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("The receipt image is empty.");
        }
        String url = buildUrl(endpoints.getFileboxUpload());
        String bearer = resolveToken(token);
        // multipart/form-data field name is "multipartFile" (see the provider's OpenAPI).
        org.springframework.util.MultiValueMap<String, Object> form =
                new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource file =
                new org.springframework.core.io.ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        return filename == null || filename.isBlank() ? "receipt" : filename;
                    }
                };
        form.add("multipartFile", file);
        try {
            String response = restClient.post()
                    .uri(url)
                    .header("accept", "*/*")
                    .header("X-RR-MODE", "NONE")
                    .header("Authorization", "Bearer " + bearer)
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode parsed = objectMapper.readTree(response == null ? "[]" : response);
            JsonNode first = parsed.isArray() ? parsed.path(0) : parsed;
            long fileId = first.path("fileId").asLong(0);
            if (fileId <= 0) {
                throw new IllegalStateException("filebox upload returned no fileId: " + response);
            }
            log.info("BizPlay filebox upload ok: fileId={}", fileId);
            return fileId;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("BizPlay filebox upload failed: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IllegalStateException("BizPlay rejected the receipt image (HTTP "
                    + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("BizPlay filebox upload failed: {}", e.getMessage());
            throw new IllegalStateException("BizPlay receipt image upload failed: " + rootMessage(e));
        }
    }

    @Override
    public JsonNode getIssuedReceiptsBulk(java.util.List<Long> receiptIds, String token) {
        if (receiptIds == null || receiptIds.isEmpty()) {
            throw new IllegalArgumentException("No receipt ids to look up.");
        }
        String ids = receiptIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String url = buildUrl(endpoints.getIssuedBulk(), "ids", ids);
        return get(url, token);
    }

    @Override
    public JsonNode getTranKindList(String token) {
        // Company master data — cache it like the paper/catalog lookups.
        return getCached("trankinds", buildUrl(endpoints.getTrankindList()), token);
    }

    @Override
    public String patchEtcReceiptDetail(long receiptId, JsonNode detail, String token) {
        if (detail == null || !detail.isObject()) {
            throw new IllegalArgumentException("Receipt detail is required.");
        }
        String url = buildUrl(endpoints.getReceiptEtcDetail(), "receiptId", receiptId);
        String bearer = resolveToken(token);
        try {
            String response = restClient.patch()
                    .uri(url)
                    .header("accept", "*/*")
                    .header("X-RR-MODE", "NONE")
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(detail))
                    .retrieve()
                    .body(String.class);
            log.info("BizPlay receipt-etc detail updated (receiptId={}): {}", receiptId, response);
            return response;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("receipt-etc detail update failed: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IllegalStateException("BizPlay rejected the receipt detail (HTTP "
                    + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("receipt-etc detail update failed: {}", e.getMessage());
            throw new IllegalStateException("BizPlay receipt detail update failed: " + rootMessage(e));
        }
    }

    @Override
    public JsonNode getPlanList(long travelerId, String startDate, String endDate, String token) {
        String url = buildUrl(endpoints.getPlanList()) + "?travelerId=" + travelerId
                + "&searchPeriodType=BSTR_START_DATE&startDate=" + startDate + "&endDate=" + endDate;
        // Not cached: the list changes as plans are drafted; the pick must see fresh rows.
        return get(url, token);
    }

    @Override
    public JsonNode getPlanDetail(long approvalId, String token) {
        String url = buildUrl(endpoints.getPlanDetail(), "approvalId", approvalId);
        return get(url, token);
    }

    @Override
    public JsonNode getUnattachedReceipts(long corpUserId, String startDate, String endDate,
                                          java.util.List<String> cardTypes,
                                          java.util.List<Long> tranKindIds,
                                          java.util.List<Long> excludeTranKindIds, String token) {
        String types = (cardTypes == null || cardTypes.isEmpty())
                ? "CORP" : String.join("%2C", cardTypes);
        StringBuilder url = new StringBuilder(
                buildUrl(endpoints.getReceiptStream(), "receiptProductCode", properties.getReceiptProductCode()))
                .append("?startDate=").append(startDate).append("&endDate=").append(endDate)
                .append("&receiptStatusTypeList=YET%2CISSUED")
                .append("&corpUserIds=").append(corpUserId)
                .append("&cardTypeList=").append(types)
                .append("&excludeExpenseAttached=true");
        // Section-scoped TranKind filter (from the form's paperItemOrderDto[].itemDto.tranKinds).
        if (tranKindIds != null && !tranKindIds.isEmpty()) {
            url.append("&tranKindIds=").append(tranKindIds.stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining("%2C")));
        }
        if (excludeTranKindIds != null && !excludeTranKindIds.isEmpty()) {
            url.append("&excludeTranKindIds=").append(excludeTranKindIds.stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining("%2C")));
        }
        String bearer = resolveToken(token);
        try {
            String raw = restClient.get()
                    .uri(java.net.URI.create(url.toString()))   // pre-encoded commas must not be re-encoded
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
