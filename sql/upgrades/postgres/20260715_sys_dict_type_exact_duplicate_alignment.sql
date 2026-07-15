-- Soft-delete only exact duplicate active dictionary types.
-- Conflicting rows with different metadata remain active so rehearsal assertions can surface them for manual review.

BEGIN;

WITH ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY tenant_id, dict_type, dict_name, status, remark
            ORDER BY id
        ) AS duplicate_rank
    FROM sys_dict_type
    WHERE COALESCE(del_flag, 0) = 0
),
exact_duplicates AS (
    SELECT id
    FROM ranked
    WHERE duplicate_rank > 1
)
UPDATE sys_dict_type target
SET del_flag = 1
FROM exact_duplicates duplicate
WHERE target.id = duplicate.id;

COMMIT;