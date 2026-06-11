-- AIVideo post-production speech timing and character voice assets.
-- Purpose: let final edit place TTS audio at exact in-shot time ranges and inherit stable voices from character assets.

ALTER TABLE ai_video_shot
    ADD COLUMN IF NOT EXISTS tts_start_ms INT;

ALTER TABLE ai_video_shot
    ADD COLUMN IF NOT EXISTS tts_end_ms INT;

ALTER TABLE ai_video_shot
    ADD COLUMN IF NOT EXISTS tts_speaker VARCHAR(128);

ALTER TABLE ai_video_shot
    ADD COLUMN IF NOT EXISTS tts_voice_type VARCHAR(128);

COMMENT ON COLUMN ai_video_shot.tts_start_ms IS 'In-shot TTS start time in milliseconds';
COMMENT ON COLUMN ai_video_shot.tts_end_ms IS 'In-shot TTS end time in milliseconds';
COMMENT ON COLUMN ai_video_shot.tts_speaker IS 'Resolved speaker name for post-production TTS';
COMMENT ON COLUMN ai_video_shot.tts_voice_type IS 'Resolved Volcengine voice_type or cloned voice ID for this shot';

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_mode VARCHAR(32);

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_type VARCHAR(128);

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_name VARCHAR(128);

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_desc VARCHAR(512);

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_reference_media_id BIGINT;

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_sample_text VARCHAR(512);

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_speed_ratio NUMERIC(6,3);

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_volume_ratio NUMERIC(6,3);

ALTER TABLE ai_video_character
    ADD COLUMN IF NOT EXISTS voice_pitch_ratio NUMERIC(6,3);

COMMENT ON COLUMN ai_video_character.voice_mode IS 'Character voice mode: SYSTEM_TTS/VOICE_CLONE/REFERENCE_AUDIO/NONE';
COMMENT ON COLUMN ai_video_character.voice_type IS 'Volcengine TTS voice_type or voice clone ID';
COMMENT ON COLUMN ai_video_character.voice_name IS 'Human-readable voice label';
COMMENT ON COLUMN ai_video_character.voice_desc IS 'Voice style description for prompts and review';
COMMENT ON COLUMN ai_video_character.voice_reference_media_id IS 'Optional media asset ID for voice reference sample';
COMMENT ON COLUMN ai_video_character.voice_sample_text IS 'Optional sample line used for voice asset generation';
COMMENT ON COLUMN ai_video_character.voice_speed_ratio IS 'Default speech speed ratio for this character';
COMMENT ON COLUMN ai_video_character.voice_volume_ratio IS 'Default speech volume ratio for this character';
COMMENT ON COLUMN ai_video_character.voice_pitch_ratio IS 'Default speech pitch ratio for this character';
