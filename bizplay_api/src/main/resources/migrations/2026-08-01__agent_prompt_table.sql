-- =============================================================================
-- Migration: per-agent custom prompt table (saved per corp)
-- Date:      2026-08-01
--
-- WHY
--   Sub-agent system prompts (and the chat starter message / example prompts) are
--   customizable at runtime via /api/v1/agent-conversations/agent-prompts, scoped
--   by corpNo: key = (corp_no, name). A row overrides the built-in default compiled
--   into the agent for that corp; deleting the row restores it.
--
-- SAFETY
--   * Idempotent: CREATE TABLE IF NOT EXISTS — safe to re-run.
--   * The app also self-bootstraps this table at startup (and upgrades a pre-tenant
--     single-key table in place, assigning existing rows to corp 1234567890), so
--     running this script manually is only needed for fresh schemaV2 databases.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS conversational_agent_prompt (
    corp_no      VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    prompt       TEXT NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (corp_no, name)
);

COMMIT;
