-- Production-safe conversational schema alignment, v4.
-- Goal: make existing production conversational_* tables match the current code/schemaV2.sql.
-- Safe to run multiple times. Does not drop/truncate/delete data.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- Create any fully-missing conversational tables.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversational_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversational_staff (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES conversational_department(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    position VARCHAR(100),
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversational_agent_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    agent_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'COLLECTING',
    draft_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    chat_event_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date TIMESTAMP NOT NULL DEFAULT NOW()
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
    updated_date TIMESTAMP NOT NULL DEFAULT NOW()
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
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
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
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversational_attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_plan_id UUID REFERENCES conversational_trip_plan(id) ON UPDATE CASCADE ON DELETE CASCADE,
    report_id UUID REFERENCES conversational_trip_report(id) ON UPDATE CASCADE ON DELETE CASCADE,
    file_id VARCHAR(100) NOT NULL,
    attachment_type VARCHAR(30) NOT NULL,
    url VARCHAR(2048),
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- Add every column expected by current repository SQL to existing tables.
-- -----------------------------------------------------------------------------
ALTER TABLE conversational_department ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_department ADD COLUMN IF NOT EXISTS corp_no VARCHAR(50);
ALTER TABLE conversational_department ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE conversational_department ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_staff ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_staff ADD COLUMN IF NOT EXISTS department_id UUID;
ALTER TABLE conversational_staff ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE conversational_staff ADD COLUMN IF NOT EXISTS position VARCHAR(100);
ALTER TABLE conversational_staff ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS corp_no VARCHAR(50);
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS agent_type VARCHAR(50);
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'COLLECTING';
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS draft_json JSONB DEFAULT '{}'::jsonb;
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS chat_event_json JSONB DEFAULT '[]'::jsonb;
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();
ALTER TABLE conversational_agent_session ADD COLUMN IF NOT EXISTS updated_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS corp_no VARCHAR(50);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS agent_session_id UUID;
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS user_req_id VARCHAR(100);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS plan_type VARCHAR(255);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS purpose VARCHAR(255);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS title VARCHAR(100);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS business_period VARCHAR(100);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS business_start_date DATE;
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS business_end_date DATE;
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS destination VARCHAR(255);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS business_trip_classification VARCHAR(100);
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS approval_status VARCHAR(50) DEFAULT 'Request for approval';
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS extras JSONB DEFAULT '{}'::jsonb;
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();
ALTER TABLE conversational_trip_plan ADD COLUMN IF NOT EXISTS updated_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_traveler ADD COLUMN IF NOT EXISTS trip_id UUID;
ALTER TABLE conversational_traveler ADD COLUMN IF NOT EXISTS staff_id UUID;
ALTER TABLE conversational_traveler ADD COLUMN IF NOT EXISTS origin_location VARCHAR(255);
ALTER TABLE conversational_traveler ADD COLUMN IF NOT EXISTS destination_location VARCHAR(255);
ALTER TABLE conversational_traveler ADD COLUMN IF NOT EXISTS return_point VARCHAR(255);
ALTER TABLE conversational_traveler ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS expense_type VARCHAR(100);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS tax_code VARCHAR(50);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS use_purpose VARCHAR(100);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS account VARCHAR(100);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS evidence_date DATE;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS policy_amount NUMERIC(15,2);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS application_amount NUMERIC(15,2);
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS excess_reason TEXT;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE conversational_cost_expense ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS tax_code VARCHAR(50);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS use_purpose VARCHAR(100);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS account VARCHAR(100);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS transportation_method VARCHAR(100);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS grade VARCHAR(100);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS origin_location VARCHAR(255);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS destination_location VARCHAR(255);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS usage_date DATE;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS evidence_date DATE;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS vendor VARCHAR(255);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS supply_price NUMERIC(15,2);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS tax NUMERIC(15,2);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS policy_amount NUMERIC(15,2);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS application_amount NUMERIC(15,2);
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS excess_reason TEXT;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE conversational_transportation_expense ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS agent_session_id UUID;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS department_id UUID;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS trip_plan_id UUID;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS transportation_expense_id UUID;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS cost_expense_id UUID;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS section_code VARCHAR(30);
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS approval_number VARCHAR(100);
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS approval_status VARCHAR(50) DEFAULT 'Request for approval';
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS extras JSONB DEFAULT '{}'::jsonb;
ALTER TABLE conversational_trip_report ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS trip_plan_id UUID;
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS report_id UUID;
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS file_id VARCHAR(100);
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS attachment_type VARCHAR(30);
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS url VARCHAR(2048);
ALTER TABLE conversational_attachment ADD COLUMN IF NOT EXISTS created_date TIMESTAMP DEFAULT NOW();

-- -----------------------------------------------------------------------------
-- Backfill renamed legacy columns and safe defaults.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_department' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_department SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_staff' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_staff SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_agent_session' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_agent_session SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_agent_session' AND column_name = 'updated_at') THEN
        EXECUTE 'UPDATE conversational_agent_session SET updated_date = COALESCE(updated_date, updated_at) WHERE updated_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_trip_plan' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_trip_plan SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_trip_plan' AND column_name = 'updated_at') THEN
        EXECUTE 'UPDATE conversational_trip_plan SET updated_date = COALESCE(updated_date, updated_at) WHERE updated_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_traveler' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_traveler SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_cost_expense' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_cost_expense SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_cost_expense' AND column_name = 'proof_date') THEN
        EXECUTE 'UPDATE conversational_cost_expense SET evidence_date = COALESCE(evidence_date, proof_date) WHERE evidence_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_transportation_expense' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_transportation_expense SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_transportation_expense' AND column_name = 'proof_date') THEN
        EXECUTE 'UPDATE conversational_transportation_expense SET evidence_date = COALESCE(evidence_date, proof_date) WHERE evidence_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_trip_report' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_trip_report SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'conversational_attachment' AND column_name = 'created_at') THEN
        EXECUTE 'UPDATE conversational_attachment SET created_date = COALESCE(created_date, created_at) WHERE created_date IS NULL';
    END IF;
