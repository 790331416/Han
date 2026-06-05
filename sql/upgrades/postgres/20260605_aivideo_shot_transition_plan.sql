ALTER TABLE ai_video_shot
    ADD COLUMN IF NOT EXISTS transition_before_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS transition_before_desc TEXT,
    ADD COLUMN IF NOT EXISTS transition_effect VARCHAR(64),
    ADD COLUMN IF NOT EXISTS stitch_group_no INT;

COMMENT ON COLUMN ai_video_shot.transition_before_type IS 'Transition relation before this shot: OPENING/CONTINUE/SCENE_CUT/TIME_JUMP/MONTAGE/INSERT';
COMMENT ON COLUMN ai_video_shot.transition_before_desc IS 'Human-readable transition description before this shot';
COMMENT ON COLUMN ai_video_shot.transition_effect IS 'Suggested post-edit transition effect';
COMMENT ON COLUMN ai_video_shot.stitch_group_no IS 'Continuous stitching group number for post-edit planning';

WITH ordered AS (
    SELECT
        shot_id,
        project_id,
        episode_no,
        shot_no,
        sort_order,
        scene_id,
        character_ids,
        LAG(scene_id) OVER (PARTITION BY project_id ORDER BY shot_no, sort_order, shot_id) AS prev_scene_id,
        LAG(character_ids) OVER (PARTITION BY project_id ORDER BY shot_no, sort_order, shot_id) AS prev_character_ids
    FROM ai_video_shot
    WHERE del_flag = 0
),
normalized AS (
    SELECT
        ordered.*,
        ARRAY(
            SELECT token
            FROM regexp_split_to_table(regexp_replace(coalesce(character_ids, ''), '\s+', '', 'g'), '[,，、]+') AS tokens(token)
            WHERE token <> ''
        ) AS current_character_tokens,
        ARRAY(
            SELECT token
            FROM regexp_split_to_table(regexp_replace(coalesce(prev_character_ids, ''), '\s+', '', 'g'), '[,，、]+') AS tokens(token)
            WHERE token <> ''
        ) AS previous_character_tokens
    FROM ordered
),
typed AS (
    SELECT
        shot_id,
        prev_scene_id,
        CASE
            WHEN prev_scene_id IS NULL THEN 'OPENING'
            WHEN scene_id IS DISTINCT FROM prev_scene_id THEN 'SCENE_CUT'
            WHEN cardinality(current_character_tokens) > 0
                AND (
                    cardinality(previous_character_tokens) = 0
                    OR EXISTS (
                        SELECT 1
                        FROM unnest(current_character_tokens) AS current_token
                        WHERE NOT current_token = ANY(previous_character_tokens)
                    )
                ) THEN 'INSERT'
            ELSE 'CONTINUE'
        END AS inferred_type
    FROM normalized
),
grouped AS (
    SELECT
        s.shot_id,
        s.scene_id,
        t.prev_scene_id,
        t.inferred_type,
        SUM(CASE WHEN t.inferred_type IN ('OPENING', 'CONTINUE') THEN 0 ELSE 1 END)
            OVER (PARTITION BY s.project_id ORDER BY s.shot_no, s.sort_order, s.shot_id) + 1 AS inferred_group_no
    FROM ai_video_shot s
    JOIN typed t ON t.shot_id = s.shot_id
    WHERE s.del_flag = 0
)
UPDATE ai_video_shot s
SET
    transition_before_type = COALESCE(NULLIF(s.transition_before_type, ''), g.inferred_type),
    transition_before_desc = COALESCE(NULLIF(s.transition_before_desc, ''),
        CASE
            WHEN g.inferred_type = 'OPENING' THEN '开场镜头，建立当前场景。'
            WHEN g.inferred_type = 'CONTINUE' THEN '延续上一镜头，同一场景内连续动作。'
            WHEN g.inferred_type = 'INSERT' THEN '同场景切人/插入镜头，不强制继承上一尾帧。'
            ELSE '明确切场：' || coalesce(prev_scene.scene_name, '上一场景')
                || ' -> ' || coalesce(current_scene.scene_name, '当前场景') || '。'
        END),
    transition_effect = COALESCE(NULLIF(s.transition_effect, ''), 'hard_cut'),
    stitch_group_no = COALESCE(s.stitch_group_no, g.inferred_group_no)
FROM grouped g
LEFT JOIN ai_video_scene prev_scene ON prev_scene.scene_id = g.prev_scene_id
LEFT JOIN ai_video_scene current_scene ON current_scene.scene_id = g.scene_id
WHERE s.shot_id = g.shot_id;
