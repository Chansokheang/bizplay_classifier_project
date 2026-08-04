-- =============================================================================
-- Migration: corp-registered MCP servers
-- Date:      2026-08-02
--
-- WHY
--   Corps can connect their own MCP servers (Settings > MCP servers /
--   /api/v1/agent-conversations/mcp-servers). Tools of enabled servers surface in
--   the custom-agent builder as mcp:<server>:<tool>; calls execute only once an
--   admin marks the server TRUSTED. URLs are SSRF-guarded by the application.
--
-- SAFETY
--   * Idempotent: CREATE TABLE IF NOT EXISTS - safe to re-run.
--   * The app also self-bootstraps this table at startup.
-- =============================================================================

BEGIN;

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
);

COMMIT;
