-- ============================================
-- PostgreSQL Schema Creation Script
-- ============================================
-- Create database bizplay;
-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- 1. ROLE
-- ============================================
CREATE TABLE role (
                      role_id         SERIAL PRIMARY KEY,
                      role_name        VARCHAR(5) NOT NULL
);

INSERT INTO role VALUES (DEFAULT, 'USER'), (DEFAULT, 'ADMIN');

-- ============================================
-- 2. USER
-- ============================================
CREATE TABLE "users" (
                         user_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         role_id         INT NOT NULL REFERENCES role(role_id) ON UPDATE CASCADE ON DELETE CASCADE DEFAULT 1,
                         username        VARCHAR(40) NOT NULL,
                         firstname       VARCHAR(20) NOT NULL,
                         lastname        VARCHAR(20) NOT NULL,
                         email           VARCHAR(255) NOT NULL UNIQUE,
                         password        TEXT NOT NULL,
                         gender          CHAR(1),
                         dob             DATE NOT NULL,
                         is_verified     BOOLEAN NOT NULL DEFAULT FALSE,
                         is_disabled     BOOLEAN NOT NULL DEFAULT FALSE,
                         created_date    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE corp_group(
                           corp_group_id   BIGSERIAL  PRIMARY KEY,
                           corp_group_cd   VARCHAR(20) unique NOT NULL
);

-- ============================================
-- 3. corp
-- ============================================
CREATE TABLE corp (
                      corp_id          BIGSERIAL PRIMARY KEY,
                      corp_no          CHAR(10) UNIQUE NOT NULL,
                      user_id          UUID NOT NULL REFERENCES "users"(user_id) ON UPDATE CASCADE ON DELETE CASCADE,
                      corp_group_id    BIGINT NOT NULL REFERENCES "corp_group"(corp_group_id) ON UPDATE CASCADE ON DELETE CASCADE,
                      corp_name        VARCHAR(255) NOT NULL,
                      created_date     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 4. BOT_CONFIG
-- ============================================
CREATE TABLE classifer_bot_config (
                                      bot_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      corp_no         CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
                                      config          JSON NOT NULL,
                                      created_date    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 5-1. RULE_CATEGORIES
-- ============================================
CREATE TABLE classifer_categories (
                                      category_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      corp_no      CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
                                      code         VARCHAR(50) UNIQUE NOT NUll,
                                      category     VARCHAR(255) NOT NULL,
                                      "사용여부"      BOOLEAN NOT NULL DEFAULT FALSE
);

-- ============================================
-- 5. RULE (purpose)
-- ============================================


CREATE TABLE classifer_rules (
                                 rule_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 corp_no         CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
                                 가맹점업종명      VARCHAR(255) NOT NULL,
                                 가맹점업종코드     VARCHAR(5) NOT NULL,
                                 usage_status    CHAR(1) NOT NULL DEFAULT 'N',
                                 min_amount      INT,
                                 max_amount      INT,
                                 description     TEXT,
                                 created_date    TIMESTAMP NOT NULL DEFAULT NOW()
);


-- ============================================
-- INDEXES (recommended)
-- ============================================
CREATE INDEX idx_user_role           ON "users"(role_id);
CREATE INDEX idx_corp_user        ON corp(user_id);
CREATE INDEX idx_bot_config_corp  ON classifer_bot_config(corp_no);
CREATE INDEX idx_rule_corp        ON classifer_rules(corp_no);


CREATE TABLE classifer_file_upload_history (
                                               file_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               corp_no             CHAR(10) REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
                                               original_file_name  VARCHAR(255) NOT NULL,
                                               stored_file_name    VARCHAR(255) NOT NULL,
                                               file_url            TEXT NOT NULL,
                                               sheet_name          VARCHAR(100),
                                               file_type           VARCHAR(20) NOT NULL, -- TRAINING / INPUT / OUTPUT
                                               status              VARCHAR(255),
                                               created_date        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_file_upload_history_corp ON classifer_file_upload_history(corp_no);
CREATE INDEX idx_file_upload_history_created ON classifer_file_upload_history(created_date);
CREATE INDEX idx_file_upload_history_type ON classifer_file_upload_history(file_type);

create table otps(
                     otp_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                     user_id UUID NOT NULL REFERENCES "users"(user_id) ON UPDATE CASCADE ON DELETE CASCADE,
                     otp_code CHAR(8) NOT NULL,
                     issued_date DATE NOT NULL,
                     expiration DATE NOT NULL,
                     is_verified BOOLEAN NOT NULL default False
);

CREATE TABLE rule_category_map (
                                   rule_id      UUID NOT NULL REFERENCES classifer_rules(rule_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                   code  VARCHAR(50) NOT NULL REFERENCES classifer_categories(code) ON UPDATE CASCADE ON DELETE CASCADE,
                                   PRIMARY KEY (rule_id, code)
);


-- Per-file classification summary (one row per uploaded file per corp)
CREATE TABLE classifer_file_classify_summary (
                                                 summary_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                 file_id                 UUID NOT NULL REFERENCES classifer_file_upload_history(file_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                                 corp_no                 CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,

    -- processing totals
                                                 total_rows              INT NOT NULL DEFAULT 0,
                                                 processed_rows          INT NOT NULL DEFAULT 0,
                                                 skipped_rows            INT NOT NULL DEFAULT 0,

    -- classification breakdown
                                                 rule_matched_rows       INT NOT NULL DEFAULT 0,
                                                 ai_matched_rows         INT NOT NULL DEFAULT 0,
                                                 unmatched_rows          INT NOT NULL DEFAULT 0,

                                                 created_date            TIMESTAMP NOT NULL DEFAULT NOW(),
                                                 updated_date            TIMESTAMP NOT NULL DEFAULT NOW(),

                                                 CONSTRAINT uq_file_classify_summary UNIQUE (file_id, corp_no),
                                                 CONSTRAINT ck_file_classify_non_negative CHECK (
                                                     total_rows >= 0 AND processed_rows >= 0 AND skipped_rows >= 0
                                                         AND rule_matched_rows >= 0 AND ai_matched_rows >= 0 AND unmatched_rows >= 0
                                                     )
);

CREATE INDEX idx_file_classify_summary_corp ON classifer_file_classify_summary(corp_no);
CREATE INDEX idx_file_classify_summary_file ON classifer_file_classify_summary(file_id);
CREATE INDEX idx_file_classify_summary_created ON classifer_file_classify_summary(created_date);