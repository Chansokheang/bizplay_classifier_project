-- Production-safe fix for report header/detail schema split.
-- Current code inserts conversational_trip_report as a header row and stores expense
-- lines in conversational_trip_report_detail. Older production schemas still require
-- conversational_trip_report.section_code, which breaks header inserts.
--
-- Safe to run multiple times. Does not drop/truncate/delete data.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Keep legacy columns if they exist, but make them nullable because they now belong
-- to conversational_trip_report_detail, not the report header.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'conversational_trip_report'
           AND column_name = 'section_code'
    ) THEN
        ALTER TABLE conversational_trip_report ALTER COLUMN section_code DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'conversational_trip_report'
           AND column_name = 'transportation_expense_id'
    ) THEN
        ALTER TABLE conversational_trip_report ALTER COLUMN transportation_expense_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'conversational_trip_report'
           AND column_name = 'cost_expense_id'
    ) THEN
        ALTER TABLE conversational_trip_report ALTER COLUMN cost_expense_id DROP NOT NULL;
    END IF;
END $$;

-- Ensure the new detail table exists for current code.
CREATE TABLE IF NOT EXISTS conversational_trip_report_detail (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    trip_report_id UUID NOT NULL,
    section_code VARCHAR(30) NOT NULL,
    transportation_expense_id UUID,
    cost_expense_id UUID,
    conversational_attachment_id UUID,
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
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
        RAISE NOTICE 'conversational_trip_report_detail.trip_report_id has NULL rows; leaving nullable.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM conversational_trip_report_detail WHERE section_code IS NULL) THEN
        ALTER TABLE conversational_trip_report_detail ALTER COLUMN section_code SET NOT NULL;
    ELSE
        RAISE NOTICE 'conversational_trip_report_detail.section_code has NULL rows; leaving nullable.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'conversational_trip_report_detail_pkey') THEN
        ALTER TABLE conversational_trip_report_detail
            ADD CONSTRAINT conversational_trip_report_detail_pkey PRIMARY KEY (id);
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

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_report
    ON conversational_trip_report_detail(trip_report_id);

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_transportation
    ON conversational_trip_report_detail(transportation_expense_id);

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_cost
    ON conversational_trip_report_detail(cost_expense_id);

CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_detail_attachment
    ON conversational_trip_report_detail(conversational_attachment_id);

-- Verify the old header column no longer blocks current inserts.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'conversational_trip_report'
           AND column_name = 'section_code'
           AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'conversational_trip_report.section_code is still NOT NULL';
    END IF;
END $$;

COMMIT;
