package com.api.bizplay_chatbot.rag.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Cross-encoder reranker that re-scores retrieved chunks against the original query.
 * Uses the BAAI/bge-reranker-v2-m3 model served via a /v1/rerank-compatible API
 * (e.g., vLLM, TEI, or Infinity).
 *
 * Pipeline: vector search (fast, approximate) → reranker (slow, precise) → top-N to LLM.
 * This significantly improves context quality over embedding distance alone.
 */
@Slf4j
@Service
public class RerankerService {

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    public RerankerService(
            @Value("${app.reranker.base-url:}") String baseUrl,
            @Value("${app.reranker.api-key:}") String apiKey,
            @Value("${app.reranker.model:}") String model,
            @Value("${app.reranker.enabled:false}") boolean enabled) {
        this.model = model;
        this.enabled = enabled;

        // Build RestClient only if reranker is enabled and configured.
        // Force HTTP/1.1 — vLLM's uvicorn drops POST bodies over HTTP/2.
        if (enabled && !baseUrl.isBlank()) {
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
            requestFactory.setReadTimeout(java.time.Duration.ofSeconds(30));
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(requestFactory);
            if (!apiKey.isBlank()) {
                builder.defaultHeader("Authorization", "Bearer " + apiKey);
            }
            this.restClient = builder.build();
        } else {
            this.restClient = null;
        }
    }

    public boolean isEnabled() {
        return enabled && restClient != null;
    }

    /**
     * Rerank retrieved chunks by cross-encoder relevance to the query.
     * Calls POST /v1/rerank with query + document texts, then reorders
     * the original Document list by descending relevance score.
     *
     * @param query  the user's original question
     * @param chunks candidate chunks from vector similarity search
     * @param topN   number of top-scoring chunks to return after reranking
     * @return reranked and trimmed list of Documents (best first)
     */
    public List<Document> rerank(String query, List<Document> chunks, int topN) {
        if (!isEnabled() || chunks.size() <= 1) {
            return chunks.stream().limit(topN).toList();
        }

        List<String> documents = chunks.stream()
                .map(Document::getText)
                .toList();

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "query", query,
                    "documents", documents,
                    "top_n", topN
            );

            // Response format: { "results": [ { "index": 0, "relevance_score": 0.95 }, ... ] }
            Map<String, Object> response = restClient.post()
                    .uri("/v1/rerank")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !response.containsKey("results")) {
                log.warn("Reranker returned unexpected response, falling back to original order");
                return chunks.stream().limit(topN).toList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

            // Log raw reranker scores for debugging score consistency
            log.info("Reranker raw scores (top_n={}): {}", topN,
                    results.stream()
                            .map(r -> String.format("idx=%d score=%.4f",
                                    ((Number) r.get("index")).intValue(),
                                    ((Number) r.get("relevance_score")).doubleValue()))
                            .toList());

            // Sort by relevance_score descending, map index back to original chunks,
            // and store the reranker score in metadata for source attribution.
            return results.stream()
                    .sorted(Comparator.comparingDouble(
                            (Map<String, Object> r) -> ((Number) r.get("relevance_score")).doubleValue()).reversed())
                    .map(r -> {
                        int index = ((Number) r.get("index")).intValue();
                        double score = ((Number) r.get("relevance_score")).doubleValue();
                        Document doc = chunks.get(index);
                        doc.getMetadata().put("rerank_score", score);
                        return doc;
                    })
                    .limit(topN)
                    .toList();

        } catch (Exception e) {
            // Graceful degradation: if reranker fails, fall back to vector similarity order
            log.error("Reranker call failed, falling back to original order: {}", e.getMessage());
            return chunks.stream().limit(topN).toList();
        }
    }
}
