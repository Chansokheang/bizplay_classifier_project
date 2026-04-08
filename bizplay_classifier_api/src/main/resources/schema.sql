-- ============================================
-- PostgreSQL Schema Creation Script
-- ============================================
-- Create database bizplay_classifier;
-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- 1. ROLE
-- ============================================
CREATE TABLE role (
                      role_id         SERIAL PRIMARY KEY,
                      role_name        VARCHAR(5) NOT NULL
);

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

-- ============================================
-- 3. COMPANY
-- ============================================
CREATE TABLE companies (
                           company_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           user_id         UUID NOT NULL REFERENCES "users"(user_id) ON UPDATE CASCADE ON DELETE CASCADE,
                           company_name    VARCHAR(255) NOT NULL,
                           business_number CHAR(10),
                           created_date    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 4. BOT_CONFIG
-- ============================================
CREATE TABLE bot_config (
                            bot_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            company_id      UUID NOT NULL REFERENCES companies(company_id) ON UPDATE CASCADE ON DELETE CASCADE,
                            config          JSON NOT NULL,
                            created_date    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 5. CATEGORIES
-- ============================================
CREATE TABLE categories (
                            category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            company_id  UUID NOT NULL REFERENCES companies(company_id) ON UPDATE CASCADE ON DELETE CASCADE,
                            code        CHAR(5) NOT NULL,
                            category    VARCHAR(255) NOT NULL,
                            "?ъ슜?щ?"  BOOLEAN NOT NULL DEFAULT FALSE
);

-- ============================================
-- 5-1. RULE
-- ============================================
CREATE TABLE rules (
                       rule_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       company_id     UUID NOT NULL REFERENCES companies(company_id) ON UPDATE CASCADE ON DELETE CASCADE,
                       rule_name      VARCHAR(255) NOT NULL,
                       usage_status   CHAR(1) NOT NULL DEFAULT 'N',
                       min_amount     INT,
                       max_amount     INT,
                       description    TEXT,
                       created_date   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 5-2. RULE_CATEGORY_MAP (many-to-many)
-- ============================================
CREATE TABLE rule_category_map (
                                   rule_id      UUID NOT NULL REFERENCES rules(rule_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                   category_id  UUID NOT NULL REFERENCES categories(category_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                   PRIMARY KEY (rule_id, category_id)
);

-- ============================================
-- 6. TRANSACTION
-- ============================================
CREATE TABLE transactions (
                              transaction_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              company_id                      UUID NOT NULL REFERENCES companies(company_id) ON UPDATE CASCADE ON DELETE CASCADE,
                              approval_date                   CHAR(8) NOT NULL,
                              approval_time                   CHAR(6) NOT NULL,
                              merchant_name                   VARCHAR(50) NOT NULL,
                              merchant_industry_code          VARCHAR(5),
                              merchant_industry_name          VARCHAR(50),
                              merchant_business_reg_number    CHAR(10) NOT NULL,
                              supply_amount                   INT,
                              vat_amount                      INT,
                              tax_type                        VARCHAR(10),
                              field_name1                     VARCHAR(50),
                              ?댁슜湲곌?id                       VARCHAR(255),   -- TODO: refactor to UUID FK ??company
                              pk                              VARCHAR(255),   -- TODO: clarify purpose
                              ?ъ슜?릋d                         VARCHAR(255),   -- TODO: refactor to UUID FK ??user
                              ?묒꽦?릋d                         VARCHAR(255),   -- TODO: refactor to UUID FK ??user
                              created_date                    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 7. CLASSIFY_DETAIL
-- ============================================
CREATE TABLE classify_detail (
                                 classify_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 transaction_id      UUID NOT NULL REFERENCES transactions(transaction_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                 method_type         VARCHAR(5),     -- E.g. AI, RULES
                                 confidence_score    INT,
                                 reason              TEXT,
                                 is_confirmed        BOOLEAN NOT NULL DEFAULT FALSE,
                                 created_date        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 8. CLASSIFY_RULE (Junction Table)
-- ============================================
CREATE TABLE classify_rule (
                               classify_id     UUID NOT NULL REFERENCES classify_detail(classify_id) ON UPDATE CASCADE ON DELETE CASCADE,
                               rule_id         UUID NOT NULL REFERENCES rules(rule_id) ON UPDATE CASCADE ON DELETE CASCADE,
                               PRIMARY KEY (classify_id, rule_id)
);

-- ============================================
-- 9. CLASSIFY_SUMMARY
-- ============================================
CREATE TABLE classify_summary (
                                  summary_id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  rule_id                         UUID NOT NULL REFERENCES rules(rule_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                  classify_id                     UUID NOT NULL REFERENCES classify_detail(classify_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                  company_id                      UUID NOT NULL REFERENCES companies(company_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                  case_num                        INT,
                                  total_amount                    INT,
                                  month                           VARCHAR(10),
                                  classification_completion_rate  INT,
                                  rule_ratio                      INT,
                                  ai_ratio                        INT,
                                  average                         FLOAT,
                                  created_date                    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- INDEXES (recommended)
-- ============================================
CREATE INDEX idx_user_role           ON "users"(role_id);
CREATE INDEX idx_company_user        ON companies(user_id);
CREATE INDEX idx_bot_config_company  ON bot_config(company_id);
CREATE INDEX idx_rule_company        ON rules(company_id);
CREATE INDEX idx_rule_category_map_rule ON rule_category_map(rule_id);
CREATE INDEX idx_rule_category_map_category ON rule_category_map(category_id);
CREATE INDEX idx_categories_company  ON categories(company_id);
CREATE INDEX idx_transaction_company ON transactions(company_id);
CREATE INDEX idx_classify_transaction ON classify_detail(transaction_id);
CREATE INDEX idx_classify_rule_classify ON classify_rule(classify_id);
CREATE INDEX idx_classify_rule_rule     ON classify_rule(rule_id);
CREATE INDEX idx_summary_company     ON classify_summary(company_id);
CREATE INDEX idx_summary_rule        ON classify_summary(rule_id);
CREATE INDEX idx_summary_classify    ON classify_summary(classify_id);

-- ============================================
-- 10. FILE_UPLOAD_HISTORY
-- ============================================
CREATE TABLE file_upload_history (
                                     file_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     company_id          UUID REFERENCES companies(company_id) ON UPDATE CASCADE ON DELETE CASCADE,
                                     original_file_name  VARCHAR(255) NOT NULL,
                                     stored_file_name    VARCHAR(255) NOT NULL,
                                     file_url            TEXT NOT NULL,
                                     sheet_name          VARCHAR(100),
                                     created_date        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_file_upload_history_company ON file_upload_history(company_id);
CREATE INDEX idx_file_upload_history_created ON file_upload_history(created_date);

-- ============================================
-- 10-1. FILE_CLASSIFY_SUMMARY
-- ============================================
CREATE TABLE file_classify_summary (
    summary_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id             UUID NOT NULL REFERENCES file_upload_history(file_id) ON UPDATE CASCADE ON DELETE CASCADE,
    company_id          UUID NOT NULL REFERENCES companies(company_id) ON UPDATE CASCADE ON DELETE CASCADE,
    total_rows          INT NOT NULL DEFAULT 0,
    processed_rows      INT NOT NULL DEFAULT 0,
    skipped_rows        INT NOT NULL DEFAULT 0,
    rule_matched_rows   INT NOT NULL DEFAULT 0,
    ai_matched_rows     INT NOT NULL DEFAULT 0,
    unmatched_rows      INT NOT NULL DEFAULT 0,
    created_date        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_file_classify_summary UNIQUE (file_id, company_id),
    CONSTRAINT ck_file_classify_non_negative CHECK (
        total_rows >= 0 AND processed_rows >= 0 AND skipped_rows >= 0
        AND rule_matched_rows >= 0 AND ai_matched_rows >= 0 AND unmatched_rows >= 0
    )
);

CREATE INDEX idx_file_classify_summary_company ON file_classify_summary(company_id);
CREATE INDEX idx_file_classify_summary_file ON file_classify_summary(file_id);
CREATE INDEX idx_file_classify_summary_created ON file_classify_summary(created_date);

-- ============================================
-- 11. OTPS
-- ============================================
CREATE TABLE otps(
    otp_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "users"(user_id) ON UPDATE CASCADE ON DELETE CASCADE,
    otp_code CHAR(8) NOT NULL,
    issued_date DATE NOT NULL,
    expiration DATE NOT NULL,
    is_verified BOOLEAN NOT NULL default FALSE
);
