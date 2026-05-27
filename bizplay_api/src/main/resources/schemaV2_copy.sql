-- ============================================
-- PostgreSQL Schema Creation Script
-- ============================================
-- Create database bizplay;
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- 1. CORP_GROUP
-- ============================================
CREATE TABLE corp_group (
    corp_group_id BIGSERIAL PRIMARY KEY,
    corp_group_cd VARCHAR(20) UNIQUE NOT NULL
);

-- ============================================
-- 2. CORP
-- ============================================
CREATE TABLE corp (
    corp_id BIGSERIAL PRIMARY KEY,
    corp_no VARCHAR(50) UNIQUE NOT NULL,
    corp_group_id BIGINT NOT NULL REFERENCES corp_group(corp_group_id) ON UPDATE CASCADE ON DELETE CASCADE,
    corp_name VARCHAR(255) NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 3. BOT_CONFIG
-- ============================================
CREATE TABLE classifer_bot_config (
    bot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
    config JSON NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 4. CATEGORIES
-- ============================================
CREATE TABLE classifer_categories (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    category VARCHAR(255) NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_categories_corp_code UNIQUE (corp_no, code)
);

-- ============================================
-- 5. RULES
-- ============================================
CREATE TABLE classifer_rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
    merchant_industry_name VARCHAR(255) NOT NULL,
    merchant_industry_code VARCHAR(5) NOT NULL,
    usage_status CHAR(1) NOT NULL DEFAULT 'N',
    min_amount INT,
    max_amount INT,
    description TEXT,
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 6. FILE_UPLOAD_HISTORY
-- ============================================
CREATE TABLE classifer_file_upload_history (
    file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no CHAR(10) REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    sheet_name VARCHAR(100),
    file_type VARCHAR(20) NOT NULL,
    status VARCHAR(255),
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 7. RULE_CATEGORY_MAP
-- ============================================
CREATE TABLE rule_category_map (
    rule_id UUID NOT NULL REFERENCES classifer_rules(rule_id) ON UPDATE CASCADE ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES classifer_categories(category_id) ON UPDATE CASCADE ON DELETE CASCADE,
    PRIMARY KEY (rule_id, category_id)
);

-- ============================================
-- 8. FILE_CLASSIFY_SUMMARY
-- ============================================
CREATE TABLE classifer_file_classify_summary (
    summary_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID NOT NULL REFERENCES classifer_file_upload_history(file_id) ON UPDATE CASCADE ON DELETE CASCADE,
    corp_no CHAR(10) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
    total_rows INT NOT NULL DEFAULT 0,
    processed_rows INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0,
    rule_matched_rows INT NOT NULL DEFAULT 0,
    ai_matched_rows INT NOT NULL DEFAULT 0,
    unmatched_rows INT NOT NULL DEFAULT 0,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_file_classify_summary UNIQUE (file_id, corp_no),
    CONSTRAINT ck_file_classify_non_negative CHECK (
        total_rows >= 0
        AND processed_rows >= 0
        AND skipped_rows >= 0
        AND rule_matched_rows >= 0
        AND ai_matched_rows >= 0
        AND unmatched_rows >= 0
    )
);

-- ============================================
-- INDEXES
-- ============================================
CREATE INDEX idx_corp_group_code ON corp_group(corp_group_cd);
CREATE INDEX idx_bot_config_corp ON classifer_bot_config(corp_no);
CREATE INDEX idx_category_corp ON classifer_categories(corp_no);
CREATE INDEX idx_rule_corp ON classifer_rules(corp_no);
CREATE INDEX idx_file_upload_history_corp ON classifer_file_upload_history(corp_no);
CREATE INDEX idx_file_upload_history_created ON classifer_file_upload_history(created_date);
CREATE INDEX idx_file_upload_history_type ON classifer_file_upload_history(file_type);
CREATE INDEX idx_file_classify_summary_corp ON classifer_file_classify_summary(corp_no);
CREATE INDEX idx_file_classify_summary_file ON classifer_file_classify_summary(file_id);
CREATE INDEX idx_file_classify_summary_created ON classifer_file_classify_summary(created_date);