END $$;

UPDATE conversational_department SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_staff SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_agent_session SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_trip_plan SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_cost_expense SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_transportation_expense SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_trip_report SET id = gen_random_uuid() WHERE id IS NULL;
UPDATE conversational_attachment SET id = gen_random_uuid() WHERE id IS NULL;

UPDATE conversational_agent_session SET status = 'COLLECTING' WHERE status IS NULL OR status = '';
UPDATE conversational_agent_session SET draft_json = '{}'::jsonb WHERE draft_json IS NULL;
UPDATE conversational_agent_session SET chat_event_json = '[]'::jsonb WHERE chat_event_json IS NULL;
UPDATE conversational_agent_session SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_agent_session SET updated_date = created_date WHERE updated_date IS NULL;

UPDATE conversational_trip_plan SET user_req_id = 'MIGRATED-' || id::text WHERE user_req_id IS NULL OR user_req_id = '';
UPDATE conversational_trip_plan SET approval_status = 'Request for approval' WHERE approval_status IS NULL OR approval_status = '';
UPDATE conversational_trip_plan SET extras = '{}'::jsonb WHERE extras IS NULL;
UPDATE conversational_trip_plan SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_trip_plan SET updated_date = created_date WHERE updated_date IS NULL;

UPDATE conversational_department SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_staff SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_traveler SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_cost_expense SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_transportation_expense SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_trip_report SET approval_status = 'Request for approval' WHERE approval_status IS NULL OR approval_status = '';
UPDATE conversational_trip_report SET extras = '{}'::jsonb WHERE extras IS NULL;
UPDATE conversational_trip_report SET created_date = NOW() WHERE created_date IS NULL;
UPDATE conversational_attachment SET created_date = NOW() WHERE created_date IS NULL;

