-- =============================================================================
-- Migration: make expense date columns NULLABLE
-- Date:      2026-06-29
-- Author:    compliance / R10 date-alignment fix
--
-- WHY
--   Report creation used to default a missing receipt date to LocalDate.now()
--   (today), so an unreadable date silently became "today" and tripped the R10
--   date-alignment audit as a false out-of-period failure. The app now stores
--   NULL for a missing date (and R10 skips null-dated lines), so these columns
--   must allow NULL.
--
-- AFFECTED COLUMNS
--   conversational_cost_expense.start_date
--   conversational_cost_expense.end_date
--   conversational_cost_expense.evidence_date
--   conversational_transportation_expense.usage_date
--
-- SAFETY
--   * DROP NOT NULL is a metadata-only change (no table rewrite); it is fast.
--   * It takes a brief ACCESS EXCLUSIVE lock per table — run during a quiet
--     window if the tables are hot.
--   * Idempotent: re-running on an already-nullable column is a harmless no-op.
--   * Non-destructive: no data is read, changed, or deleted.
--   * The existing date CHECK constraint already tolerates NULL, so nothing
--     else needs to change.
--
-- ROLLBACK (only if NO null values have been written since — a NOT NULL re-add
-- fails if any NULLs exist):
--   ALTER TABLE conversational_cost_expense           ALTER COLUMN start_date    SET NOT NULL;
--   ALTER TABLE conversational_cost_expense           ALTER COLUMN end_date      SET NOT NULL;
--   ALTER TABLE conversational_cost_expense           ALTER COLUMN evidence_date SET NOT NULL;
--   ALTER TABLE conversational_transportation_expense ALTER COLUMN usage_date    SET NOT NULL;
-- =============================================================================

BEGIN;

ALTER TABLE conversational_cost_expense           ALTER COLUMN start_date    DROP NOT NULL;
ALTER TABLE conversational_cost_expense           ALTER COLUMN end_date      DROP NOT NULL;
ALTER TABLE conversational_cost_expense           ALTER COLUMN evidence_date DROP NOT NULL;
ALTER TABLE conversational_transportation_expense ALTER COLUMN usage_date    DROP NOT NULL;

-- Verify: all four should report is_nullable = 'YES'.
SELECT table_name, column_name, is_nullable
FROM information_schema.columns
WHERE table_name IN ('conversational_cost_expense', 'conversational_transportation_expense')
  AND column_name IN ('start_date', 'end_date', 'evidence_date', 'usage_date')
ORDER BY table_name, column_name;

COMMIT;
