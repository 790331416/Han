-- AI short-drama MVP 0 schema.
-- Scope: full tier / deployed PostgreSQL upgrade.
-- This script is idempotent and does not seed API keys or provider secrets.

CREATE TABLE IF NOT EXISTS ai_video_project (
    project_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    project_name VARCHAR(200) NOT NULL,
    owner_user_id BIGINT,
    topic_type VARCHAR(50),
    target_platform VARCHAR(50),
    default_ratio VARCHAR(20) DEFAULT '9:16',
    default_style VARCHAR(100),
    default_shot_duration INT DEFAULT 5,
    candidate_image_count INT DEFAULT 3,
    preview_mode CHAR(1) DEFAULT '1',
    current_stage VARCHAR(32) DEFAULT 'DRAFT',
    project_status VARCHAR(32) DEFAULT 'DRAFT',
    budget_limit NUMERIC(12,4),
    estimated_cost NUMERIC(12,4) DEFAULT 0,
    actual_cost NUMERIC(12,4) DEFAULT 0,
    summary TEXT,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0,
    remark VARCHAR(500)
);

COMMENT ON TABLE ai_video_project IS 'AI short-drama project';

CREATE INDEX IF NOT EXISTS idx_ai_video_project_tenant ON ai_video_project (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_project_owner ON ai_video_project (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_project_status ON ai_video_project (project_status, current_stage);
CREATE INDEX IF NOT EXISTS idx_ai_video_project_update_time ON ai_video_project (update_time);

CREATE TABLE IF NOT EXISTS ai_video_source_document (
    document_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    source_type VARCHAR(20) DEFAULT 'TEXT',
    file_id BIGINT,
    file_name VARCHAR(200),
    raw_text TEXT,
    parsed_text TEXT,
    chapter_json TEXT,
    char_count BIGINT DEFAULT 0,
    parse_status VARCHAR(32) DEFAULT 'PENDING',
    parse_error TEXT,
    confirmed CHAR(1) DEFAULT '0',
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_source_document IS 'AI short-drama source document';

CREATE INDEX IF NOT EXISTS idx_ai_video_doc_project ON ai_video_source_document (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_doc_tenant ON ai_video_source_document (tenant_id);

CREATE TABLE IF NOT EXISTS ai_video_content_version (
    version_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    document_id BIGINT,
    content_type VARCHAR(32) NOT NULL,
    version_no INT DEFAULT 1,
    title VARCHAR(200),
    content_text TEXT,
    content_json TEXT,
    prompt_template_id BIGINT,
    custom_prompt TEXT,
    model_id BIGINT,
    task_id BIGINT,
    selected CHAR(1) DEFAULT '0',
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_content_version IS 'AI short-drama text content version';

CREATE INDEX IF NOT EXISTS idx_ai_video_content_project_type ON ai_video_content_version (project_id, content_type);
CREATE INDEX IF NOT EXISTS idx_ai_video_content_task ON ai_video_content_version (task_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_content_selected ON ai_video_content_version (selected);

CREATE TABLE IF NOT EXISTS ai_video_character (
    character_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    character_name VARCHAR(100) NOT NULL,
    gender VARCHAR(20),
    age_desc VARCHAR(50),
    identity_desc VARCHAR(200),
    personality_tags VARCHAR(500),
    story_role VARCHAR(100),
    relationship_desc TEXT,
    appearance TEXT,
    hair_style VARCHAR(200),
    costume TEXT,
    color_style VARCHAR(200),
    negative_traits TEXT,
    prompt_text TEXT,
    completeness VARCHAR(32),
    missing_fields TEXT,
    locked_media_id BIGINT,
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    sort_order INT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_character IS 'AI short-drama character asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_character_project ON ai_video_character (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_character_locked_media ON ai_video_character (locked_media_id);

CREATE TABLE IF NOT EXISTS ai_video_scene (
    scene_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    scene_name VARCHAR(200) NOT NULL,
    scene_type VARCHAR(100),
    episode_no INT,
    time_desc VARCHAR(100),
    weather VARCHAR(100),
    atmosphere VARCHAR(200),
    visual_features TEXT,
    color_tone VARCHAR(200),
    props TEXT,
    negative_elements TEXT,
    prompt_text TEXT,
    completeness VARCHAR(32),
    missing_fields TEXT,
    locked_media_id BIGINT,
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    sort_order INT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_scene IS 'AI short-drama scene asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_scene_project ON ai_video_scene (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_scene_locked_media ON ai_video_scene (locked_media_id);

CREATE TABLE IF NOT EXISTS ai_video_shot (
    shot_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    episode_no INT DEFAULT 1,
    shot_no INT DEFAULT 1,
    duration_sec INT DEFAULT 5,
    scene_id BIGINT,
    character_ids TEXT,
    shot_type VARCHAR(100),
    camera_position VARCHAR(100),
    camera_movement VARCHAR(100),
    action_desc TEXT,
    dialogue TEXT,
    voice_over TEXT,
    emotion VARCHAR(200),
    prompt_text TEXT,
    reference_media_ids TEXT,
    keyframe_media_id BIGINT,
    video_media_id BIGINT,
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    generation_status VARCHAR(32) DEFAULT 'PENDING',
    sort_order INT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_shot IS 'AI short-drama shot asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_shot_project_episode ON ai_video_shot (project_id, episode_no, shot_no);
CREATE INDEX IF NOT EXISTS idx_ai_video_shot_scene ON ai_video_shot (scene_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_shot_status ON ai_video_shot (generation_status);

CREATE TABLE IF NOT EXISTS ai_video_media_asset (
    media_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    asset_type VARCHAR(32) NOT NULL,
    biz_type VARCHAR(32),
    biz_id BIGINT,
    file_id BIGINT,
    file_url VARCHAR(1000),
    thumbnail_file_id BIGINT,
    prompt_text TEXT,
    negative_prompt TEXT,
    model_id BIGINT,
    task_id BIGINT,
    params_json TEXT,
    candidate_no INT DEFAULT 1,
    selected CHAR(1) DEFAULT '0',
    asset_status VARCHAR(32) DEFAULT 'READY',
    cost_amount NUMERIC(12,4) DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_media_asset IS 'AI short-drama media asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_media_project ON ai_video_media_asset (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_media_biz ON ai_video_media_asset (biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_media_file ON ai_video_media_asset (file_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_media_selected ON ai_video_media_asset (selected);

CREATE TABLE IF NOT EXISTS ai_video_generation_task (
    task_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT,
    tenant_id BIGINT DEFAULT 0,
    task_type VARCHAR(32) NOT NULL,
    biz_type VARCHAR(32),
    biz_id BIGINT,
    model_id BIGINT,
    prompt_template_id BIGINT,
    prompt_text TEXT,
    custom_prompt TEXT,
    params_json TEXT,
    provider_task_id VARCHAR(200),
    job_id BIGINT,
    task_status VARCHAR(32) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    estimated_cost NUMERIC(12,4) DEFAULT 0,
    actual_cost NUMERIC(12,4) DEFAULT 0,
    token_count INT,
    error_code VARCHAR(100),
    error_message TEXT,
    started_time TIMESTAMP,
    finished_time TIMESTAMP,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_generation_task IS 'AI short-drama generation task';

CREATE INDEX IF NOT EXISTS idx_ai_video_task_project ON ai_video_generation_task (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_task_status ON ai_video_generation_task (task_status);
CREATE INDEX IF NOT EXISTS idx_ai_video_task_type ON ai_video_generation_task (task_type);
CREATE INDEX IF NOT EXISTS idx_ai_video_task_provider ON ai_video_generation_task (provider_task_id);

CREATE TABLE IF NOT EXISTS ai_video_review_record (
    review_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT,
    action_type VARCHAR(32) NOT NULL,
    before_status VARCHAR(32),
    after_status VARCHAR(32),
    comment TEXT,
    extra_prompt TEXT,
    review_user_id BIGINT,
    review_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_video_review_record IS 'AI short-drama review record';

CREATE INDEX IF NOT EXISTS idx_ai_video_review_project ON ai_video_review_record (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_review_target ON ai_video_review_record (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_review_user ON ai_video_review_record (review_user_id);

CREATE TABLE IF NOT EXISTS ai_video_project_setting (
    setting_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT,
    tenant_id BIGINT DEFAULT 0,
    text_model_id BIGINT,
    image_model_id BIGINT,
    video_model_id BIGINT,
    polish_prompt_template_id BIGINT,
    script_prompt_template_id BIGINT,
    character_prompt_template_id BIGINT,
    scene_prompt_template_id BIGINT,
    shot_prompt_template_id BIGINT,
    video_prompt_template_id BIGINT,
    default_ratio VARCHAR(20) DEFAULT '9:16',
    default_resolution VARCHAR(50) DEFAULT '720p',
    default_shot_duration INT DEFAULT 5,
    image_candidate_count INT DEFAULT 3,
    video_candidate_count INT DEFAULT 1,
    preview_mode CHAR(1) DEFAULT '1',
    content_audit_enabled CHAR(1) DEFAULT '1',
    params_json TEXT,
    remark VARCHAR(500),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_video_project_setting IS 'AI short-drama project setting snapshot';

CREATE INDEX IF NOT EXISTS idx_ai_video_setting_project ON ai_video_project_setting (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_setting_tenant_global ON ai_video_project_setting (tenant_id, project_id);
