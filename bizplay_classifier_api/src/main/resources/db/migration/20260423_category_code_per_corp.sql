BEGIN;

ALTER TABLE rule_category_map
    ADD COLUMN IF NOT EXISTS category_id UUID;

UPDATE rule_category_map rcm
SET category_id = c.category_id
FROM classifier_categories c
WHERE rcm.category_id IS NULL
  AND rcm.code = c.code;

ALTER TABLE rule_category_map
    ALTER COLUMN category_id SET NOT NULL;

DO $$
DECLARE
    constraint_record record;
BEGIN
    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'rule_category_map'::regclass
          AND contype IN ('p', 'f', 'u')
    LOOP
        EXECUTE format('ALTER TABLE rule_category_map DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
    END LOOP;
END $$;

ALTER TABLE rule_category_map
    ADD CONSTRAINT rule_category_map_category_id_fkey
        FOREIGN KEY (category_id)
        REFERENCES classifier_categories(category_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE;

ALTER TABLE rule_category_map
    ADD CONSTRAINT rule_category_map_rule_id_fkey
        FOREIGN KEY (rule_id)
        REFERENCES classifier_rules(rule_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE;

ALTER TABLE rule_category_map
    ADD CONSTRAINT rule_category_map_pkey PRIMARY KEY (rule_id, category_id);

ALTER TABLE rule_category_map
    DROP COLUMN IF EXISTS code;

DO $$
DECLARE
    constraint_record record;
BEGIN
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
        EXECUTE format('ALTER TABLE classifier_categories DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
    END LOOP;
END $$;

ALTER TABLE classifier_categories
    ADD CONSTRAINT uq_classifier_categories_corp_code UNIQUE (corp_no, code);

COMMIT;
