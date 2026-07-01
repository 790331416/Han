package com.han.system.builtin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysBuiltinDictRegistryTest {

    @Test
    void builtInRegistryContainsGenericDictionaryTypes() {
        assertThat(SysBuiltinDictRegistry.definitions())
                .extracting(SysBuiltinDictRegistry.DictDefinition::dictType)
                .contains(
                        "sys_normal_disable",
                        "ai_model_type",
                        "ai_model_provider",
                        "ai_prompt_category",
                        "ai_kb_type",
                        "ai_mcp_transport_type",
                        "ai_workflow_type",
                        "ai_knowledge_index_status");
    }

    @Test
    void builtInRegistryExcludesBusinessDictionaries() {
        assertThat(SysBuiltinDictRegistry.definitions())
                .extracting(SysBuiltinDictRegistry.DictDefinition::dictType)
                .noneMatch(type -> type.startsWith("aivideo_"));
    }

    @Test
    void builtInRegistryContainsGenericDictionaryValues() {
        assertThat(SysBuiltinDictRegistry.definitions())
                .flatExtracting(SysBuiltinDictRegistry.DictDefinition::items)
                .extracting(SysBuiltinDictRegistry.DictItem::dictValue)
                .contains("0", "TTS", "VIDEO_EDIT", "volcengine", "general", "sse", "advanced", "completed");
    }

    @Test
    void everyDefinitionHasItems() {
        assertThat(SysBuiltinDictRegistry.definitions())
                .allSatisfy(def -> assertThat(def.items()).isNotEmpty());
    }
}
