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
        String upgrade = Files.readString(
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

        assertContains(upgrade, "谁递给谁");
        assertContains(upgrade, "最后谁拿着");
        assertContains(upgrade, "上一镜背对");
        assertContains(upgrade, "道具颜色");
        assertContains(upgrade, "referenceAudioUrls");
        assertContains(upgrade, "最多 3 段");
        assertContains(upgrade, "插入镜头/交接镜头");
        assertContains(upgrade, "AI短剧后期语音合成");
        assertContains(upgrade, "aivideo_tts");
        assertContains(upgrade, "MAX(template_id)");
        assertContains(upgrade, "video_prompt_template_id");
        assertDoesNotContain(upgrade, "MAX(id)");
        assertDoesNotContain(upgrade, "asset_prompt_template_id");
        assertDoesNotContain(upgrade, "shot_video_prompt_template_id");
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
    }

    private static void assertContains(String content, String needle) {
        assertTrue(content.contains(needle), () -> "SQL should contain hard rule: " + needle);
    }

    private static void assertDoesNotContain(String content, String needle) {
        assertFalse(content.contains(needle), () -> "SQL should not contain obsolete column/expression: " + needle);
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
