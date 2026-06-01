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
