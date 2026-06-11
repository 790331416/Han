package com.han.system.builtin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysBuiltinDictRegistryTest {

    @Test
    void builtInRegistryContainsAiModelAndPromptDictionaryOptions() {
        assertThat(SysBuiltinDictRegistry.definitions())
                .extracting(SysBuiltinDictRegistry.DictDefinition::dictType)
                .contains("ai_model_type", "ai_model_provider", "ai_prompt_category");

        assertThat(SysBuiltinDictRegistry.definitions())
                .flatExtracting(SysBuiltinDictRegistry.DictDefinition::items)
                .extracting(SysBuiltinDictRegistry.DictItem::dictValue)
                .contains("TTS", "VIDEO_EDIT", "volcengine", "aivideo_tts");
    }

    @Test
    void fullAppWorkflowBuildsAndPushesHanSystemImage() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String workflow = Files.readString(
                repoRoot.resolve(".github/workflows/full-app-image.yml"),
                StandardCharsets.UTF_8);

        assertThat(workflow).contains("SYSTEM_IMAGE_NAME");
        assertThat(workflow).contains("han-modules/han-system/**");
        assertThat(workflow).contains("Build and push han-system image");
        assertThat(workflow).contains("mvn -B -gs settings.workspace.xml -pl han-modules/han-system -am -DskipTests package");
        assertThat(workflow).contains("docker build");
        assertThat(workflow).contains("han-modules/han-system");
        assertThat(workflow).contains("docker push \"${image}:${short_sha}\"");
    }

    @Test
    void latestUpgradeBackfillsAiDictionaryOptionsWhenDatabaseMissedInitSql() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String upgrade = Files.readString(
                repoRoot.resolve("sql/upgrades/postgres/20260611_ai_builtin_dict_alignment.sql"),
                StandardCharsets.UTF_8);

        assertThat(upgrade).contains("sys_dict_type");
        assertThat(upgrade).contains("sys_dict_data");
        assertThat(upgrade).contains("ai_model_type");
        assertThat(upgrade).contains("ai_model_provider");
        assertThat(upgrade).contains("ai_prompt_category");
        assertThat(upgrade).contains("VIDEO_EDIT");
        assertThat(upgrade).contains("TTS");
        assertThat(upgrade).contains("volcengine");
        assertThat(upgrade).contains("aivideo_tts");
        assertThat(upgrade).contains("WHERE NOT EXISTS");
    }

    private static Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve(".github/workflows/full-app-image.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + start);
    }
}
