package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A corp-registered MCP server (Model Context Protocol, Streamable HTTP). Its tools become
 * available to the corp's custom agents as {@code mcp:<server>:<tool>} — but only once an admin
 * marks the server {@code trusted} (tool calls are refused before that; listing is always allowed).
 */
@Getter
@Setter
@NoArgsConstructor
public class ConversationalMcpServer {

    private String corpNo;
    private String name;
    private String url;
    /** Optional Authorization header value sent to the server (e.g. "Bearer xyz"). */
    private String authHeader;
    private boolean trusted;
    private boolean enabled;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
