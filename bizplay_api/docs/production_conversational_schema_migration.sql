-- Production-safe additive migration for BizPlay conversational schema.
-- Purpose: add missing conversational tables/columns/indexes without dropping data.
-- Safe to run multiple times.
-- Run after taking a pg_dump backup.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- Base conversational tables
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversational_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversational_department_corp_name UNIQUE (corp_no, name)
);

CREATE TABLE IF NOT EXISTS conversational_staff (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES conversational_department(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    position VARCHAR(100),
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversational_staff_department_name_position UNIQUE (department_id, name, position)
);

CREATE TABLE IF NOT EXISTS conversational_agent_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    agent_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'COLLECTING',
    draft_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    chat_event_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_conversational_agent_type CHECK (agent_type IN ('TRIP_PLAN', 'EXPENSE_REPORT')),
    CONSTRAINT ck_conversational_agent_status CHECK (status IN ('COLLECTING', 'READY_FOR_REVIEW', 'APPROVED', 'POSTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS conversational_trip_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    agent_session_id UUID REFERENCES conversational_agent_session(id) ON UPDATE CASCADE ON DELETE SET NULL,
    user_req_id VARCHAR(100) NOT NULL,
    plan_type VARCHAR(255) NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT,
    business_period VARCHAR(100) NOT NULL,
    business_start_date DATE,
    business_end_date DATE,
    destination VARCHAR(255) NOT NULL,
    business_trip_classification VARCHAR(100) NOT NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'Request for approval',
    extras JSONB DEFAULT '{}'::jsonb,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_conversational_trip_plan_approval CHECK (approval_status IN ('Request for approval', 'Business trip cancellation', 'Approval complete')),
    CONSTRAINT ck_conversational_trip_plan_dates CHECK (business_start_date IS NULL OR business_end_date IS NULL OR business_end_date >= business_start_date)
);

CREATE TABLE IF NOT EXISTS conversational_traveler (
    trip_id UUID NOT NULL REFERENCES conversational_trip_plan(id) ON UPDATE CASCADE ON DELETE CASCADE,
    staff_id UUID NOT NULL REFERENCES conversational_staff(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    origin_location VARCHAR(255) NOT NULL,
    destination_location VARCHAR(255) NOT NULL,
    return_point VARCHAR(255) NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (trip_id, staff_id)
);

CREATE TABLE IF NOT EXISTS conversational_cost_expense (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_type VARCHAR(100) NOT NULL,
    tax_code VARCHAR(50) NOT NULL,
    category VARCHAR(100) NOT NULL,
    use_purpose VARCHAR(100) NOT NULL,
    account VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    evidence_date DATE NOT NULL,
    description TEXT,
    policy_amount NUMERIC(15,2) NOT NULL,
    application_amount NUMERIC(15,2) NOT NULL,
    excess_reason TEXT,
    note TEXT,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_conversational_cost_expense_type CHECK (expense_type IN ('COST', 'ETC')),
    CONSTRAINT ck_conversational_cost_expense_dates CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE IF NOT EXISTS conversational_transportation_expense (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_code VARCHAR(50) NOT NULL,
    category VARCHAR(100) NOT NULL,
    use_purpose VARCHAR(100) NOT NULL,
    account VARCHAR(100) NOT NULL,
    transportation_method VARCHAR(100) NOT NULL,
    grade VARCHAR(100),
    origin_location VARCHAR(255) NOT NULL,
    destination_location VARCHAR(255) NOT NULL,
    usage_date DATE NOT NULL,
    evidence_date DATE,
    vendor VARCHAR(255) NOT NULL,
    supply_price NUMERIC(15,2) NOT NULL,
    tax NUMERIC(15,2) NOT NULL,
    policy_amount NUMERIC(15,2) NOT NULL,
    application_amount NUMERIC(15,2) NOT NULL,
    excess_reason TEXT,
    description TEXT,
    note TEXT,
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversational_trip_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_session_id UUID REFERENCES conversational_agent_session(id) ON UPDATE CASCADE ON DELETE SET NULL,
    department_id UUID NOT NULL REFERENCES conversational_department(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    trip_plan_id UUID NOT NULL REFERENCES conversational_trip_plan(id) ON UPDATE CASCADE ON DELETE CASCADE,
    transportation_expense_id UUID REFERENCES conversational_transportation_expense(id) ON UPDATE CASCADE ON DELETE SET NULL,
    cost_expense_id UUID REFERENCES conversational_cost_expense(id) ON UPDATE CASCADE ON DELETE SET NULL,
    section_code VARCHAR(30) NOT NULL,
    approval_number VARCHAR(100),
    approval_status VARCHAR(50) NOT NULL DEFAULT 'Request for approval',
    extras JSONB DEFAULT '{}'::jsonb,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_conversational_trip_report_approval CHECK (approval_status IN ('Request for approval', 'Business trip cancellation', 'Approval complete')),
    CONSTRAINT ck_conversational_trip_report_section CHECK (section_code IN ('COST', 'TRANSPORTATION', 'ETC')),
    CONSTRAINT ck_conversational_trip_report_expense_ref CHECK (
        (section_code = 'TRANSPORTATION' AND transportation_expense_id IS NOT NULL AND cost_expense_id IS NULL)
        OR (section_code IN ('COST', 'ETC') AND cost_expense_id IS NOT NULL AND transportation_expense_id IS NULL)
    )
);

CREATE TABLE IF NOT EXISTS conversational_attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_plan_id UUID REFERENCES conversational_trip_plan(id) ON UPDATE CASCADE ON DELETE CASCADE,
    report_id UUID REFERENCES conversational_trip_report(id) ON UPDATE CASCADE ON DELETE CASCADE,
    file_id VARCHAR(100) NOT NULL,
    attachment_type VARCHAR(30) NOT NULL,
    url VARCHAR(2048),
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_conversational_attachment_owner CHECK (
        (trip_plan_id IS NOT NULL AND report_id IS NULL)
        OR (trip_plan_id IS NULL AND report_id IS NOT NULL)
    ),
    CONSTRAINT ck_conversational_attachment_type CHECK (attachment_type IN ('PLAN', 'REPORT'))
);

-- -----------------------------------------------------------------------------
-- Add columns for production databases that already had older table versions.
-- Nullable columns are used where old rows may exist. Defaults are added where app expects them.
-- -----------------------------------------------------------------------------
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS corp_no VARCHAR(50);
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS agent_type VARCHAR(50);
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'COLLECTING';
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS draft_json JSONB DEFAULT '{}'::jsonb;
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS chat_event_json JSONB DEFAULT '[]'::jsonb;
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS updated_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS agent_session_id UUID;
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS approval_status VARCHAR(50) DEFAULT 'Request for approval';
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS extras JSONB DEFAULT '{}'::jsonb;
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS updated_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS evidence_date DATE;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS policy_amount NUMERIC(15,2);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS application_amount NUMERIC(15,2);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS excess_reason TEXT;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS grade VARCHAR(100);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS evidence_date DATE;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS policy_amount NUMERIC(15,2);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS application_amount NUMERIC(15,2);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS excess_reason TEXT;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS agent_session_id UUID;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS approval_number VARCHAR(100);
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS approval_status VARCHAR(50) DEFAULT 'Request for approval';
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS extras JSONB DEFAULT '{}'::jsonb;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS trip_plan_id UUID;
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS report_id UUID;
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS attachment_type VARCHAR(30);
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS url VARCHAR(2048);
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

-- Backfill defaults for older rows before tightening selected NOT NULL columns.
UPDATE conversational_agent_session SET status = 'COLLECTING' WHERE status IS NULL;
UPDATE conversational_agent_session SET draft_json = '{}'::jsonb WHERE draft_json IS NULL;
UPDATE conversational_agent_session SET chat_event_json = '[]'::jsonb WHERE chat_event_json IS NULL;
UPDATE conversational_agent_session SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_agent_session SET updated_date = NOW() WHERE updated_date IS NULL;
UPDATE conversational_trip_plan SET approval_status = 'Request for approval' WHERE approval_status IS NULL;
UPDATE conversational_trip_plan SET extras = '{}'::jsonb WHERE extras IS NULL;
UPDATE conversational_trip_plan SET updated_date = COALESCE(created_date, NOW()) WHERE updated_date IS NULL;
UPDATE conversational_trip_report SET approval_status = 'Request for approval' WHERE approval_status IS NULL;
UPDATE conversational_trip_report SET extras = '{}'::jsonb WHERE extras IS NULL;

-- Tighten NOT NULL only for columns where the app requires it and backfill is safe.
ALTER TABLE conversational_agent_session ALTER COLUMN status SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN draft_json SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN chat_event_json SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN updated_date SET NOT NULL;
ALTER TABLE conversational_trip_plan ALTER COLUMN approval_status SET NOT NULL;
ALTER TABLE conversational_trip_report ALTER COLUMN approval_status SET NOT NULL;

-- -----------------------------------------------------------------------------
-- Constraints for older existing tables. These are guarded to avoid duplicates.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_agent_session_corp') THEN
        ALTER TABLE conversational_agent_session
            ADD CONSTRAINT fk_conversational_agent_session_corp
            FOREIGN KEY (corp_no) REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_plan_agent_session') THEN
        ALTER TABLE conversational_trip_plan
            ADD CONSTRAINT fk_conversational_trip_plan_agent_session
            FOREIGN KEY (agent_session_id) REFERENCES conversational_agent_session(id) ON UPDATE CASCADE ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_report_agent_session') THEN
        ALTER TABLE conversational_trip_report
            ADD CONSTRAINT fk_conversational_trip_report_agent_session
            FOREIGN KEY (agent_session_id) REFERENCES conversational_agent_session(id) ON UPDATE CASCADE ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_agent_type') THEN
        ALTER TABLE conversational_agent_session
            ADD CONSTRAINT ck_conversational_agent_type CHECK (agent_type IN ('TRIP_PLAN', 'EXPENSE_REPORT'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_agent_status') THEN
        ALTER TABLE conversational_agent_session
            ADD CONSTRAINT ck_conversational_agent_status CHECK (status IN ('COLLECTING', 'READY_FOR_REVIEW', 'APPROVED', 'POSTED', 'CANCELLED'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_plan_approval') THEN
        ALTER TABLE conversational_trip_plan
            ADD CONSTRAINT ck_conversational_trip_plan_approval CHECK (approval_status IN ('Request for approval', 'Business trip cancellation', 'Approval complete'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_plan_dates') THEN
        ALTER TABLE conversational_trip_plan
            ADD CONSTRAINT ck_conversational_trip_plan_dates CHECK (business_start_date IS NULL OR business_end_date IS NULL OR business_end_date >= business_start_date);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_cost_expense_type') THEN
        ALTER TABLE conversational_cost_expense
            ADD CONSTRAINT ck_conversational_cost_expense_type CHECK (expense_type IN ('COST', 'ETC'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_cost_expense_dates') THEN
        ALTER TABLE conversational_cost_expense
            ADD CONSTRAINT ck_conversational_cost_expense_dates CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_approval') THEN
        ALTER TABLE conversational_trip_report
            ADD CONSTRAINT ck_conversational_trip_report_approval CHECK (approval_status IN ('Request for approval', 'Business trip cancellation', 'Approval complete'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_section') THEN
        ALTER TABLE conversational_trip_report
            ADD CONSTRAINT ck_conversational_trip_report_section CHECK (section_code IN ('COST', 'TRANSPORTATION', 'ETC'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_expense_ref') THEN
        ALTER TABLE conversational_trip_report
            ADD CONSTRAINT ck_conversational_trip_report_expense_ref CHECK (
                (section_code = 'TRANSPORTATION' AND transportation_expense_id IS NOT NULL AND cost_expense_id IS NULL)
                OR (section_code IN ('COST', 'ETC') AND cost_expense_id IS NOT NULL AND transportation_expense_id IS NULL)
            );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_attachment_owner') THEN
        ALTER TABLE conversational_attachment
            ADD CONSTRAINT ck_conversational_attachment_owner CHECK (
                (trip_plan_id IS NOT NULL AND report_id IS NULL)
                OR (trip_plan_id IS NULL AND report_id IS NOT NULL)
            );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_attachment_type') THEN
        ALTER TABLE conversational_attachment
            ADD CONSTRAINT ck_conversational_attachment_type CHECK (attachment_type IN ('PLAN', 'REPORT'));
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Indexes. Use evidence_date, not proof_date.
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_conversational_department_corp_no ON conversational_department(corp_no);
CREATE INDEX IF NOT EXISTS idx_conversational_staff_department ON conversational_staff(department_id);
CREATE INDEX IF NOT EXISTS idx_conversational_agent_session_corp_no ON conversational_agent_session(corp_no);
CREATE INDEX IF NOT EXISTS idx_conversational_agent_session_type_status ON conversational_agent_session(agent_type, status);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_corp_no ON conversational_trip_plan(corp_no);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_agent_session ON conversational_trip_plan(agent_session_id);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_period ON conversational_trip_plan(business_start_date, business_end_date);
CREATE INDEX IF NOT EXISTS idx_conversational_traveler_staff ON conversational_traveler(staff_id);
CREATE INDEX IF NOT EXISTS idx_conversational_cost_expense_type ON conversational_cost_expense(expense_type);
CREATE INDEX IF NOT EXISTS idx_conversational_cost_expense_dates ON conversational_cost_expense(start_date, end_date, evidence_date);
CREATE INDEX IF NOT EXISTS idx_conversational_transportation_expense_usage_date ON conversational_transportation_expense(usage_date);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_department ON conversational_trip_report(department_id);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_plan ON conversational_trip_report(trip_plan_id);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_agent_session ON conversational_trip_report(agent_session_id);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_transportation ON conversational_trip_report(transportation_expense_id);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_report_cost ON conversational_trip_report(cost_expense_id);
CREATE INDEX IF NOT EXISTS idx_conversational_attachment_report ON conversational_attachment(report_id);
CREATE INDEX IF NOT EXISTS idx_conversational_attachment_trip_plan ON conversational_attachment(trip_plan_id);

COMMIT;

-- Verification query after migration:
-- SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'conversational_%' ORDER BY table_name;
-- SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name LIKE 'conversational_%' ORDER BY table_name, ordinal_position;
