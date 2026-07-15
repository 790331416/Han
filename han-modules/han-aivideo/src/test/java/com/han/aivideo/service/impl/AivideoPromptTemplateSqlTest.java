package com.han.aivideo.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AivideoPromptTemplateSqlTest {

    @Test
    void fullInitAndLatestUpgradeCarryAivideoPromptHardRules() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String fullInit = Files.readString(repoRoot.resolve("sql/tiers/full/full-init.sql"), StandardCharsets.UTF_8);
        String continuityUpgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260609_aivideo_prompt_template_alignment.sql"),
                StandardCharsets.UTF_8);

        assertContains(fullInit, "谁递给谁");
        assertContains(fullInit, "最后谁拿着");
        assertContains(fullInit, "上一镜背对");
        assertContains(fullInit, "道具颜色");
        assertContains(fullInit, "referenceAudioUrls");
        assertContains(fullInit, "最多 3 段");
        assertContains(fullInit, "插入镜头/交接镜头");
        assertContains(fullInit, "AI短剧后期语音合成");
        assertContains(fullInit, "aivideo_tts");
        assertContains(fullInit, "aivideo_script");
        assertContains(fullInit, "aivideo_storyboard");
        assertContains(fullInit, "AI智能");
        assertContains(fullInit, "Prompt模板");
        assertDoesNotContain(fullInit, "AI鐭");
        assertDoesNotContain(fullInit, "妯℃澘");

        assertContains(continuityUpgrade, "谁递给谁");
        assertContains(continuityUpgrade, "最后谁拿着");
        assertContains(continuityUpgrade, "上一镜背对");
        assertContains(continuityUpgrade, "道具颜色");
        assertContains(continuityUpgrade, "referenceAudioUrls");
        assertContains(continuityUpgrade, "最多 3 段");
        assertContains(continuityUpgrade, "插入镜头/交接镜头");
        assertContains(continuityUpgrade, "AI短剧后期语音合成");
        assertContains(continuityUpgrade, "Prompt模板");
        assertDoesNotContain(continuityUpgrade, "AI鐭");
        assertDoesNotContain(continuityUpgrade, "妯℃澘");
    }

    @Test
    void fullInitAndLatestUpgradeCarryAivideoSoundDesignRules() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String fullInit = Files.readString(repoRoot.resolve("sql/tiers/full/full-init.sql"), StandardCharsets.UTF_8);
        String soundUpgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260610_aivideo_sound_design_prompt.sql"),
                StandardCharsets.UTF_8);

        assertContains(fullInit, "soundDesign");
        assertContains(fullInit, "voiceProfiles");
        assertContains(fullInit, "narrationProfile");
        assertContains(fullInit, "bgmPlan");
        assertContains(fullInit, "sfxPlan");
        assertContains(fullInit, "bgmCue");
        assertContains(fullInit, "sfxCues");
        assertContains(fullInit, "音乐音效");
        assertContains(fullInit, "剧本阶段");
        assertContains(fullInit, "后期语音合成");

        assertContains(soundUpgrade, "soundDesign");
        assertContains(soundUpgrade, "voiceProfiles");
        assertContains(soundUpgrade, "narrationProfile");
        assertContains(soundUpgrade, "bgmPlan");
        assertContains(soundUpgrade, "sfxPlan");
        assertContains(soundUpgrade, "bgmCue");
        assertContains(soundUpgrade, "sfxCues");
        assertContains(soundUpgrade, "音乐音效");
        assertContains(soundUpgrade, "剧本阶段");
        assertContains(soundUpgrade, "后期语音合成");
        assertDoesNotContain(soundUpgrade, "asset_prompt_template_id");
        assertDoesNotContain(soundUpgrade, "shot_video_prompt_template_id");
        assertDoesNotContain(soundUpgrade, "AI鐭");
        assertDoesNotContain(soundUpgrade, "妯℃澘");
    }

    @Test
    void latestUpgradeBackfillsPromptTemplateMenuPermissions() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String upgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260609_aivideo_prompt_template_alignment.sql"),
                StandardCharsets.UTF_8);

        assertContains(upgrade, "ai:prompt:list");
        assertContains(upgrade, "ai:prompt:query");
        assertContains(upgrade, "ai:prompt:add");
        assertContains(upgrade, "ai:prompt:edit");
        assertContains(upgrade, "ai:prompt:remove");
        assertContains(upgrade, "sys_role_menu");
        assertContains(upgrade, "Prompt模板");
        assertContains(upgrade, "v_ai_root_id");
        assertContains(upgrade, "v_prompt_menu_id");
    }

    @Test
    void latestUpgradeBackfillsAllAivideoPromptTemplatesWhenMissing() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String upgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260609_aivideo_prompt_template_alignment.sql"),
                StandardCharsets.UTF_8);

        assertContains(upgrade, "INSERT INTO ai_prompt_template");
        assertContains(upgrade, "WHERE NOT EXISTS");
        assertContains(upgrade, "AI短剧原文润色");
        assertContains(upgrade, "AI短剧剧本生成");
        assertContains(upgrade, "AI短剧资产提取");
        assertContains(upgrade, "AI短剧角色构建");
        assertContains(upgrade, "AI短剧场景设计");
        assertContains(upgrade, "AI短剧分镜提取");
        assertContains(upgrade, "AI短剧角色图生成");
        assertContains(upgrade, "AI短剧场景图生成");
        assertContains(upgrade, "AI短剧分镜视频生成");
        assertContains(upgrade, "AI短剧后期语音合成");
    }

    @Test
    void aivideoModelConfigSeedsIncludeTtsAndVideoEdit() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String fullInit = Files.readString(repoRoot.resolve("sql/tiers/full/full-init.sql"), StandardCharsets.UTF_8);
        String upgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260610_aivideo_model_config_alignment.sql"),
                StandardCharsets.UTF_8);

        assertContains(fullInit, "火山语音合成");
        assertContains(fullInit, "volc-tts");
        assertContains(fullInit, "火山 VOD 视频剪辑合成");
        assertContains(fullInit, "VIDEO_EDIT");
        assertContains(fullInit, "vod-direct-edit");
        assertContains(upgrade, "火山语音合成");
        assertContains(upgrade, "volc-tts");
        assertContains(upgrade, "火山 VOD 视频剪辑合成");
        assertContains(upgrade, "VIDEO_EDIT");
        assertContains(upgrade, "vod-direct-edit");
        assertContains(upgrade, "to_regclass('public.ai_model')");
        assertContains(upgrade, "IS NOT NULL");
    }

    @Test
    void builtinDictionaryUpgradeSupportsLegacyTenantColumns() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String upgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260611_ai_builtin_dict_alignment.sql"),
                StandardCharsets.UTF_8);

        assertContains(upgrade, "tmp_ai_builtin_target_tenants");
        assertContains(upgrade, "to_regclass('public.sys_tenant')");
        assertContains(upgrade, "column_name = 'id'");
        assertContains(upgrade, "column_name = 'tenant_id'");
        assertContains(upgrade, "column_name = 'deleted'");
        assertContains(upgrade, "column_name = 'del_flag'");
        assertContains(upgrade, "EXECUTE 'INSERT INTO tmp_ai_builtin_target_tenants");
        assertDoesNotContain(upgrade, "UNION ALL\n        SELECT id::BIGINT FROM sys_tenant");
        assertDoesNotContain(upgrade, "UNION ALL\n        SELECT tenant_id::BIGINT FROM sys_tenant");
    }

    @Test
    void shotSoundCueColumnsExistInFullInitAndUpgrade() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String fullInit = Files.readString(repoRoot.resolve("sql/tiers/full/full-init.sql"), StandardCharsets.UTF_8);
        String upgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260610_aivideo_shot_sound_cues.sql"),
                StandardCharsets.UTF_8);

        assertContains(fullInit, "bgm_cue TEXT");
        assertContains(fullInit, "sfx_cues TEXT");
        assertContains(upgrade, "ADD COLUMN IF NOT EXISTS bgm_cue TEXT");
        assertContains(upgrade, "ADD COLUMN IF NOT EXISTS sfx_cues TEXT");
    }

    @Test
    void propAssetSchemaAndPromptContractExistInFullInitAndUpgrade() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String fullInit = Files.readString(repoRoot.resolve("sql/tiers/full/full-init.sql"), StandardCharsets.UTF_8);
        String upgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260611_aivideo_prop_assets.sql"),
                StandardCharsets.UTF_8);

        assertContains(fullInit, "CREATE TABLE IF NOT EXISTS ai_video_prop");
        assertContains(fullInit, "prop_name VARCHAR(200) NOT NULL");
        assertContains(fullInit, "owner_character_name VARCHAR(128)");
        assertContains(fullInit, "last_holder VARCHAR(128)");
        assertContains(fullInit, "continuity_rules TEXT");
        assertContains(fullInit, "idx_ai_video_prop_project");
        assertContains(fullInit, "\"props\"");
        assertContains(fullInit, "\"propName\"");
        assertContains(fullInit, "关键道具");
        assertContains(fullInit, "最后谁拿着");

        assertContains(upgrade, "CREATE TABLE IF NOT EXISTS ai_video_prop");
        assertContains(upgrade, "prop_name VARCHAR(200) NOT NULL");
        assertContains(upgrade, "owner_character_name VARCHAR(128)");
        assertContains(upgrade, "last_holder VARCHAR(128)");
        assertContains(upgrade, "continuity_rules TEXT");
        assertContains(upgrade, "idx_ai_video_prop_project");
        assertContains(upgrade, "\"props\"");
        assertContains(upgrade, "\"propName\"");
        assertContains(upgrade, "关键道具");
        assertContains(upgrade, "最后谁拿着");
    }

    @Test
    void promptTemplateAlignmentUpgradesOnlyTouchManagedBuiltInTemplates() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String continuityUpgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260609_aivideo_prompt_template_alignment.sql"),
                StandardCharsets.UTF_8);
        String soundUpgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260610_aivideo_sound_design_prompt.sql"),
                StandardCharsets.UTF_8);
        String propUpgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260611_aivideo_prop_assets.sql"),
                StandardCharsets.UTF_8);

        assertManagedTemplateGuard(continuityUpgrade);
        assertManagedTemplateGuard(soundUpgrade);
        assertManagedTemplateGuard(propUpgrade);
    }

    private static void assertContains(String content, String needle) {
        assertTrue(content.contains(needle), () -> "SQL should contain hard rule: " + needle);
    }

    private static void assertDoesNotContain(String content, String needle) {
        assertFalse(content.contains(needle), () -> "SQL should not contain mojibake or obsolete expression: " + needle);
    }

    private static void assertManagedTemplateGuard(String content) {
        assertContains(content, "COALESCE");
        assertContains(content, "built_in");
        assertContains(content, "tenant_id");
        assertContains(content, "= 1 OR COALESCE");
    }

    private static Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("sql/tiers/full/full-init.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + start);
    }
}
