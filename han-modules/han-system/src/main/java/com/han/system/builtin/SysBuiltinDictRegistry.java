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
 * 内置字典注册中心。
 *
 * <p>这里维护 Han 公共模块依赖的通用基础字典值，目标是让：
 * <ul>
 *     <li>全量初始化 SQL 与运行期兜底保持一致；</li>
 *     <li>新租户首次访问字典接口时也能自动补齐必要字典；</li>
 *     <li>前端公共下拉选项逐步从写死常量迁移到字典中心。</li>
 * </ul>
 *
 * <p>仅维护与具体业务无关的通用字典；业务专属字典应由对应业务模块或业务升级 SQL 注入，
 * 不在通用底座硬编码。
 *
 * <p>注意：这里只补齐“缺失的内置项”，不会覆盖租户已经维护过的字典内容。
 */
@Component
@RequiredArgsConstructor
public class SysBuiltinDictRegistry {

    /**
     * 系统字典默认启用状态。
     */
    private static final int STATUS_ENABLED = 0;

    /**
     * 所有运行期需要兜底补齐的内置字典定义（仅通用字典）。
     */
    private static final List<DictDefinition> DEFINITIONS = List.of(
            definition("通用启停状态", "sys_normal_disable", "系统通用启停状态字典", List.of(
                    item("正常", "0", 1, "primary"),
                    item("停用", "1", 2, "danger")
            )),
            definition("AI模型类型", "ai_model_type", "AI模型管理模型类型列表", List.of(
                    item("大语言模型", "LLM", 10, "primary"),
                    item("图片生成模型", "IMAGE", 20, "success"),
                    item("视频生成模型", "VIDEO", 30, "warning"),
                    item("视频剪辑合成", "VIDEO_EDIT", 40, "warning"),
                    item("向量模型", "EMBEDDING", 50, "info"),
                    item("重排模型", "RERANK", 60, "info"),
                    item("语音合成", "TTS", 70, "success"),
                    item("语音识别", "STT", 80, "info")
            )),
            definition("AI模型供应商", "ai_model_provider", "AI模型管理供应商列表", List.of(
                    item("OpenAI", "openai", 10, "primary"),
                    item("火山引擎/方舟", "volcengine", 20, "warning"),
                    item("DeepSeek", "deepseek", 30, "success"),
                    item("通义千问", "qwen", 40, "success"),
                    item("智谱AI", "zhipu", 50, "primary"),
                    item("百度千帆", "baidu", 60, "primary"),
                    item("Ollama", "ollama", 70, "info"),
                    item("Azure OpenAI", "azure", 80, "primary"),
                    item("Anthropic", "anthropic", 90, "info"),
                    item("SiliconFlow", "siliconflow", 100, "success"),
                    item("Coze(扣子)", "coze", 110, "warning"),
                    item("DIFY", "dify", 120, "info"),
                    item("FastGPT", "fastgpt", 130, "info")
            )),
            definition("AI Prompt模板分类", "ai_prompt_category", "AI Prompt模板分类列表", List.of(
                    item("系统提示词", "system", 10, "primary"),
                    item("用户模板", "user", 20, "success"),
                    item("助手模板", "assistant", 30, "warning")
            )),
            definition("AI知识库类型", "ai_kb_type", "AI知识库类型列表", List.of(
                    item("通用知识库", "general", 10, "primary"),
                    item("QA问答库", "qa", 20, "success"),
                    item("网页爬取", "web", 30, "warning")
            )),
            definition("AI MCP传输类型", "ai_mcp_transport_type", "AI MCP 传输类型列表", List.of(
                    item("SSE", "sse", 10, "primary"),
                    item("Streamable HTTP", "streamable_http", 20, "success"),
                    item("Stdio", "stdio", 30, "info")
            )),
            definition("AI工作流类型", "ai_workflow_type", "AI工作流类型列表", List.of(
                    item("简单对话", "simple", 10, "primary"),
                    item("高级编排", "advanced", 20, "success")
            )),
            definition("AI知识库索引状态", "ai_knowledge_index_status", "AI知识库索引状态列表", List.of(
                    item("待处理", "pending", 10, "info"),
                    item("索引中", "indexing", 20, "warning"),
                    item("已完成", "completed", 30, "success"),
                    item("失败", "failed", 40, "danger")
            ))
    );

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    /**
     * 返回当前代码内置的字典定义，用于测试和脚本对齐校验。
     */
    public static List<DictDefinition> definitions() {
        return DEFINITIONS;
    }

    /**
     * 为当前租户补齐缺失的内置字典。
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized void ensureBuiltInDictionaries() {
        Long tenantId = resolveTenantId();
        for (DictDefinition definition : DEFINITIONS) {
            ensureDictType(definition, tenantId);
            ensureDictItems(definition, tenantId);
        }
    }

    /**
     * 统一把空租户视为平台租户 0，避免运行期出现 null 租户脏数据。
     */
    private Long resolveTenantId() {
        Long tenantId = TenantHelper.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }

    /**
     * 仅在当前租户缺失该字典类型时插入。
     */
    private void ensureDictType(DictDefinition definition, Long tenantId) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictTypePo>()
                .eq(SysDictTypePo::getTenantId, tenantId)
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

    /**
     * 仅在当前租户缺失该字典值时插入。
     */
    private void ensureDictItems(DictDefinition definition, Long tenantId) {
        for (DictItem item : definition.items()) {
            Long count = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictDataPo>()
                    .eq(SysDictDataPo::getTenantId, tenantId)
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

    private static DictDefinition definition(String dictName, String dictType, String remark, List<DictItem> items) {
        return new DictDefinition(dictName, dictType, remark, items);
    }

    private static DictItem item(String dictLabel, String dictValue, Integer dictSort, String listClass) {
        return new DictItem(dictLabel, dictValue, dictSort, listClass);
    }

    /**
     * 字典类型定义。
     *
     * @param dictName 字典名称
     * @param dictType 字典类型编码
     * @param remark 字典说明
     * @param items 字典值列表
     */
    public record DictDefinition(String dictName, String dictType, String remark, List<DictItem> items) {
    }

    /**
     * 字典值定义。
     *
     * @param dictLabel 字典标签
     * @param dictValue 字典值
     * @param dictSort 排序值
     * @param listClass 列表展示样式
     */
    public record DictItem(String dictLabel, String dictValue, Integer dictSort, String listClass) {
    }
}
