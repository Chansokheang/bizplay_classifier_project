package com.api.bizplay_conversational.service.mcpService;

import com.api.bizplay_conversational.model.entity.ConversationalMcpServer;

import java.util.List;

/**
 * Corp-registered MCP servers (Model Context Protocol over Streamable HTTP). Registered servers
 * expose their tools to the corp's custom agents as {@code mcp:<server>:<tool>}; tool calls are
 * refused until an admin marks the server trusted. URLs are SSRF-guarded.
 */
public interface McpClientService {

    List<ConversationalMcpServer> list(String corpNo);

    ConversationalMcpServer put(String corpNo, String name, String url, String authHeader,
                                Boolean trusted, Boolean enabled);

    void delete(String corpNo, String name);

    /** Connection test: MCP initialize + tools/list. Never throws — errors ride the result. */
    TestResult test(String corpNo, String name);

    /** All tools of the corp's ENABLED servers (cached briefly). Failures yield empty lists. */
    List<McpTool> listTools(String corpNo);

    /** Execute one tool. The server must be enabled AND trusted. Returns the text content. */
    String callTool(String corpNo, String serverName, String toolName, String argumentsJson);

    record McpTool(String server, String name, String description, String inputSchemaJson, boolean trusted) {
    }

    record TestResult(boolean ok, String serverInfo, List<McpTool> tools, String error) {
    }
}