-- -----------------------------------------------------------------------------
-- Defaults and safe NOT NULL tightening used by current code.
-- Required business columns are not force-filled with fake data, except user_req_id
-- for old trip plans, because existing rows may need manual domain cleanup.
-- -----------------------------------------------------------------------------
ALTER TABLE conversational_department ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_staff ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_agent_session ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_trip_plan ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_cost_expense ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_transportation_expense ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_trip_report ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE conversational_attachment ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE conversational_agent_session ALTER COLUMN status SET DEFAULT 'COLLECTING';
ALTER TABLE conversational_agent_session ALTER COLUMN draft_json SET DEFAULT '{}'::jsonb;
ALTER TABLE conversational_agent_session ALTER COLUMN chat_event_json SET DEFAULT '[]'::jsonb;
ALTER TABLE conversational_agent_session ALTER COLUMN created_date SET DEFAULT NOW();
ALTER TABLE conversational_agent_session ALTER COLUMN updated_date SET DEFAULT NOW();
ALTER TABLE conversational_trip_plan ALTER COLUMN approval_status SET DEFAULT 'Request for approval';
ALTER TABLE conversational_trip_plan ALTER COLUMN extras SET DEFAULT '{}'::jsonb;
ALTER TABLE conversational_trip_plan ALTER COLUMN created_date SET DEFAULT NOW();
ALTER TABLE conversational_trip_plan ALTER COLUMN updated_date SET DEFAULT NOW();
ALTER TABLE conversational_trip_report ALTER COLUMN approval_status SET DEFAULT 'Request for approval';
ALTER TABLE conversational_trip_report ALTER COLUMN extras SET DEFAULT '{}'::jsonb;
ALTER TABLE conversational_trip_report ALTER COLUMN created_date SET DEFAULT NOW();
ALTER TABLE conversational_attachment ALTER COLUMN created_date SET DEFAULT NOW();

ALTER TABLE conversational_department ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_department ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_staff ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_staff ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN status SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN draft_json SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN chat_event_json SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_agent_session ALTER COLUMN updated_date SET NOT NULL;
ALTER TABLE conversational_trip_plan ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_trip_plan ALTER COLUMN user_req_id SET NOT NULL;
ALTER TABLE conversational_trip_plan ALTER COLUMN approval_status SET NOT NULL;
ALTER TABLE conversational_trip_plan ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_trip_plan ALTER COLUMN updated_date SET NOT NULL;
ALTER TABLE conversational_traveler ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_cost_expense ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_cost_expense ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_transportation_expense ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_transportation_expense ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_trip_report ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_trip_report ALTER COLUMN approval_status SET NOT NULL;
ALTER TABLE conversational_trip_report ALTER COLUMN created_date SET NOT NULL;
ALTER TABLE conversational_attachment ALTER COLUMN id SET NOT NULL;
ALTER TABLE conversational_attachment ALTER COLUMN created_date SET NOT NULL;

