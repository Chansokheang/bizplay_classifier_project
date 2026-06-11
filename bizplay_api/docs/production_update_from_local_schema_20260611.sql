-- Production-safe additive schema update generated from local vs production comparison.
-- Date: 2026-06-11
-- Scope: add local tables missing in production without dropping/truncating existing data.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- compliance_audit
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_audit (
    id UUID DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50),
    trip_plan_id UUID,
    report_id UUID,
    compliance_status VARCHAR(20),
    confidence_level VARCHAR(20),
    rules_json JSONB DEFAULT '[]'::jsonb,
    created_date TIMESTAMP DEFAULT NOW()
);

ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS corp_no VARCHAR(50);
ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS trip_plan_id UUID;
ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS report_id UUID;
ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS compliance_status VARCHAR(20);
ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS confidence_level VARCHAR(20);
ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS rules_json JSONB DEFAULT '[]'::jsonb;
ALTER TABLE compliance_audit ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

UPDATE compliance_audit SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE compliance_audit SET rules_json = '[]'::jsonb WHERE rules_json IS NULL;
UPDATE compliance_audit SET created_date = NOW() WHERE created_date IS NULL;

ALTER TABLE compliance_audit ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE compliance_audit ALTER COLUMN rules_json SET DEFAULT '[]'::jsonb;
ALTER TABLE compliance_audit ALTER COLUMN created_date SET DEFAULT NOW();
ALTER TABLE compliance_audit ALTER COLUMN id SET NOT NULL;
ALTER TABLE compliance_audit ALTER COLUMN rules_json SET NOT NULL;
ALTER TABLE compliance_audit ALTER COLUMN created_date SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM compliance_audit WHERE corp_no IS NULL) THEN
        ALTER TABLE compliance_audit ALTER COLUMN corp_no SET NOT NULL;
    ELSE
        RAISE NOTICE 'compliance_audit.corp_no has NULL rows; leaving nullable to avoid failing migration.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM compliance_audit WHERE trip_plan_id IS NULL) THEN
        ALTER TABLE compliance_audit ALTER COLUMN trip_plan_id SET NOT NULL;
    ELSE
        RAISE NOTICE 'compliance_audit.trip_plan_id has NULL rows; leaving nullable to avoid failing migration.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- conversational_trip_report_detail
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversational_trip_report_detail (
    id UUID DEFAULT gen_random_uuid(),
    trip_report_id UUID,
    section_code VARCHAR(30),
    transportation_expense_id UUID,
    cost_expense_id UUID,
    conversational_attachment_id UUID,
    created_date TIMESTAMP DEFAULT NOW()
);

ALTER TABLE conversational_trip_report_detail ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_trip_report_detail ADD COLUMN IF NOT EXISTS trip_report_id UUID;
ALTER TABLE conversational_trip_report_detail ADD COLUMN IF NOT EXISTS section_code VARCHAR(30);
ALTER TABLE conversational_trip_report_detail ADD COLUMN IF NOT EXISTS transportation_expense_id UUID;
ALTER TABLE conversational_trip_report_detail ADD COLUMN IF NOT EXISTS cost_expense_id UUID;
ALTER TABLE conversational_trip_report_detail ADD COLUMN IF NOT EXISTS conversational_attachment_id UUID;
ALTER TABLE conversational_trip_report_detail ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

UPDATE conversational_trip_report_detail SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_trip_report_detail SET created_date = NOW() WHERE created_date IS NULL;

ALTER TABLE conversational_trip_report_detail ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_trip_report_detail ALTER COLUMN created_date SET DEFAULT NOW();
ALTER TABLE conversational_trip_report_detail ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_trip_report_detail ALTER COLUMN created_date SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM conversational_trip_report_detail WHERE trip_report_id IS NULL) THEN
        ALTER TABLE conversational_trip_report_detail ALTER COLUMN trip_report_id SET NOT NULL;
    ELSE
        RAISE NOTICE 'conversational_trip_report_detail.trip_report_id has NULL rows; leaving nullable to avoid failing migration.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM conversational_trip_report_detail WHERE section_code IS NULL) THEN
        ALTER TABLE conversational_trip_report_detail ALTER COLUMN section_code SET NOT NULL;
    ELSE
        RAISE NOTICE 'conversational_trip_report_detail.section_code has NULL rows; leaving nullable to avoid failing migration.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Primary keys, checks, and foreign keys. Guarded for idempotency.
