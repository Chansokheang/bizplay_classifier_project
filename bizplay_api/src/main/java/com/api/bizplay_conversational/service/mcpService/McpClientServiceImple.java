package com.api.bizplay_conversational.service.mcpService;

import com.api.bizplay_conversational.model.entity.ConversationalMcpServer;
import com.api.bizplay_conversational.repository.ConversationalMcpServerRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal MCP client — JSON-RPC 2.0 over Streamable HTTP (single endpoint, POST; the response
 * body may be plain JSON or an SSE stream whose data lines carry the JSON-RPC response). Kept
 * dependency-free on purpose: initialize / tools list / tools call is all the agents need.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientServiceImple implements McpClientService {

    private static final int MAX_RESPONSE_CHARS = 20_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(12);
    private static final long TOOLS_CACHE_MS = 60_000;

    private final ConversationalMcpServerRepo serverRepo;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** SSRF guard: private/loopback targets are refused unless explicitly allowed (local dev). */
    @Value("${app.conversational.mcp.allow-private:false}")
    private boolean allowPrivateTargets;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build();

    private final AtomicLong rpcId = new AtomicLong(1);

    private record ToolsCacheEntry(List<McpTool> tools, long expiresAt) {
    }

    private final Map<String, ToolsCacheEntry> toolsCache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS conversational_mcp_server (
                        corp_no      VARCHAR(50)  NOT NULL,
                        name         VARCHAR(100) NOT NULL,
                        url          VARCHAR(500) NOT NULL,
                        auth_header  VARCHAR(500),
                        trusted      BOOLEAN NOT NULL DEFAULT FALSE,
                        enabled      BOOLEAN NOT NULL DEFAULT TRUE,
                        created_date TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (corp_no, name)
                    )""");
        } catch (Exception e) {
            log.warn("Could not bootstrap conversational_mcp_server ({}).", e.getMessage());
        }
    }

    // --- CRUD ---------------------------------------------------------------------

    @Override
    public List<ConversationalMcpServer> list(String corpNo) {
        requireCorp(corpNo);
        return serverRepo.findAll(corpNo.trim());
    }

    @Override
    public ConversationalMcpServer put(String corpNo, String name, String url, String authHeader,
                                       Boolean trusted, Boolean enabled) {
        requireCorp(corpNo);
        if (name == null || !name.matches("[A-Za-z0-9_-]{2,50}")) {
            throw new IllegalArgumentException("name must be 2-50 letters/digits/dashes (it becomes the mcp:<name>:* prefix).");
        }
        validateUrl(url);
        ConversationalMcpServer s = new ConversationalMcpServer();
        s.setCorpNo(corpNo.trim());
        s.setName(name.trim());
        s.setUrl(url.trim());
        s.setAuthHeader(authHeader == null || authHeader.isBlank() ? null : authHeader.trim());
        s.setTrusted(trusted != null && trusted);
        s.setEnabled(enabled == null || enabled);
        serverRepo.upsert(s);
        toolsCache.remove(corpNo.trim());
        log.info("MCP server '{}' saved for corp {} (trusted={}, enabled={}).", name, corpNo, s.isTrusted(), s.isEnabled());
        return serverRepo.findByName(corpNo.trim(), name.trim());
    }

    @Override
    public void delete(String corpNo, String name) {
        requireCorp(corpNo);
        serverRepo.deleteByName(corpNo.trim(), name);
        toolsCache.remove(corpNo.trim());
    }

    // --- protocol -----------------------------------------------------------------

    @Override
    public TestResult test(String corpNo, String name) {
        ConversationalMcpServer s = serverRepo.findByName(corpNo.trim(), name);
        if (s == null) {
            return new TestResult(false, null, List.of(), "Unknown MCP server: '" + name + "'.");
        }
        try {
            validateUrl(s.getUrl());
            Session session = initialize(s);
            List<McpTool> tools = toolsList(s, session);
            toolsCache.remove(corpNo.trim());
            return new TestResult(true, session.serverInfo(), tools, null);
        } catch (Exception e) {
            return new TestResult(false, null, List.of(), rootMessage(e));
        }
    }

    @Override
    public List<McpTool> listTools(String corpNo) {
        if (corpNo == null || corpNo.isBlank()) {
            return List.of();
        }
        String key = corpNo.trim();
        ToolsCacheEntry hit = toolsCache.get(key);
        if (hit != null && hit.expiresAt() > System.currentTimeMillis()) {
            return hit.tools();
        }
        List<McpTool> all = new ArrayList<>();
        try {
            for (ConversationalMcpServer s : serverRepo.findAll(key)) {
                if (!s.isEnabled()) {
                    continue;
                }
                try {
                    validateUrl(s.getUrl());
                    Session session = initialize(s);
                    all.addAll(toolsList(s, session));
                } catch (Exception e) {
                    log.warn("MCP tools/list failed for '{}': {}", s.getName(), rootMessage(e));
                }
            }
        } catch (Exception e) {
            log.warn("MCP server listing failed for corp {}: {}", corpNo, e.getMessage());
        }
        toolsCache.put(key, new ToolsCacheEntry(all, System.currentTimeMillis() + TOOLS_CACHE_MS));
        return all;
    }

    @Override
    public String callTool(String corpNo, String serverName, String toolName, String argumentsJson) {
        ConversationalMcpServer s = serverRepo.findByName(corpNo.trim(), serverName);
        // "TOOL BLOCKED:"/"TOOL FAILED:" prefixes are a contract with the custom-agent
        // executor — it uses them to keep refused calls out of toolsUsed and to stop the
        // model from silently substituting its own knowledge for tool data.
        if (s == null || !s.isEnabled()) {
            return "TOOL FAILED: MCP server '" + serverName + "' is not available.";
        }
        if (!s.isTrusted()) {
            return "TOOL BLOCKED: MCP server '" + serverName + "' is not marked TRUSTED — an admin must "
                    + "enable trust in Settings before its tools can run.";
        }
        try {
            validateUrl(s.getUrl());
            Session session = initialize(s);
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            JsonNode args;
            try {
                args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            } catch (Exception e) {
                args = objectMapper.createObjectNode();
            }
            params.set("arguments", args.isObject() ? args : objectMapper.createObjectNode().set("input", args));
            JsonNode result = rpc(s, session, "tools/call", params);
            // Result content: [{type:"text", text:"..."}, ...] — join the text parts.
            StringBuilder out = new StringBuilder();
            for (JsonNode c : result.path("content")) {
                if ("text".equals(c.path("type").asText())) {
                    out.append(c.path("text").asText()).append('\n');
                }
            }
            if (result.path("isError").asBoolean(false)) {
                return "tool error: " + out.toString().trim();
            }
            String text = out.toString().trim();
            if (text.isBlank()) {
                text = result.toString();
            }
            return text.length() > MAX_RESPONSE_CHARS ? text.substring(0, MAX_RESPONSE_CHARS) + " …(truncated)" : text;
        } catch (Exception e) {
            return "TOOL FAILED: MCP call failed: " + rootMessage(e);
        }
    }

    // --- minimal Streamable-HTTP JSON-RPC -----------------------------------------

    private record Session(String id, String serverInfo) {
    }

    private Session initialize(ConversationalMcpServer s) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2025-03-26");
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode client = objectMapper.createObjectNode();
        client.put("name", "bizplay-agent");
        client.put("version", "1.0");
        params.set("clientInfo", client);
        Rpc rpc = post(s, null, "initialize", params, false);
        String info = rpc.result().path("serverInfo").path("name").asText("MCP server");
        // Fire-and-forget initialized notification (some servers require it before tools/*).
        try {
            post(s, rpc.sessionId(), "notifications/initialized", null, true);
        } catch (Exception ignored) {
            // Notifications are best-effort.
        }
        return new Session(rpc.sessionId(), info);
    }

    private List<McpTool> toolsList(ConversationalMcpServer s, Session session) throws Exception {
        JsonNode result = rpc(s, session, "tools/list", objectMapper.createObjectNode());
        List<McpTool> tools = new ArrayList<>();
        for (JsonNode t : result.path("tools")) {
            tools.add(new McpTool(
                    s.getName(),
                    t.path("name").asText(),
                    t.path("description").asText(""),
                    t.path("inputSchema").isMissingNode() ? "{}" : t.path("inputSchema").toString(),
                    s.isTrusted()));
        }
        return tools;
    }

    private JsonNode rpc(ConversationalMcpServer s, Session session, String method, ObjectNode params) throws Exception {
        return post(s, session == null ? null : session.id(), method, params, false).result();
    }

    private record Rpc(JsonNode result, String sessionId) {
    }

    private Rpc post(ConversationalMcpServer s, String sessionId, String method, ObjectNode params,
                     boolean notification) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        if (!notification) {
            body.put("id", rpcId.getAndIncrement());
        }
        body.put("method", method);
        if (params != null) {
            body.set("params", params);
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(s.getUrl()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (sessionId != null) {
            req.header("Mcp-Session-Id", sessionId);
        }
        if (s.getAuthHeader() != null) {
            req.header("Authorization", s.getAuthHeader());
        }
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        String newSession = res.headers().firstValue("Mcp-Session-Id").orElse(sessionId);
        if (notification) {
            return new Rpc(objectMapper.createObjectNode(), newSession);
        }
        if (res.statusCode() >= 300) {
            throw new IllegalStateException("MCP HTTP " + res.statusCode() + ": " + trim(res.body(), 200));
        }
        JsonNode parsed = parseJsonRpcBody(res.body());
        if (parsed.has("error")) {
            throw new IllegalStateException("MCP error: " + parsed.path("error").path("message").asText(parsed.path("error").toString()));
        }
        return new Rpc(parsed.path("result"), newSession);
    }

    /** The body is either plain JSON or an SSE stream — take the last data: JSON-RPC message. */
    private JsonNode parseJsonRpcBody(String body) throws Exception {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("{")) {
            return objectMapper.readTree(trimmed);
        }
        JsonNode last = null;
        for (String line : trimmed.split("\r?\n")) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (data.startsWith("{")) {
                    JsonNode candidate = objectMapper.readTree(data);
                    if (candidate.has("result") || candidate.has("error")) {
                        last = candidate;
                    }
                }
            }
        }
        if (last == null) {
            throw new IllegalStateException("MCP response was not JSON-RPC (got: " + trim(trimmed, 120) + ")");
        }
        return last;
    }

    /** SSRF guard: http(s) only; private, loopback and link-local targets refused by default. */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required.");
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http(s) MCP URLs are allowed.");
        }
        if (allowPrivateTargets) {
            return;
        }
        try {
            InetAddress addr = InetAddress.getByName(uri.getHost());
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress()) {
                throw new IllegalArgumentException(
                        "MCP URLs must point to a public host (private/loopback addresses are blocked).");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not resolve the MCP host: " + uri.getHost());
        }
    }

    private static void requireCorp(String corpNo) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
    }

    private static String trim(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) + "…" : s);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
