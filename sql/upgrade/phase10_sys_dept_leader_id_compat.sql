ALTER TABLE sys_dept
    ADD COLUMN IF NOT EXISTS leader_id BIGINT;

UPDATE sys_dept
SET leader_id = leader::BIGINT
WHERE leader_id IS NULL
  AND leader IS NOT NULL
  AND btrim(leader) <> ''
  AND leader ~ '^[0-9]+$';
