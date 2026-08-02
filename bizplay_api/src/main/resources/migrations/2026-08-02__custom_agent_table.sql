-- =============================================================================
-- Migration: user-defined custom sub-agents (per corp)
-- Date:      2026-08-02
--
-- WHY
--   Admins can create their own sub-agents at runtime (Settings > Custom agents /
--   /api/v1/agent-conversations/custom-agents): a system prompt, a "when to use"
--   routing description, and a read-only tool allowlist (db-select, geocode). The
--   plan-agent chat routes matching messages to them automatically.
--
-- SAFETY
--   * Idempotent: CREATE TABLE IF NOT EXISTS — safe to re-run.
--   * The app also self-bootstraps this table at startup, so running this script
--     manually is only needed for fresh schemaV2-provisioned databases.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS conversational_custom_agent (
    corp_no      VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  TEXT NOT NULL,
    prompt       TEXT NOT NULL,
    model        VARCHAR(100),
    tools        VARCHAR(500) NOT NULL DEFAULT '',
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (corp_no, name)
);

COMMIT;
