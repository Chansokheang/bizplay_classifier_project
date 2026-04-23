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
        migrateCategoryCodeScope();
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

    private void migrateCategoryCodeScope() {
        jdbcTemplate.execute("""
            DO $$
            DECLARE
                constraint_record record;
            BEGIN
                IF to_regclass('public.classifier_categories') IS NULL
                   OR to_regclass('public.rule_category_map') IS NULL THEN
                    RETURN;
                END IF;

                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'rule_category_map'
                      AND column_name = 'category_id'
                ) THEN
                    ALTER TABLE public.rule_category_map ADD COLUMN category_id UUID;
                END IF;

                IF EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'rule_category_map'
                      AND column_name = 'code'
                ) THEN
                    UPDATE public.rule_category_map rcm
                    SET category_id = c.category_id
                    FROM public.classifier_categories c
                    WHERE rcm.category_id IS NULL
                      AND rcm.code = c.code;
                END IF;

                IF EXISTS (
                    SELECT 1
                    FROM public.rule_category_map
                    WHERE category_id IS NULL
                ) THEN
                    RAISE EXCEPTION 'Could not migrate rule_category_map: category_id contains NULL values.';
                END IF;

                ALTER TABLE public.rule_category_map ALTER COLUMN category_id SET NOT NULL;

                FOR constraint_record IN
                    SELECT conname
                    FROM pg_constraint
                    WHERE conrelid = 'public.rule_category_map'::regclass
                      AND contype IN ('p', 'f', 'u')
                LOOP
                    EXECUTE format('ALTER TABLE public.rule_category_map DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
                END LOOP;

                IF EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'rule_category_map'
                      AND column_name = 'code'
                ) THEN
                    ALTER TABLE public.rule_category_map DROP COLUMN code;
                END IF;

                ALTER TABLE public.rule_category_map
                    ADD CONSTRAINT rule_category_map_rule_id_fkey
                    FOREIGN KEY (rule_id)
                    REFERENCES public.classifier_rules(rule_id)
                    ON UPDATE CASCADE
                    ON DELETE CASCADE;

                ALTER TABLE public.rule_category_map
                    ADD CONSTRAINT rule_category_map_category_id_fkey
                    FOREIGN KEY (category_id)
                    REFERENCES public.classifier_categories(category_id)
                    ON UPDATE CASCADE
                    ON DELETE CASCADE;

                ALTER TABLE public.rule_category_map
                    ADD CONSTRAINT rule_category_map_pkey PRIMARY KEY (rule_id, category_id);

                FOR constraint_record IN
                    SELECT con.conname
                    FROM pg_constraint con
                    JOIN pg_class rel ON rel.oid = con.conrelid
                    JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY (con.conkey)
                    WHERE rel.relname = 'classifier_categories'
                      AND con.contype = 'u'
                      AND att.attname = 'code'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM pg_attribute other_att
                          WHERE other_att.attrelid = rel.oid
                            AND other_att.attnum = ANY (con.conkey)
                            AND other_att.attname = 'corp_no'
                      )
                LOOP
                    EXECUTE format('ALTER TABLE public.classifier_categories DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
                END LOOP;

                IF NOT EXISTS (
                    SELECT 1
                    FROM pg_constraint
                    WHERE conrelid = 'public.classifier_categories'::regclass
                      AND conname = 'uq_classifier_categories_corp_code'
                ) THEN
                    ALTER TABLE public.classifier_categories
                        ADD CONSTRAINT uq_classifier_categories_corp_code UNIQUE (corp_no, code);
                END IF;
            END
            $$;
        """);
    }
}
