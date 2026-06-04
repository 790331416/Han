package com.han.aivideo.service.impl;

import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for AI short-drama services.
 */
abstract class AivideoServiceSupport {

    protected static final int DEL_FLAG_NORMAL = 0;
    protected static final String YES = "1";
    protected static final String NO = "0";
    protected static final String PARAM_DEFAULT_STYLE = "defaultStyle";
    protected static final String PARAM_GENERATION_STRATEGY = "generationStrategy";
    protected static final String PARAM_AUDIO_MODE = "audioMode";
    protected static final String PARAM_SUBTITLE_MODE = "subtitleMode";
    protected static final String PARAM_REFERENCE_STRATEGY = "referenceStrategy";
    protected static final String PARAM_ACTION_INTENSITY = "actionIntensity";
    protected static final String PARAM_CONTINUITY_LEVEL = "continuityLevel";
    protected static final String PARAM_MULTI_ROLE_STRATEGY = "multiRoleStrategy";
    protected static final String PARAM_CHARACTER_DESIGN_TYPE = "characterDesignType";
    protected static final String PARAM_GLOBAL_PROMPT = "globalPrompt";
    protected static final String PARAM_POLISH_PROMPT = "polishPrompt";
    protected static final String PARAM_SCRIPT_PROMPT = "scriptPrompt";
    protected static final String PARAM_ASSET_PROMPT = "assetPrompt";
    protected static final String PARAM_CHARACTER_IMAGE_PROMPT = "characterImagePrompt";
    protected static final String PARAM_SCENE_IMAGE_PROMPT = "sceneImagePrompt";
    protected static final String PARAM_SHOT_VIDEO_PROMPT = "shotVideoPrompt";

    protected static final String DEFAULT_STYLE = "写实电影感";
    protected static final String DEFAULT_GENERATION_STRATEGY = "AUTO";
    protected static final String DEFAULT_AUDIO_MODE = "SILENT";
    protected static final String DEFAULT_SUBTITLE_MODE = "NONE";
    protected static final String DEFAULT_REFERENCE_STRATEGY = "CHARACTER_SCENE";
    protected static final String DEFAULT_ACTION_INTENSITY = "NORMAL";
    protected static final String DEFAULT_CONTINUITY_LEVEL = "STRICT";
    protected static final String DEFAULT_MULTI_ROLE_STRATEGY = "SINGLE_FIRST";
    protected static final String DEFAULT_CHARACTER_DESIGN_TYPE = "AUTO";

    protected int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    protected int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    protected String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    protected Map<String, Object> parseParamsJson(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = XuJsonUtil.parseObject(paramsJson, Map.class);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (RuntimeException exception) {
            return new LinkedHashMap<>();
        }
    }

