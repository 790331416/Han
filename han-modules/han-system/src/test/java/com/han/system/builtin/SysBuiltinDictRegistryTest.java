package com.han.system.builtin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysBuiltinDictRegistryTest {

    @Test
    void builtInRegistryContainsAiAndAivideoDictionaryOptions() {
        assertThat(SysBuiltinDictRegistry.definitions())
                .extracting(SysBuiltinDictRegistry.DictDefinition::dictType)
                .contains(
                        "ai_model_type",
                        "ai_model_provider",
                        "ai_prompt_category",
                        "sys_normal_disable",
                        "ai_kb_type",
                        "ai_mcp_transport_type",
                        "ai_workflow_type",
                        "ai_knowledge_index_status",
                        "aivideo_project_stage",
                        "aivideo_project_status",
                        "aivideo_task_status",
                        "aivideo_ratio",
                        "aivideo_resolution",
                        "aivideo_visual_style",
                        "aivideo_generation_strategy",
                        "aivideo_audio_mode",
                        "aivideo_subtitle_mode",
                        "aivideo_reference_strategy",
                        "aivideo_action_intensity",
                        "aivideo_continuity_level",
                        "aivideo_multi_role_strategy",
                        "aivideo_character_design_type",
                        "aivideo_media_access_policy");

        assertThat(SysBuiltinDictRegistry.definitions())
                .flatExtracting(SysBuiltinDictRegistry.DictDefinition::items)
                .extracting(SysBuiltinDictRegistry.DictItem::dictValue)
                .contains(
                        "TTS",
                        "VIDEO_EDIT",
                        "volcengine",
                        "aivideo_tts",
                        "general",
                        "sse",
                        "advanced",
                        "completed",
                        "0",
                        "DOCUMENT_SAVED",
                        "VIDEO_GENERATING",
                        "RUNNING",
                        "SUCCESS",
                        "9:16",
                        "720p",
                        "POST_TTS",
                        "ULTRA_STRICT",
                        "CHIBI_FULL_BODY",
                        "PRIVATE",
                        "PUBLIC");
    }

    @Test
    void builtInRegistryBackfillsCurrentTenantVisibleDictionaryRows() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String registry = Files.readString(
                repoRoot.resolve("han-modules/han-system/src/main/java/com/han/system/builtin/SysBuiltinDictRegistry.java"),
                StandardCharsets.UTF_8);

        assertThat(registry).contains("TenantHelper.getTenantId()");
        assertThat(registry).contains("resolveTenantId()");
        assertThat(registry).contains("type.setTenantId(tenantId)");
        assertThat(registry).contains("data.setTenantId(tenantId)");
        assertThat(registry).contains(".eq(SysDictTypePo::getTenantId, tenantId)");
        assertThat(registry).contains(".eq(SysDictDataPo::getTenantId, tenantId)");
        assertThat(registry).doesNotContain("TenantHelper.ignore(() ->");
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
        assertThat(upgrade).contains("target_tenants");
        assertThat(upgrade).contains("sys_tenant");
        assertThat(upgrade).contains("ai_model_type");
        assertThat(upgrade).contains("ai_model_provider");
        assertThat(upgrade).contains("ai_prompt_category");
        assertThat(upgrade).contains("sys_normal_disable");
        assertThat(upgrade).contains("ai_kb_type");
        assertThat(upgrade).contains("ai_mcp_transport_type");
        assertThat(upgrade).contains("ai_workflow_type");
        assertThat(upgrade).contains("ai_knowledge_index_status");
        assertThat(upgrade).contains("VIDEO_EDIT");
        assertThat(upgrade).contains("TTS");
        assertThat(upgrade).contains("volcengine");
        assertThat(upgrade).contains("aivideo_tts");
        assertThat(upgrade).contains("aivideo_visual_style");
        assertThat(upgrade).contains("aivideo_character_design_type");
        assertThat(upgrade).contains("aivideo_media_access_policy");
        assertThat(upgrade).contains("CHIBI_FULL_BODY");
        assertThat(upgrade).contains("POST_TTS");
        assertThat(upgrade).contains("ULTRA_STRICT");
        assertThat(upgrade).contains("WHERE NOT EXISTS");
    }

    @Test
    void dictControllerSupportsDataLabelFilterForDictionaryValuePage() throws Exception {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        String controller = Files.readString(
                repoRoot.resolve("han-modules/han-system/src/main/java/com/han/system/controller/admin/ASysDictController.java"),
                StandardCharsets.UTF_8);

        assertThat(controller).contains("@RequestParam(value = \"dictLabel\", required = false) String dictLabel");
        assertThat(controller).contains(".like(dictLabel != null && !dictLabel.isEmpty(), SysDictDataPo::getDictLabel, dictLabel)");
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
