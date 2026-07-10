-- AI short-drama controlled media preview access.
-- Idempotent. No API keys or secrets are stored here.

ALTER TABLE ai_video_project_setting
    ADD COLUMN IF NOT EXISTS media_access_policy VARCHAR(20) DEFAULT 'PRIVATE';

UPDATE ai_video_project_setting
SET media_access_policy = 'PRIVATE',
    update_time = CURRENT_TIMESTAMP
WHERE media_access_policy IS NULL
   OR media_access_policy NOT IN ('PRIVATE', 'PUBLIC');

UPDATE ai_video_project_setting
SET image_candidate_count = 2,
    update_time = CURRENT_TIMESTAMP
WHERE COALESCE(image_candidate_count, 0) = 3;

UPDATE ai_video_project
SET candidate_image_count = 2,
    update_time = CURRENT_TIMESTAMP
WHERE COALESCE(candidate_image_count, 0) = 3;

UPDATE ai_video_media_asset
SET file_url = substring(file_url from '(/file/public/.*)$'),
    update_time = CURRENT_TIMESTAMP
WHERE file_url LIKE 'http://%/file/public/%'
  AND substring(file_url from '(/file/public/.*)$') IS NOT NULL;