-- Foreign keys/checks use NOT VALID so old production data is not scanned during deploy;
-- new rows are still checked.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'compliance_audit_pkey') THEN
        ALTER TABLE compliance_audit ADD CONSTRAINT compliance_audit_pkey PRIMARY KEY (id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'conversational_trip_report_detail_pkey') THEN
        ALTER TABLE conversational_trip_report_detail ADD CONSTRAINT conversational_trip_report_detail_pkey PRIMARY KEY (id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_detail_section') THEN
        ALTER TABLE conversational_trip_report_detail
            ADD CONSTRAINT ck_conversational_trip_report_detail_section
            CHECK (section_code IN ('COST', 'TRANSPORTATION', 'ETC')) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_detail_expense_ref') THEN
        ALTER TABLE conversational_trip_report_detail
            ADD CONSTRAINT ck_conversational_trip_report_detail_expense_ref
            CHECK (
                (section_code = 'TRANSPORTATION' AND transportation_expense_id IS NOT NULL AND cost_expense_id IS NULL)
                OR (section_code IN ('COST', 'ETC') AND cost_expense_id IS NOT NULL AND transportation_expense_id IS NULL)
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'compliance_audit_trip_plan_id_fkey') THEN
        ALTER TABLE compliance_audit
            ADD CONSTRAINT compliance_audit_trip_plan_id_fkey
            FOREIGN KEY (trip_plan_id) REFERENCES conversational_trip_plan(id)
            ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'compliance_audit_report_id_fkey') THEN
        ALTER TABLE compliance_audit
            ADD CONSTRAINT compliance_audit_report_id_fkey
            FOREIGN KEY (report_id) REFERENCES conversational_trip_report(id)
            ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'conversational_trip_report_detail_trip_report_id_fkey') THEN
        ALTER TABLE conversational_trip_report_detail
            ADD CONSTRAINT conversational_trip_report_detail_trip_report_id_fkey
            FOREIGN KEY (trip_report_id) REFERENCES conversational_trip_report(id)
            ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'conversational_trip_report_detail_cost_expense_id_fkey') THEN
        ALTER TABLE conversational_trip_report_detail
            ADD CONSTRAINT conversational_trip_report_detail_cost_expense_id_fkey
            FOREIGN KEY (cost_expense_id) REFERENCES conversational_cost_expense(id)
            ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'conversational_trip_report_detai_transportation_expense_id_fkey') THEN
        ALTER TABLE conversational_trip_report_detail
            ADD CONSTRAINT conversational_trip_report_detai_transportation_expense_id_fkey
            FOREIGN KEY (transportation_expense_id) REFERENCES conversational_transportation_expense(id)
            ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'conversational_trip_report_de_conversational_attachment_id_fkey') THEN
        ALTER TABLE conversational_trip_report_detail
            ADD CONSTRAINT conversational_trip_report_de_conversational_attachment_id_fkey
            FOREIGN KEY (conversational_attachment_id) REFERENCES conversational_attachment(id)
            ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Indexes from local schema.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_compliance_audit_corp_no
    ON compliance_audit(corp_no);

CREATE INDEX IF NOT EXISTS idx_compliance_audit_trip_plan
    ON compliance_audit(trip_plan_id);

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_report
    ON conversational_trip_report_detail(trip_report_id);

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_transportation
    ON conversational_trip_report_detail(transportation_expense_id);

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_cost
    ON conversational_trip_report_detail(cost_expense_id);

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_attachment
    ON conversational_trip_report_detail(conversational_attachment_id);

-- ---------------------------------------------------------------------------
-- Final audit: fail if production is still missing the local tables/columns.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    missing_count integer;
BEGIN
    WITH expected(table_name, column_name) AS (
        VALUES
            ('compliance_audit', 'id'),
            ('compliance_audit', 'corp_no'),
            ('compliance_audit', 'trip_plan_id'),
            ('compliance_audit', 'report_id'),
            ('compliance_audit', 'compliance_status'),
            ('compliance_audit', 'confidence_level'),
            ('compliance_audit', 'rules_json'),
            ('compliance_audit', 'created_date'),
            ('conversational_trip_report_detail', 'id'),
            ('conversational_trip_report_detail', 'trip_report_id'),
            ('conversational_trip_report_detail', 'section_code'),
            ('conversational_trip_report_detail', 'transportation_expense_id'),
            ('conversational_trip_report_detail', 'cost_expense_id'),
            ('conversational_trip_report_detail', 'conversational_attachment_id'),
            ('conversational_trip_report_detail', 'created_date')
    )
    SELECT COUNT(*)
      INTO missing_count
      FROM expected e
      LEFT JOIN information_schema.columns c
        ON c.table_schema = 'public'
       AND c.table_name = e.table_name
       AND c.column_name = e.column_name
     WHERE c.column_name IS NULL;

    IF missing_count > 0 THEN
        RAISE EXCEPTION 'Production schema update failed: % required local columns are still missing', missing_count;
    END IF;
END $$;

COMMIT;
