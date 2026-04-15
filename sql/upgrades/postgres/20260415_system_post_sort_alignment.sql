DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_post'
          AND column_name = 'sort'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_post'
          AND column_name = 'post_sort'
    ) THEN
        ALTER TABLE sys_post RENAME COLUMN sort TO post_sort;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_post'
          AND column_name = 'post_sort'
    ) THEN
        ALTER TABLE sys_post ADD COLUMN post_sort INT DEFAULT 0;
    END IF;

    UPDATE sys_post SET post_sort = COALESCE(post_sort, 0);
    ALTER TABLE sys_post ALTER COLUMN post_sort SET DEFAULT 0;
END $$;