-- -----------------------------------------------------------------------------
-- Guarded constraints. NOT VALID avoids failing deployment if old data is dirty;
-- new/updated rows are still checked.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_conversational_department_corp_name') THEN
        ALTER TABLE conversational_department ADD CONSTRAINT uq_conversational_department_corp_name UNIQUE (corp_no, name);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_conversational_staff_department_name_position') THEN
        ALTER TABLE conversational_staff ADD CONSTRAINT uq_conversational_staff_department_name_position UNIQUE (department_id, name, position);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_department_corp') THEN
        ALTER TABLE conversational_department ADD CONSTRAINT fk_conversational_department_corp
            FOREIGN KEY (corp_no) REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_staff_department') THEN
        ALTER TABLE conversational_staff ADD CONSTRAINT fk_conversational_staff_department
            FOREIGN KEY (department_id) REFERENCES conversational_department(id) ON UPDATE CASCADE ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_agent_session_corp') THEN
        ALTER TABLE conversational_agent_session ADD CONSTRAINT fk_conversational_agent_session_corp
            FOREIGN KEY (corp_no) REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_plan_corp') THEN
        ALTER TABLE conversational_trip_plan ADD CONSTRAINT fk_conversational_trip_plan_corp
            FOREIGN KEY (corp_no) REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_plan_agent_session') THEN
        ALTER TABLE conversational_trip_plan ADD CONSTRAINT fk_conversational_trip_plan_agent_session
            FOREIGN KEY (agent_session_id) REFERENCES conversational_agent_session(id) ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_traveler_trip') THEN
        ALTER TABLE conversational_traveler ADD CONSTRAINT fk_conversational_traveler_trip
            FOREIGN KEY (trip_id) REFERENCES conversational_trip_plan(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_traveler_staff') THEN
        ALTER TABLE conversational_traveler ADD CONSTRAINT fk_conversational_traveler_staff
            FOREIGN KEY (staff_id) REFERENCES conversational_staff(id) ON UPDATE CASCADE ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_report_agent_session') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT fk_conversational_trip_report_agent_session
            FOREIGN KEY (agent_session_id) REFERENCES conversational_agent_session(id) ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_report_department') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT fk_conversational_trip_report_department
            FOREIGN KEY (department_id) REFERENCES conversational_department(id) ON UPDATE CASCADE ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_report_plan') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT fk_conversational_trip_report_plan
            FOREIGN KEY (trip_plan_id) REFERENCES conversational_trip_plan(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_report_transportation') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT fk_conversational_trip_report_transportation
            FOREIGN KEY (transportation_expense_id) REFERENCES conversational_transportation_expense(id) ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_trip_report_cost') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT fk_conversational_trip_report_cost
            FOREIGN KEY (cost_expense_id) REFERENCES conversational_cost_expense(id) ON UPDATE CASCADE ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_attachment_trip_plan') THEN
        ALTER TABLE conversational_attachment ADD CONSTRAINT fk_conversational_attachment_trip_plan
            FOREIGN KEY (trip_plan_id) REFERENCES conversational_trip_plan(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversational_attachment_report') THEN
        ALTER TABLE conversational_attachment ADD CONSTRAINT fk_conversational_attachment_report
            FOREIGN KEY (report_id) REFERENCES conversational_trip_report(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_agent_type') THEN
        ALTER TABLE conversational_agent_session ADD CONSTRAINT ck_conversational_agent_type
            CHECK (agent_type IN ('TRIP_PLAN', 'EXPENSE_REPORT')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_agent_status') THEN
        ALTER TABLE conversational_agent_session ADD CONSTRAINT ck_conversational_agent_status
            CHECK (status IN ('COLLECTING', 'READY_FOR_REVIEW', 'APPROVED', 'POSTED', 'CANCELLED')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_plan_approval') THEN
        ALTER TABLE conversational_trip_plan ADD CONSTRAINT ck_conversational_trip_plan_approval
            CHECK (approval_status IN ('Request for approval', 'Business trip cancellation', 'Approval complete')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_plan_dates') THEN
        ALTER TABLE conversational_trip_plan ADD CONSTRAINT ck_conversational_trip_plan_dates
            CHECK (business_start_date IS NULL OR business_end_date IS NULL OR business_end_date >= business_start_date) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_cost_expense_type') THEN
        ALTER TABLE conversational_cost_expense ADD CONSTRAINT ck_conversational_cost_expense_type
            CHECK (expense_type IN ('COST', 'ETC')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_cost_expense_dates') THEN
        ALTER TABLE conversational_cost_expense ADD CONSTRAINT ck_conversational_cost_expense_dates
            CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_approval') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT ck_conversational_trip_report_approval
            CHECK (approval_status IN ('Request for approval', 'Business trip cancellation', 'Approval complete')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_section') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT ck_conversational_trip_report_section
            CHECK (section_code IN ('COST', 'TRANSPORTATION', 'ETC')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_trip_report_expense_ref') THEN
        ALTER TABLE conversational_trip_report ADD CONSTRAINT ck_conversational_trip_report_expense_ref CHECK (
            (section_code = 'TRANSPORTATION' AND transportation_expense_id IS NOT NULL AND cost_expense_id IS NULL)
            OR (section_code IN ('COST', 'ETC') AND cost_expense_id IS NOT NULL AND transportation_expense_id IS NULL)
        ) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_attachment_owner') THEN
        ALTER TABLE conversational_attachment ADD CONSTRAINT ck_conversational_attachment_owner CHECK (
            (trip_plan_id IS NOT NULL AND report_id IS NULL)
            OR (trip_plan_id IS NULL AND report_id IS NOT NULL)
        ) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_conversational_attachment_type') THEN
        ALTER TABLE conversational_attachment ADD CONSTRAINT ck_conversational_attachment_type
            CHECK (attachment_type IN ('PLAN', 'REPORT')) NOT VALID;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Indexes expected by schemaV2/current query paths.
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_conversational_department_corp_no ON conversational_department(corp_no);
CREATE INDEX IF NOT EXISTS idx_conversational_staff_department ON conversational_staff(department_id);
CREATE INDEX IF NOT EXISTS idx_conversational_agent_session_corp_no ON conversational_agent_session(corp_no);
CREATE INDEX IF NOT EXISTS idx_conversational_agent_session_type_status ON conversational_agent_session(agent_type, status);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_corp_no ON conversational_trip_plan(corp_no);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_agent_session ON conversational_trip_plan(agent_session_id);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_period ON conversational_trip_plan(business_start_date, business_end_date);
CREATE INDEX IF NOT EXISTS idx_conversational_trip_plan_user_req_id ON conversational_trip_plan(user_req_id);
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

-- -----------------------------------------------------------------------------
-- Final audit: fail the transaction if any current-code required column is missing.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    missing_count integer;
BEGIN
    WITH expected(table_name, column_name) AS (
        VALUES
            ('conversational_department','id'), ('conversational_department','corp_no'), ('conversational_department','name'), ('conversational_department','created_date'),
            ('conversational_staff','id'), ('conversational_staff','department_id'), ('conversational_staff','name'), ('conversational_staff','position'), ('conversational_staff','created_date'),
            ('conversational_agent_session','id'), ('conversational_agent_session','corp_no'), ('conversational_agent_session','agent_type'), ('conversational_agent_session','status'), ('conversational_agent_session','draft_json'), ('conversational_agent_session','chat_event_json'), ('conversational_agent_session','created_date'), ('conversational_agent_session','updated_date'),
            ('conversational_trip_plan','id'), ('conversational_trip_plan','corp_no'), ('conversational_trip_plan','agent_session_id'), ('conversational_trip_plan','user_req_id'), ('conversational_trip_plan','plan_type'), ('conversational_trip_plan','purpose'), ('conversational_trip_plan','title'), ('conversational_trip_plan','content'), ('conversational_trip_plan','business_period'), ('conversational_trip_plan','business_start_date'), ('conversational_trip_plan','business_end_date'), ('conversational_trip_plan','destination'), ('conversational_trip_plan','business_trip_classification'), ('conversational_trip_plan','approval_status'), ('conversational_trip_plan','extras'), ('conversational_trip_plan','created_date'), ('conversational_trip_plan','updated_date'),
            ('conversational_traveler','trip_id'), ('conversational_traveler','staff_id'), ('conversational_traveler','origin_location'), ('conversational_traveler','destination_location'), ('conversational_traveler','return_point'), ('conversational_traveler','created_date'),
            ('conversational_cost_expense','id'), ('conversational_cost_expense','expense_type'), ('conversational_cost_expense','tax_code'), ('conversational_cost_expense','category'), ('conversational_cost_expense','use_purpose'), ('conversational_cost_expense','account'), ('conversational_cost_expense','start_date'), ('conversational_cost_expense','end_date'), ('conversational_cost_expense','evidence_date'), ('conversational_cost_expense','description'), ('conversational_cost_expense','policy_amount'), ('conversational_cost_expense','application_amount'), ('conversational_cost_expense','excess_reason'), ('conversational_cost_expense','note'), ('conversational_cost_expense','created_date'),
            ('conversational_transportation_expense','id'), ('conversational_transportation_expense','tax_code'), ('conversational_transportation_expense','category'), ('conversational_transportation_expense','use_purpose'), ('conversational_transportation_expense','account'), ('conversational_transportation_expense','transportation_method'), ('conversational_transportation_expense','grade'), ('conversational_transportation_expense','origin_location'), ('conversational_transportation_expense','destination_location'), ('conversational_transportation_expense','usage_date'), ('conversational_transportation_expense','evidence_date'), ('conversational_transportation_expense','vendor'), ('conversational_transportation_expense','supply_price'), ('conversational_transportation_expense','tax'), ('conversational_transportation_expense','policy_amount'), ('conversational_transportation_expense','application_amount'), ('conversational_transportation_expense','excess_reason'), ('conversational_transportation_expense','description'), ('conversational_transportation_expense','note'), ('conversational_transportation_expense','created_date'),
            ('conversational_trip_report','id'), ('conversational_trip_report','agent_session_id'), ('conversational_trip_report','department_id'), ('conversational_trip_report','trip_plan_id'), ('conversational_trip_report','transportation_expense_id'), ('conversational_trip_report','cost_expense_id'), ('conversational_trip_report','section_code'), ('conversational_trip_report','approval_number'), ('conversational_trip_report','approval_status'), ('conversational_trip_report','extras'), ('conversational_trip_report','created_date'),
            ('conversational_attachment','id'), ('conversational_attachment','trip_plan_id'), ('conversational_attachment','report_id'), ('conversational_attachment','file_id'), ('conversational_attachment','attachment_type'), ('conversational_attachment','url'), ('conversational_attachment','created_date')
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
        RAISE EXCEPTION 'Conversational schema alignment failed: % required columns are still missing', missing_count;
    END IF;
END $$;

COMMIT;
