ALTER TABLE sys_dept
    ADD COLUMN IF NOT EXISTS leader_id BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_dept'
          AND column_name = 'leader'
    ) THEN
        UPDATE sys_dept
        SET leader_id = leader::BIGINT
        WHERE leader_id IS NULL
          AND leader IS NOT NULL
          AND btrim(leader) <> ''
          AND leader ~ '^[0-9]+$';
    END IF;
END $$;
