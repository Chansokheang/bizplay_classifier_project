-- ============================================
-- PostgreSQL Schema Creation Script
-- ============================================
-- Create database bizplay;
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS vector;

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
CREATE TABLE classifier_bot_config (
    bot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
    config JSON NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- 4. CATEGORIES
-- ============================================
CREATE TABLE classifier_categories (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    category VARCHAR(255) NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_categories_corp_code UNIQUE (corp_no, code)
);

-- ============================================
-- 5. RULES
-- ============================================
CREATE TABLE classifier_rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
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
CREATE TABLE classifier_file_upload_history (
    file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
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
    rule_id UUID NOT NULL REFERENCES classifier_rules(rule_id) ON UPDATE CASCADE ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES classifier_categories(category_id) ON UPDATE CASCADE ON DELETE CASCADE,
    PRIMARY KEY (rule_id, category_id)
);

-- ============================================
-- 8. FILE_CLASSIFY_SUMMARY
-- ============================================
CREATE TABLE classifier_file_classify_summary (
    summary_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID NOT NULL REFERENCES classifier_file_upload_history(file_id) ON UPDATE CASCADE ON DELETE CASCADE,
    corp_no VARCHAR(50) NOT NULL REFERENCES corp(corp_no) ON UPDATE CASCADE ON DELETE CASCADE,
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
CREATE INDEX idx_bot_config_corp ON classifier_bot_config(corp_no);
CREATE INDEX idx_category_corp ON classifier_categories(corp_no);
CREATE INDEX idx_rule_corp ON classifier_rules(corp_no);
CREATE INDEX idx_file_upload_history_corp ON classifier_file_upload_history(corp_no);
CREATE INDEX idx_file_upload_history_created ON classifier_file_upload_history(created_date);
CREATE INDEX idx_file_upload_history_type ON classifier_file_upload_history(file_type);
CREATE INDEX idx_file_classify_summary_corp ON classifier_file_classify_summary(corp_no);
CREATE INDEX idx_file_classify_summary_file ON classifier_file_classify_summary(file_id);
CREATE INDEX idx_file_classify_summary_created ON classifier_file_classify_summary(created_date);

-- ============================================
-- BIZPLAY CHATBOT
-- ============================================
INSERT INTO corp_group (corp_group_cd)
VALUES ('DEFAULT')
ON CONFLICT (corp_group_cd) DO NOTHING;

INSERT INTO corp (corp_no, corp_group_id, corp_name)
SELECT 'DEFAULT', cg.corp_group_id, 'Default Corporation'
FROM corp_group cg
WHERE cg.corp_group_cd = 'DEFAULT'
ON CONFLICT (corp_no) DO NOTHING;

CREATE TABLE bots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_no VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    system_prompt TEXT,
    source_expose BOOLEAN NOT NULL DEFAULT TRUE,
    llm_model VARCHAR(100) NOT NULL,
    llm_temperature NUMERIC(3,2) NOT NULL DEFAULT 0.0,
    max_answer_length INTEGER NOT NULL DEFAULT 2048,
    history_turns INTEGER NOT NULL DEFAULT 5,
    top_k INTEGER NOT NULL DEFAULT 5,
    is_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    telegram_bot_token VARCHAR(128),
    telegram_bot_username VARCHAR(64),
    telegram_last_offset BIGINT,
    telegram_configured_at TIMESTAMP,
    kakao_webhook_secret VARCHAR(64),
    kakao_bot_name VARCHAR(255),
    kakao_configured_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_bots_corp_no ON bots(corp_no);
CREATE INDEX idx_bots_is_disabled ON bots(is_disabled);
CREATE UNIQUE INDEX idx_bots_telegram_token ON bots(telegram_bot_token) WHERE telegram_bot_token IS NOT NULL;
CREATE UNIQUE INDEX idx_bots_kakao_webhook_secret ON bots(kakao_webhook_secret) WHERE kakao_webhook_secret IS NOT NULL;

CREATE TABLE bot_recommended_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    question VARCHAR(500) NOT NULL,
    created_at TIMESTAMP
);
CREATE INDEX idx_bot_recq_bot_id ON bot_recommended_questions(bot_id);

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    file_name VARCHAR(500),
    content_type VARCHAR(100),
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX idx_documents_bot_id ON documents(bot_id);
CREATE INDEX idx_documents_embedding_status ON documents(embedding_status);

CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY,
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL DEFAULT 'web',
    created_at TIMESTAMP
);
CREATE INDEX idx_chat_sessions_bot_id ON chat_sessions(bot_id);
CREATE INDEX idx_chat_sessions_channel ON chat_sessions(channel);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    lang VARCHAR(2),
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMP
);
CREATE INDEX idx_chat_messages_session_id ON chat_messages(session_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at);
CREATE INDEX idx_chat_messages_lang ON chat_messages(lang);

CREATE TABLE telegram_chats (
    id UUID PRIMARY KEY,
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    chat_id BIGINT NOT NULL,
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    tg_username VARCHAR(64),
    tg_first_name VARCHAR(128),
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_telegram_chats_bot_chat UNIQUE (bot_id, chat_id)
);
CREATE INDEX idx_telegram_chats_session ON telegram_chats(session_id);

CREATE TABLE kakao_chats (
    id UUID PRIMARY KEY,
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    kakao_user_id VARCHAR(128) NOT NULL,
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_kakao_chats_bot_user UNIQUE (bot_id, kakao_user_id)
);
CREATE INDEX idx_kakao_chats_session ON kakao_chats(session_id);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID,
    bot_id UUID,
    query TEXT NOT NULL,
    accessed_doc_ids TEXT,
    created_at TIMESTAMP
);
CREATE INDEX idx_audit_logs_session_id ON audit_logs(session_id);
CREATE INDEX idx_audit_logs_bot_id ON audit_logs(bot_id);

CREATE TABLE vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT,
    metadata JSONB,
    embedding vector(1024)
);
CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
CREATE INDEX vector_store_bot_id_idx ON vector_store ((metadata->>'bot_id'));
