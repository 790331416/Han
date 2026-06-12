package com.han.system.builtin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysDictTypePo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Registry for built-in dictionary values that must exist for reusable system modules.
 */
@Component
@RequiredArgsConstructor
public class SysBuiltinDictRegistry {

    private static final int STATUS_ENABLED = 0;

    private static final List<DictDefinition> DEFINITIONS = List.of(
            new DictDefinition("AI模型类型", "ai_model_type", "AI模型管理模型类型列表", List.of(
                    new DictItem("大语言模型", "LLM", 10, "primary"),
                    new DictItem("图片生成模型", "IMAGE", 20, "success"),
                    new DictItem("视频生成模型", "VIDEO", 30, "warning"),
                    new DictItem("视频剪辑合成", "VIDEO_EDIT", 40, "warning"),
                    new DictItem("向量模型", "EMBEDDING", 50, "info"),
                    new DictItem("重排模型", "RERANK", 60, "info"),
                    new DictItem("语音合成", "TTS", 70, "success"),
                    new DictItem("语音识别", "STT", 80, "info")
            )),
            new DictDefinition("AI模型供应商", "ai_model_provider", "AI模型管理供应商列表", List.of(
                    new DictItem("OpenAI", "openai", 10, "primary"),
                    new DictItem("火山引擎/方舟", "volcengine", 20, "warning"),
                    new DictItem("DeepSeek", "deepseek", 30, "success"),
                    new DictItem("通义千问", "qwen", 40, "success"),
                    new DictItem("智谱AI", "zhipu", 50, "primary"),
                    new DictItem("百度千帆", "baidu", 60, "primary"),
                    new DictItem("Ollama", "ollama", 70, "info"),
                    new DictItem("Azure OpenAI", "azure", 80, "primary"),
                    new DictItem("Anthropic", "anthropic", 90, "info"),
                    new DictItem("SiliconFlow", "siliconflow", 100, "success"),
                    new DictItem("Coze(扣子)", "coze", 110, "warning"),
                    new DictItem("DIFY", "dify", 120, "info"),
                    new DictItem("FastGPT", "fastgpt", 130, "info")
            )),
            new DictDefinition("AI Prompt模板分类", "ai_prompt_category", "AI Prompt模板分类列表", List.of(
                    new DictItem("系统提示词", "system", 10, "primary"),
                    new DictItem("用户模板", "user", 20, "success"),
                    new DictItem("助手模板", "assistant", 30, "warning"),
                    new DictItem("AIVideo 文本润色", "aivideo_text", 40, "primary"),
                    new DictItem("AIVideo 剧本生成", "aivideo_script", 50, "primary"),
                    new DictItem("AIVideo 资产提取", "aivideo_asset", 60, "success"),
                    new DictItem("AIVideo 分镜提取", "aivideo_storyboard", 70, "warning"),
                    new DictItem("AIVideo 图片生成", "aivideo_image", 80, "success"),
                    new DictItem("AIVideo 视频生成", "aivideo_video", 90, "warning"),
                    new DictItem("AIVideo 语音合成", "aivideo_tts", 100, "info")
            ))
    );

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    public static List<DictDefinition> definitions() {
        return DEFINITIONS;
    }

    @Transactional(rollbackFor = Exception.class)
    public synchronized void ensureBuiltInDictionaries() {
        Long tenantId = resolveTenantId();
        for (DictDefinition definition : DEFINITIONS) {
            ensureDictType(definition, tenantId);
            ensureDictItems(definition, tenantId);
        }
    }

    private Long resolveTenantId() {
        Long tenantId = TenantHelper.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }

    private void ensureDictType(DictDefinition definition, Long tenantId) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictTypePo>()
                .eq(SysDictTypePo::getDictType, definition.dictType()));
        if (count != null && count > 0) {
            return;
        }
        SysDictTypePo type = new SysDictTypePo();
        type.setTenantId(tenantId);
        type.setDictName(definition.dictName());
        type.setDictType(definition.dictType());
        type.setStatus(STATUS_ENABLED);
        type.setRemark(definition.remark());
        dictTypeMapper.insert(type);
    }

    private void ensureDictItems(DictDefinition definition, Long tenantId) {
        for (DictItem item : definition.items()) {
            Long count = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictDataPo>()
                    .eq(SysDictDataPo::getDictType, definition.dictType())
                    .eq(SysDictDataPo::getDictValue, item.dictValue()));
            if (count != null && count > 0) {
                continue;
            }
            SysDictDataPo data = new SysDictDataPo();
            data.setTenantId(tenantId);
            data.setDictType(definition.dictType());
            data.setDictLabel(item.dictLabel());
            data.setDictValue(item.dictValue());
            data.setDictSort(item.dictSort());
            data.setListClass(item.listClass());
            data.setStatus(STATUS_ENABLED);
            dictDataMapper.insert(data);
        }
    }

    public record DictDefinition(String dictName, String dictType, String remark, List<DictItem> items) {
    }

    public record DictItem(String dictLabel, String dictValue, Integer dictSort, String listClass) {
    }
}
