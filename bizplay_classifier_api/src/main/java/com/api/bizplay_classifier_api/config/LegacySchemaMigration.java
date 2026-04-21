package com.api.bizplay_classifier_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class LegacySchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrateCorpColumns();
        migrateClassifierTableTypos();
    }

    private void migrateCorpColumns() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = 'corp'
                )
                AND EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'corp'
                      AND column_name = 'company_name'
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'corp'
                      AND column_name = 'corp_name'
                ) THEN
                    ALTER TABLE public.corp RENAME COLUMN company_name TO corp_name;
                END IF;
            END
            $$;
        """);
    }

    private void migrateClassifierTableTypos() {
        renameTableIfNeeded("classifer_bot_config", "classifier_bot_config");
        renameTableIfNeeded("classifer_categories", "classifier_categories");
        renameTableIfNeeded("classifer_rules", "classifier_rules");
        renameTableIfNeeded("classifer_file_upload_history", "classifier_file_upload_history");
        renameTableIfNeeded("classifer_file_classify_summary", "classifier_file_classify_summary");
    }

    private void renameTableIfNeeded(String oldTableName, String newTableName) {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = '%s'
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = '%s'
                ) THEN
                    EXECUTE 'ALTER TABLE public.%s RENAME TO %s';
                END IF;
            END
            $$;
        """.formatted(oldTableName, newTableName, oldTableName, newTableName));
    }
}
