-- =============================================================================
-- Migration: make conversational_traveler route columns NULLABLE
-- Date:      2026-07-13
--
-- WHY
--   A trip-plan traveler can be created with a PARTIAL route (e.g. origin +
--   destination but no return point). The columns were NOT NULL, so inserting
--   such a traveler failed:
--     ERROR: null value in column "return_point" violates not-null constraint
--   Route fields are optional, so they are made nullable.
--
-- SAFETY
--   * Metadata-only (DROP NOT NULL) — fast, brief ACCESS EXCLUSIVE lock, no rewrite.
--   * Idempotent: DROP NOT NULL on an already-nullable column is a no-op.
--   * Non-destructive.
--
-- ROLLBACK (only if no NULLs have been written since):
--   ALTER TABLE conversational_traveler ALTER COLUMN origin_location      SET NOT NULL;
--   ALTER TABLE conversational_traveler ALTER COLUMN destination_location SET NOT NULL;
--   ALTER TABLE conversational_traveler ALTER COLUMN return_point         SET NOT NULL;
-- =============================================================================

BEGIN;

ALTER TABLE conversational_traveler ALTER COLUMN origin_location      DROP NOT NULL;
ALTER TABLE conversational_traveler ALTER COLUMN destination_location DROP NOT NULL;
ALTER TABLE conversational_traveler ALTER COLUMN return_point         DROP NOT NULL;

COMMIT;
