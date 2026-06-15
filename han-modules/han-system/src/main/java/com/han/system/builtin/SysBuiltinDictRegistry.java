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
 * <p>这里维护 Han 公共模块和 AIVideo 模块依赖的基础字典值，目标是让：
 * <ul>
 *     <li>全量初始化 SQL 与运行期兜底保持一致；</li>
 *     <li>新租户首次访问字典接口时也能自动补齐必要字典；</li>
 *     <li>前端公共下拉选项逐步从写死常量迁移到字典中心。</li>
 * </ul>
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
     * 所有运行期需要兜底补齐的内置字典定义。
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
                    item("助手模板", "assistant", 30, "warning"),
                    item("AIVideo 文本润色", "aivideo_text", 40, "primary"),
                    item("AIVideo 剧本生成", "aivideo_script", 50, "primary"),
                    item("AIVideo 资产提取", "aivideo_asset", 60, "success"),
                    item("AIVideo 分镜提取", "aivideo_storyboard", 70, "warning"),
                    item("AIVideo 图片生成", "aivideo_image", 80, "success"),
                    item("AIVideo 视频生成", "aivideo_video", 90, "warning"),
                    item("AIVideo 语音合成", "aivideo_tts", 100, "info")
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
            )),
            definition("AIVideo 项目阶段", "aivideo_project_stage", "AI短剧项目阶段列表", List.of(
                    item("草稿", "DRAFT", 10, "info"),
                    item("原文已保存", "DOCUMENT_SAVED", 20, "primary"),
                    item("文档已确认", "DOCUMENT_PARSED", 30, "primary"),
                    item("润色已确认", "POLISH_CONFIRMED", 40, "success"),
                    item("剧本已确认", "SCRIPT_CONFIRMED", 50, "success"),
                    item("资产已确认", "ASSET_CONFIRMED", 60, "success"),
                    item("视频生成中", "VIDEO_GENERATING", 70, "warning"),
                    item("视频已确认", "VIDEO_CONFIRMED", 80, "success")
            )),
            definition("AIVideo 项目状态", "aivideo_project_status", "AI短剧项目状态列表", List.of(
                    item("草稿", "DRAFT", 10, "info"),
                    item("进行中", "RUNNING", 20, "warning"),
                    item("暂停", "PAUSED", 30, "info"),
                    item("已完成", "FINISHED", 40, "success"),
                    item("已归档", "ARCHIVED", 50, "danger")
            )),
            definition("AIVideo 任务状态", "aivideo_task_status", "AI短剧任务状态列表", List.of(
                    item("待执行", "PENDING", 10, "info"),
                    item("执行中", "RUNNING", 20, "warning"),
                    item("成功", "SUCCESS", 30, "success"),
                    item("失败", "FAILED", 40, "danger"),
                    item("已取消", "CANCELED", 50, "info")
            )),
            definition("AIVideo 画幅", "aivideo_ratio", "AI短剧项目画幅列表", List.of(
                    item("9:16", "9:16", 10, "primary"),
                    item("16:9", "16:9", 20, "success"),
                    item("1:1", "1:1", 30, "info"),
                    item("4:3", "4:3", 40, "warning")
            )),
            definition("AIVideo 清晰度", "aivideo_resolution", "AI短剧项目清晰度列表", List.of(
                    item("720p", "720p", 10, "primary"),
                    item("1080p", "1080p", 20, "success"),
                    item("2K", "2K", 30, "warning")
            )),
            definition("AIVideo 视觉风格", "aivideo_visual_style", "AI短剧视觉风格列表", List.of(
                    item("写实电影感", "写实电影感", 10, "primary"),
                    item("3D 国漫 CG", "3D 国漫 CG", 20, "success"),
                    item("2D 日漫", "2D 日漫", 30, "warning"),
                    item("复古胶片", "复古胶片", 40, "info"),
                    item("赛博朋克", "赛博朋克", 50, "danger"),
                    item("童话绘本", "童话绘本", 60, "success"),
                    item("国风水墨", "国风水墨", 70, "primary")
            )),
            definition("AIVideo 生成策略", "aivideo_generation_strategy", "AI短剧视频生成策略列表", List.of(
                    item("自动", "AUTO", 10, "primary"),
                    item("视频延长", "VIDEO_EXTEND", 20, "warning"),
                    item("分段拼接", "SEGMENT_STITCH", 30, "success"),
                    item("轨道补齐", "TRACK_FILL", 40, "info")
            )),
            definition("AIVideo 声音模式", "aivideo_audio_mode", "AI短剧声音模式列表", List.of(
                    item("静音", "SILENT", 10, "info"),
                    item("原生有声", "NATIVE_AUDIO", 20, "success"),
                    item("参考音频有声", "REFERENCE_AUDIO", 30, "warning"),
                    item("后期 TTS", "POST_TTS", 40, "primary")
            )),
            definition("AIVideo 字幕模式", "aivideo_subtitle_mode", "AI短剧字幕模式列表", List.of(
                    item("无字幕", "NONE", 10, "info"),
                    item("底部字幕", "BOTTOM", 20, "primary"),
                    item("气泡台词", "BUBBLE", 30, "success"),
                    item("标题文字", "TITLE", 40, "warning")
            )),
            definition("AIVideo 参考素材策略", "aivideo_reference_strategy", "AI短剧参考素材策略列表", List.of(
                    item("角色锚定", "CHARACTER_ANCHOR", 10, "primary"),
                    item("场景定调", "SCENE_TONE", 20, "success"),
                    item("运镜参考", "CAMERA_REFERENCE", 30, "warning"),
                    item("动作参考", "ACTION_REFERENCE", 40, "info"),
                    item("音频参考", "AUDIO_REFERENCE", 50, "warning"),
                    item("角色 + 场景", "CHARACTER_SCENE", 60, "primary")
            )),
            definition("AIVideo 动作强度", "aivideo_action_intensity", "AI短剧动作强度列表", List.of(
                    item("低缓动作", "LOW", 10, "info"),
                    item("普通动作", "NORMAL", 20, "primary"),
                    item("强动作", "STRONG", 30, "warning")
            )),
            definition("AIVideo 连续性强度", "aivideo_continuity_level", "AI短剧连续性强度列表", List.of(
                    item("普通", "NORMAL", 10, "info"),
                    item("严格", "STRICT", 20, "primary"),
                    item("极严格", "ULTRA_STRICT", 30, "warning")
            )),
            definition("AIVideo 多角色策略", "aivideo_multi_role_strategy", "AI短剧多角色策略列表", List.of(
                    item("单角色优先", "SINGLE_FIRST", 10, "primary"),
                    item("多角色允许", "MULTI_ALLOWED", 20, "success"),
                    item("超过 4 人自动拆镜", "SPLIT_OVER_FOUR", 30, "warning")
            )),
            definition("AIVideo 角色造型类型", "aivideo_character_design_type", "AI短剧角色造型类型列表", List.of(
                    item("自动", "AUTO", 10, "info"),
                    item("写实自然比例", "REALISTIC_NATURAL", 20, "primary"),
                    item("半写实卡通", "SEMI_REAL_CARTOON", 30, "success"),
                    item("3D动漫/国漫CG", "THREE_D_ANIME_CG", 40, "warning"),
                    item("2D动漫/日漫", "TWO_D_ANIME", 50, "warning"),
                    item("Q版萌系全身", "CHIBI_FULL_BODY", 60, "success"),
                    item("低龄儿童绘本", "CHILDREN_PICTURE_BOOK", 70, "info"),
                    item("动物本体萌化", "ANIMAL_BODY_CUTE", 80, "success"),
                    item("拟人化角色", "ANTHROPOMORPHIC", 90, "primary"),
                    item("怪物/夸张反派", "MONSTER_VILLAIN", 100, "danger")
            )),
            definition("AIVideo 素材访问策略", "aivideo_media_access_policy", "AI短剧素材访问策略列表", List.of(
                    item("登录可见", "PRIVATE", 10, "info"),
                    item("公开可见", "PUBLIC", 20, "success")
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