    protected String strategyText(AiVideoProjectSettingPo setting, String key) {
        if (setting == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = parseParamsJson(setting.getParamsJson()).get(key);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    protected String strategyText(AiVideoProjectSettingPo projectSetting, AiVideoProjectSettingPo globalSetting,
                                  String key, String defaultValue) {
        String value = strategyText(projectSetting, key);
        if (StringUtils.hasText(value)) {
            return value;
        }
        value = strategyText(globalSetting, key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    protected void putStrategyValue(Map<String, Object> params, String key, String value, String defaultValue) {
        params.put(key, StringUtils.hasText(value) ? value.trim() : defaultValue);
    }

    protected String mergeStagePrompt(String globalPrompt, String stagePrompt) {
        String global = trimToNull(globalPrompt);
        String stage = trimToNull(stagePrompt);
        if (!StringUtils.hasText(global)) {
            return stage;
        }
        if (!StringUtils.hasText(stage)) {
            return global;
        }
        return global + "\n\n" + stage;
    }

    protected String sanitizeCharacterImagePromptText(String value) {
        if (!StringUtils.hasText(value)) {
            return "未填写";
        }
        String sanitized = value.trim();
        sanitized = sanitized.replace("头部特写", "头部特征清晰");
        sanitized = sanitized.replace("面部特写", "面部/表情特征清晰");
        sanitized = sanitized.replace("脸部特写", "脸部/表情特征清晰");
        sanitized = sanitized.replace("大头特写", "正常比例全身视图");
        sanitized = sanitized.replace("半身像", "完整全身像");
        sanitized = sanitized.replace("半身", "完整全身");
        sanitized = sanitized.replace("四方向全身转面表", "单主体视频角色锚定图");
        sanitized = sanitized.replace("四方向全身转面图", "单主体视频角色锚定图");
        sanitized = sanitized.replace("角色转面表", "单主体视频角色锚定图");
        sanitized = sanitized.replace("三视图", "单主体3/4正面全身视频锚定图");
        sanitized = sanitized.replace("多视图", "单主体3/4正面全身视频锚定图");
        sanitized = sanitized.replace("正侧背", "单主体3/4正面全身");
        sanitized = sanitized.replace("正面图、侧面图、背面图", "单主体3/4正面全身图");
        sanitized = sanitized.replace("正面、侧面、背面", "单主体3/4正面全身");
        sanitized = sanitized.replace("正面、左侧面、右侧面、背面", "单主体3/4正面全身");
        return StringUtils.hasText(sanitized) ? sanitized.trim() : "未填写";
    }

    protected String characterDesignInstruction(String value) {
        String type = StringUtils.hasText(value) ? value.trim() : DEFAULT_CHARACTER_DESIGN_TYPE;
        if (type.contains("Q版") || "CHIBI_FULL_BODY".equals(type)) {
            return "角色造型类型=Q版萌系全身：允许 2.5-4 头身、圆润轮廓、大眼、短腿和萌化比例，但必须仍是单主体完整全身视频角色锚定图；禁止只画头像、大头贴、半身像、贴纸、表情包或多角度拼图。";
        }
        if (type.contains("动物本体") || "ANIMAL_BODY_CUTE".equals(type)) {
            return "角色造型类型=动物本体萌化：在保持物种本体、四足/翅膀/尾巴等生理结构不变的前提下，允许更圆润、更亲和；禁止改成人类身体、真人脸、直立人形或穿成人类戏服，除非角色设定明确要求拟人化。";
        }
        if (type.contains("拟人") || "ANTHROPOMORPHIC".equals(type)) {
            return "角色造型类型=拟人化角色：仅当角色设定明确为拟人化时才使用；需要写清人形比例、服装和物种标志物，同时保持完整全身、单主体和一致性锚点。";
        }
        return switch (type) {
            case "REALISTIC_NATURAL" -> "角色造型类型=写实自然比例：保持真实头身比例、自然骨骼结构和可信材质；动物必须保持真实物种体态，禁止卡通大头化、玩偶化或拟人化。";
            case "SEMI_REAL_CARTOON" -> "角色造型类型=半写实卡通：允许轮廓更圆润、五官更清晰、颜色更干净，但仍保持完整全身、自然比例和可用于视频连续生成的稳定体型。";
            case "CHILDREN_PICTURE_BOOK" -> "角色造型类型=低龄儿童绘本：轮廓柔和、表情亲和、色块清爽，保持完整全身和稳定外观；禁止过度复杂纹理、恐怖化或成人写实阴影。";
            case "MONSTER_VILLAIN" -> "角色造型类型=怪物/夸张反派：允许更强剪影、夸张轮廓和压迫感，但必须锁定 2-3 个稳定标志特征，禁止随机变形、断肢错肢、同款分身或剧情动作。";
            default -> "角色造型类型=自动：根据视觉风格和角色设定选择自然比例；无论风格如何，最终都必须是单主体完整全身视频角色锚定图。";
        };
    }

    protected Long currentTenantId() {
        if (SecurityContextHolder.isAdmin()) {
            return null;
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        return tenantId != null && tenantId > 0 ? tenantId : null;
    }

    protected Long resolveTenantIdForWrite() {
        Long tenantId = SecurityContextHolder.getTenantId();
        return tenantId != null && tenantId > 0 ? tenantId : 0L;
    }

    protected Long currentUserId() {
        return SecurityContextHolder.getUserId();
    }

    protected boolean currentUserIsAdmin() {
        return SecurityContextHolder.isAdmin();
    }

    protected String resolveOperator() {
        String username = SecurityContextHolder.getUsername();
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        Long userId = SecurityContextHolder.getUserId();
        return userId != null ? String.valueOf(userId) : "system";
    }

    protected LocalDateTime now() {
        return LocalDateTime.now();
    }
}
