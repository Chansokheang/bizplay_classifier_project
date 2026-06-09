-- Production-safe patch for deployed code that inserts conversational_trip_plan.user_req_id.
-- Safe to run multiple times. Does not drop/truncate/delete data.

BEGIN;

ALTER TABLE conversational_trip_plan
    ADD COLUMN IF NOT EXISTS user_req_id VARCHAR(100);

UPDATE conversational_trip_plan
   SET user_req_id = 'MIGRATED-' || id::text
 WHERE user_req_id IS NULL
    OR user_req_id = '';

ALTER TABLE conversational_trip_plan
    ALTER COLUMN user_req_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_user_req_id
    ON conversational_trip_plan(user_req_id);

COMMIT;
