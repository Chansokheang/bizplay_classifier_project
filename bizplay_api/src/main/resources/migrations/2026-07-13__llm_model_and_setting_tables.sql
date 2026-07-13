-- =============================================================================
-- Migration: LLM model management tables
-- Date:      2026-07-13
--
-- WHY
--   The conversational module gained runtime LLM selection + management:
--     * conversational_llm_setting  — single row: which model the agents use.
--     * conversational_llm_model     — DB-managed model definitions (add/update/
--                                      delete via /api/v1/agent-conversations/llm-models).
--   These are MyBatis-mapped (not JPA entities), so `ddl-auto=update` will NOT
--   create them, and schemaV2.sql only runs on a fresh Postgres volume. On an
--   existing database these tables must be created explicitly — that's this script.
--
-- SAFETY
--   * Idempotent: CREATE TABLE IF NOT EXISTS — safe to re-run.
--   * Non-destructive: creates empty tables only; no existing data is touched.
--   * No app downtime required; run before (or with) deploying the new build.
-- =============================================================================

BEGIN;

-- Which LLM the conversational sub-agents use. NULL active_model = each agent falls
-- back to its own configured default (app.conversational.<agent>.model).
CREATE TABLE IF NOT EXISTS conversational_llm_setting (
    id           SMALLINT PRIMARY KEY DEFAULT 1,
    active_model VARCHAR(100),
    updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_conversational_llm_setting_singleton CHECK (id = 1)
);

-- Runtime-managed LLM model definitions, loaded into the ChatClient registry at
-- startup and on each change (alongside the static app.llm.models[*] config entries).
CREATE TABLE IF NOT EXISTS conversational_llm_model (
    name             VARCHAR(100) PRIMARY KEY,
    label            VARCHAR(255),
    base_url         VARCHAR(500) NOT NULL,
    api_key          TEXT,
    auth_scheme      VARCHAR(50)  NOT NULL DEFAULT 'bearer',
    api_key_header   VARCHAR(50)  NOT NULL DEFAULT 'bearer',
    completions_path VARCHAR(255) NOT NULL DEFAULT '/chat/completions',
    model            VARCHAR(255) NOT NULL,
    temperature      DOUBLE PRECISION NOT NULL DEFAULT 0,
    max_tokens       INTEGER NOT NULL DEFAULT 2048,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_date     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date     TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMIT;
