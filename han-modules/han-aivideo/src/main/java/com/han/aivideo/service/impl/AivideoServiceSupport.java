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
        return characterDesignInstruction(value, null);
    }

    protected String characterDesignInstruction(String value, String visualStyle) {
        String type = resolveCharacterDesignType(value, visualStyle);
        if (type.contains("Q版") || "CHIBI_FULL_BODY".equals(type)) {
            return "角色造型类型=Q版萌系全身：这是给 Seedance 视频生成使用的单主体角色锚定图，角色图优先于文字描述；必须是 2.5-3.5 头身、圆润轮廓、大眼、短腿、稳定萌化比例、完整全身自然站姿；必须完整露出头部/脸、躯干、双手/手指或前爪、双脚/后爪、猫耳、猫尾或其他标志物，边缘不得裁切；服装、发型、眼睛颜色、猫耳猫尾、鞋袜和 2-3 个稳定识别特征必须清楚。背景只允许纯白/浅灰/极简棚拍。禁止拉长为正常比例，禁止变成普通3D国漫少女比例，禁止真人写实，禁止头像、大头贴、半身像、贴纸、表情包、四视图、三视图、多视图、分栏设定表、多角度拼图、同款分身、复杂剧情动作、强表情、文字、水印或 logo。";
        }
        if (type.contains("3D") || "THREE_D_ANIME_CG".equals(type)) {
            return "角色造型类型=3D动漫/国漫CG：这是给 Seedance 视频生成使用的单主体 3D CG 角色锚定图，必须是完整全身、自然站姿、主体居中、体型比例稳定、骨骼可动结构清楚；材质、服装、发型、眼睛、物种标志物和 2-3 个稳定识别特征必须锁定，后续视频不得换脸或换比例。背景只允许纯白/浅灰/极简棚拍。禁止真人照片、禁止真人脸、禁止2D平面漫画、禁止赛璐璐线稿、禁止Q版大头比例、禁止粘土玩具感、禁止头像、禁止半身、禁止四视图、禁止三视图、禁止多视图、禁止分栏设定表、禁止同款分身、禁止文字、水印或 logo。";
        }
        if (type.contains("2D") || type.contains("日漫") || "TWO_D_ANIME".equals(type)) {
            return "角色造型类型=2D动漫/日漫：这是给 Seedance 视频生成使用的单主体 2D 动画角色锚定图，必须是完整全身、线稿清晰、色块稳定、赛璐璐或动画设定稿质感，角色服装、发型、眼睛、物种标志物和 2-3 个稳定识别特征必须明确；身体结构要适合后续走路、转身、抬手等视频动作。背景只允许纯白/浅灰/极简棚拍。禁止3D渲染、禁止真人照片、禁止真人脸、禁止厚重写实光影、禁止Q版大头比例、禁止漫画分镜框、禁止对白气泡、禁止贴纸、禁止头像、禁止半身、禁止四视图、禁止三视图、禁止多视图、禁止分栏设定表、禁止同款分身、禁止文字、水印或 logo。";
        }
        if (type.contains("动物本体") || "ANIMAL_BODY_CUTE".equals(type)) {
            return "角色造型类型=动物本体萌化：这是给 Seedance 视频生成使用的动物角色锚定图，必须保持真实物种本体和可运动结构，完整全身、四足/翅膀/尾巴/耳朵/爪子全部可见，允许更圆润更亲和但不能改变物种；毛色、体型、斑纹、眼睛、项圈或 2-3 个稳定标志物必须锁定。背景只允许纯白/浅灰/极简棚拍。禁止改成人类身体、真人脸、直立人形、穿成人类戏服、头像、半身、四视图、三视图、多视图、同款分身、文字、水印或 logo，除非角色设定明确要求拟人化。";
        }
        if (type.contains("拟人") || "ANTHROPOMORPHIC".equals(type)) {
            return "角色造型类型=拟人化角色：仅当角色设定明确为拟人化时才使用；必须是单主体完整全身，人形比例、服装、发型、眼睛和物种标志物同时清楚，例如猫耳、猫尾、翅膀、角或鳞片；标志物不能被省略，后续视频必须稳定继承。背景只允许纯白/浅灰/极简棚拍。禁止变成纯人类、丢失物种标志物、头像、半身、四视图、三视图、多视图、同款分身、复杂剧情场景、文字、水印或 logo。";
        }
        return switch (type) {
            case "REALISTIC_NATURAL" -> "角色造型类型=写实自然比例：这是给 Seedance 视频生成使用的单主体写实角色锚定图，必须保持真实头身比例、自然骨骼结构、可信皮肤/毛发/服装材质、完整全身和自然站姿；动物必须保持真实物种体态。背景只允许纯白/浅灰/极简棚拍。禁止Q版、动漫化、玩偶化、过度磨皮、网红写真姿势、头像、半身、四视图、三视图、多视图、同款分身、文字、水印或 logo。";
            case "SEMI_REAL_CARTOON" -> "角色造型类型=半写实卡通：这是给 Seedance 视频生成使用的单主体半写实角色锚定图，允许轮廓更圆润、五官更清晰、颜色更干净，但必须保持完整全身、自然可动比例、稳定体型和 2-3 个识别特征。背景只允许纯白/浅灰/极简棚拍。禁止Q版大头比例、纯真人照片、贴纸表情包、头像、半身、四视图、三视图、多视图、同款分身、文字、水印或 logo。";
            case "CHILDREN_PICTURE_BOOK" -> "角色造型类型=低龄儿童绘本：这是给 Seedance 视频生成使用的单主体绘本角色锚定图，必须完整全身、轮廓柔和、表情亲和、色块清爽、低复杂度、关键服装/毛色/标志物稳定。背景只允许纯白/浅灰/极简棚拍。禁止成人写实阴影、恐怖化、复杂纹理、头像、半身、四视图、三视图、多视图、贴纸表情包、同款分身、文字、水印或 logo。";
            case "MONSTER_VILLAIN" -> "角色造型类型=怪物/夸张反派：这是给 Seedance 视频生成使用的单主体怪物/反派锚定图，允许强剪影、夸张轮廓和压迫感，但必须完整全身、结构可动、锁定 2-3 个稳定标志特征；后续视频不得随机变形。背景只允许纯白/浅灰/极简棚拍。禁止断肢错肢、随机多肢、血腥、头像、半身、四视图、三视图、多视图、同款分身、复杂剧情动作、文字、水印或 logo。";
            default -> "角色造型类型=自动：根据视觉风格和角色设定选择最贴合的角色锚定图规则；无论风格如何，最终都必须是 Seedance 视频生成可用的单主体完整全身角色锚定图，背景纯白/浅灰/极简棚拍，禁止头像、半身、四视图、三视图、多视图、分栏设定表、同款分身、文字、水印或 logo。";
        };
    }

    private String resolveCharacterDesignType(String value, String visualStyle) {
        String type = StringUtils.hasText(value) ? value.trim() : DEFAULT_CHARACTER_DESIGN_TYPE;
        if (!"AUTO".equalsIgnoreCase(type)) {
            return type;
        }
        String style = StringUtils.hasText(visualStyle) ? visualStyle.trim() : "";
        if (style.contains("Q版") || style.contains("萌系")) {
            return "CHIBI_FULL_BODY";
        }
        if (style.contains("3D") || style.contains("CG") || style.contains("国漫")) {
            return "THREE_D_ANIME_CG";
        }
        if (style.contains("2D") || style.contains("日漫") || style.contains("动漫")) {
            return "TWO_D_ANIME";
        }
        if (style.contains("绘本")) {
            return "CHILDREN_PICTURE_BOOK";
        }
        if (style.contains("写实") || style.contains("电影")) {
            return "REALISTIC_NATURAL";
        }
        return DEFAULT_CHARACTER_DESIGN_TYPE;
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
