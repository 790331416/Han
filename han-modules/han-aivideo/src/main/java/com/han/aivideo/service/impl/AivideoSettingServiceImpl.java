package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.aivideo.domain.dto.AivideoAdminSettingDto;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.vo.AivideoAdminSettingVo;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.service.IAivideoSettingService;
import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AivideoSettingServiceImpl extends AivideoServiceSupport implements IAivideoSettingService {

    private static final String MEDIA_ACCESS_PRIVATE = "PRIVATE";
    private static final String MEDIA_ACCESS_PUBLIC = "PUBLIC";

    private final AiVideoProjectSettingMapper settingMapper;

    @Override
    public AivideoAdminSettingVo getGlobalSetting() {
        AiVideoProjectSettingPo setting = selectGlobalSetting();
        if (setting == null) {
            setting = defaultGlobalSetting();
        }
        return toVo(setting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGlobalSetting(AivideoAdminSettingDto dto) {
        AivideoAdminSettingDto safeDto = dto != null ? dto : new AivideoAdminSettingDto();
        AiVideoProjectSettingPo setting = selectGlobalSetting();
        if (setting == null) {
            setting = new AiVideoProjectSettingPo();
            setting.setTenantId(resolveTenantIdForWrite());
            copySettingFields(safeDto, setting);
            fillCreateAudit(setting);
            settingMapper.insert(setting);
            return;
        }
        copySettingFields(safeDto, setting);
        fillUpdateAudit(setting);
        settingMapper.updateById(setting);
    }

    private AiVideoProjectSettingPo selectGlobalSetting() {
        LambdaQueryWrapper<AiVideoProjectSettingPo> wrapper = new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .isNull(AiVideoProjectSettingPo::getProjectId)
                .orderByDesc(AiVideoProjectSettingPo::getUpdateTime)
                .orderByDesc(AiVideoProjectSettingPo::getSettingId)
                .last("limit 1");
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiVideoProjectSettingPo::getTenantId, tenantId);
        }
        return settingMapper.selectOne(wrapper);
    }

    private AiVideoProjectSettingPo defaultGlobalSetting() {
        AiVideoProjectSettingPo setting = new AiVideoProjectSettingPo();
        setting.setDefaultRatio("9:16");
        setting.setDefaultResolution("720p");
        setting.setDefaultShotDuration(5);
        setting.setImageCandidateCount(2);
        setting.setVideoCandidateCount(1);
        setting.setPreviewMode(YES);
        setting.setContentAuditEnabled(YES);
        setting.setMediaAccessPolicy(MEDIA_ACCESS_PRIVATE);
        setting.setParamsJson(XuJsonUtil.toJsonString(defaultStrategyParams()));
        setting.setRemark("MVP 0 默认配置，未接真实火山模型");
        return setting;
    }

    private void copySettingFields(AivideoAdminSettingDto source, AiVideoProjectSettingPo target) {
        target.setTextModelId(source.getTextModelId());
        target.setImageModelId(source.getImageModelId());
        target.setVideoModelId(source.getVideoModelId());
        target.setPolishPromptTemplateId(source.getPolishPromptTemplateId());
        target.setScriptPromptTemplateId(source.getScriptPromptTemplateId());
        target.setCharacterPromptTemplateId(source.getCharacterPromptTemplateId());
        target.setScenePromptTemplateId(source.getScenePromptTemplateId());
        target.setCharacterImagePromptTemplateId(source.getCharacterImagePromptTemplateId());
        target.setSceneImagePromptTemplateId(source.getSceneImagePromptTemplateId());
        target.setShotPromptTemplateId(source.getShotPromptTemplateId());
        target.setVideoPromptTemplateId(source.getVideoPromptTemplateId());
        target.setDefaultRatio(defaultString(source.getDefaultRatio(), "9:16"));
        target.setDefaultResolution(defaultString(source.getDefaultResolution(), "720p"));
        target.setDefaultShotDuration(normalizeShotDuration(source.getDefaultShotDuration()));
        target.setImageCandidateCount(source.getImageCandidateCount() == null ? 2 : source.getImageCandidateCount());
        target.setVideoCandidateCount(source.getVideoCandidateCount() == null ? 1 : source.getVideoCandidateCount());
        target.setPreviewMode(defaultString(source.getPreviewMode(), YES));
        target.setContentAuditEnabled(defaultString(source.getContentAuditEnabled(), YES));
        target.setMediaAccessPolicy(normalizeMediaAccessPolicy(source.getMediaAccessPolicy()));
        target.setParamsJson(XuJsonUtil.toJsonString(buildStrategyParams(source, target.getParamsJson())));
        target.setRemark(trimToNull(source.getRemark()));
    }

    private AivideoAdminSettingVo toVo(AiVideoProjectSettingPo setting) {
        AivideoAdminSettingVo vo = new AivideoAdminSettingVo();
        vo.setTextModelId(setting.getTextModelId());
        vo.setImageModelId(setting.getImageModelId());
        vo.setVideoModelId(setting.getVideoModelId());
        vo.setPolishPromptTemplateId(setting.getPolishPromptTemplateId());
        vo.setScriptPromptTemplateId(setting.getScriptPromptTemplateId());
        vo.setCharacterPromptTemplateId(setting.getCharacterPromptTemplateId());
        vo.setScenePromptTemplateId(setting.getScenePromptTemplateId());
        vo.setCharacterImagePromptTemplateId(setting.getCharacterImagePromptTemplateId());
        vo.setSceneImagePromptTemplateId(setting.getSceneImagePromptTemplateId());
        vo.setShotPromptTemplateId(setting.getShotPromptTemplateId());
        vo.setVideoPromptTemplateId(setting.getVideoPromptTemplateId());
        vo.setDefaultRatio(setting.getDefaultRatio());
        vo.setDefaultResolution(setting.getDefaultResolution());
        vo.setDefaultShotDuration(setting.getDefaultShotDuration());
        vo.setImageCandidateCount(setting.getImageCandidateCount());
        vo.setVideoCandidateCount(setting.getVideoCandidateCount());
        vo.setPreviewMode(setting.getPreviewMode());
        vo.setContentAuditEnabled(setting.getContentAuditEnabled());
        vo.setMediaAccessPolicy(normalizeMediaAccessPolicy(setting.getMediaAccessPolicy()));
        Map<String, Object> params = parseParamsJson(setting.getParamsJson());
        vo.setDefaultStyle(paramText(params, PARAM_DEFAULT_STYLE, DEFAULT_STYLE));
        vo.setGenerationStrategy(paramText(params, PARAM_GENERATION_STRATEGY, DEFAULT_GENERATION_STRATEGY));
        vo.setAudioMode(paramText(params, PARAM_AUDIO_MODE, DEFAULT_AUDIO_MODE));
        vo.setSubtitleMode(paramText(params, PARAM_SUBTITLE_MODE, DEFAULT_SUBTITLE_MODE));
        vo.setReferenceStrategy(paramText(params, PARAM_REFERENCE_STRATEGY, DEFAULT_REFERENCE_STRATEGY));
        vo.setActionIntensity(paramText(params, PARAM_ACTION_INTENSITY, DEFAULT_ACTION_INTENSITY));
        vo.setContinuityLevel(paramText(params, PARAM_CONTINUITY_LEVEL, DEFAULT_CONTINUITY_LEVEL));
        vo.setMultiRoleStrategy(paramText(params, PARAM_MULTI_ROLE_STRATEGY, DEFAULT_MULTI_ROLE_STRATEGY));
        vo.setCharacterDesignType(paramText(params, PARAM_CHARACTER_DESIGN_TYPE, DEFAULT_CHARACTER_DESIGN_TYPE));
        vo.setGlobalPrompt(paramText(params, PARAM_GLOBAL_PROMPT, ""));
        vo.setPolishPrompt(paramText(params, PARAM_POLISH_PROMPT, ""));
        vo.setScriptPrompt(paramText(params, PARAM_SCRIPT_PROMPT, ""));
        vo.setAssetPrompt(paramText(params, PARAM_ASSET_PROMPT, ""));
        vo.setCharacterImagePrompt(paramText(params, PARAM_CHARACTER_IMAGE_PROMPT, ""));
        vo.setSceneImagePrompt(paramText(params, PARAM_SCENE_IMAGE_PROMPT, ""));
        vo.setShotVideoPrompt(paramText(params, PARAM_SHOT_VIDEO_PROMPT, ""));
        vo.setRemark(setting.getRemark());
        return vo;
    }

    private Map<String, Object> buildStrategyParams(AivideoAdminSettingDto source, String existingJson) {
        Map<String, Object> params = parseParamsJson(existingJson);
        putStrategyValue(params, PARAM_DEFAULT_STYLE, source.getDefaultStyle(), DEFAULT_STYLE);
        putStrategyValue(params, PARAM_GENERATION_STRATEGY, source.getGenerationStrategy(), DEFAULT_GENERATION_STRATEGY);
        putStrategyValue(params, PARAM_AUDIO_MODE, source.getAudioMode(), DEFAULT_AUDIO_MODE);
        putStrategyValue(params, PARAM_SUBTITLE_MODE, source.getSubtitleMode(), DEFAULT_SUBTITLE_MODE);
        putStrategyValue(params, PARAM_REFERENCE_STRATEGY, source.getReferenceStrategy(), DEFAULT_REFERENCE_STRATEGY);
        putStrategyValue(params, PARAM_ACTION_INTENSITY, source.getActionIntensity(), DEFAULT_ACTION_INTENSITY);
        putStrategyValue(params, PARAM_CONTINUITY_LEVEL, source.getContinuityLevel(), DEFAULT_CONTINUITY_LEVEL);
        putStrategyValue(params, PARAM_MULTI_ROLE_STRATEGY, source.getMultiRoleStrategy(), DEFAULT_MULTI_ROLE_STRATEGY);
        putStrategyValue(params, PARAM_CHARACTER_DESIGN_TYPE, source.getCharacterDesignType(), DEFAULT_CHARACTER_DESIGN_TYPE);
        putStrategyValue(params, PARAM_GLOBAL_PROMPT, source.getGlobalPrompt(), "");
        putStrategyValue(params, PARAM_POLISH_PROMPT, source.getPolishPrompt(), "");
        putStrategyValue(params, PARAM_SCRIPT_PROMPT, source.getScriptPrompt(), "");
        putStrategyValue(params, PARAM_ASSET_PROMPT, source.getAssetPrompt(), "");
        putStrategyValue(params, PARAM_CHARACTER_IMAGE_PROMPT, source.getCharacterImagePrompt(), "");
        putStrategyValue(params, PARAM_SCENE_IMAGE_PROMPT, source.getSceneImagePrompt(), "");
        putStrategyValue(params, PARAM_SHOT_VIDEO_PROMPT, source.getShotVideoPrompt(), "");
        return params;
    }

    private Map<String, Object> defaultStrategyParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        putStrategyValue(params, PARAM_DEFAULT_STYLE, null, DEFAULT_STYLE);
        putStrategyValue(params, PARAM_GENERATION_STRATEGY, null, DEFAULT_GENERATION_STRATEGY);
        putStrategyValue(params, PARAM_AUDIO_MODE, null, DEFAULT_AUDIO_MODE);
        putStrategyValue(params, PARAM_SUBTITLE_MODE, null, DEFAULT_SUBTITLE_MODE);
        putStrategyValue(params, PARAM_REFERENCE_STRATEGY, null, DEFAULT_REFERENCE_STRATEGY);
        putStrategyValue(params, PARAM_ACTION_INTENSITY, null, DEFAULT_ACTION_INTENSITY);
        putStrategyValue(params, PARAM_CONTINUITY_LEVEL, null, DEFAULT_CONTINUITY_LEVEL);
        putStrategyValue(params, PARAM_MULTI_ROLE_STRATEGY, null, DEFAULT_MULTI_ROLE_STRATEGY);
        putStrategyValue(params, PARAM_CHARACTER_DESIGN_TYPE, null, DEFAULT_CHARACTER_DESIGN_TYPE);
        return params;
    }

    private String paramText(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? defaultValue : String.valueOf(value).trim();
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private int normalizeShotDuration(Integer durationSec) {
        if (durationSec == null || durationSec <= 5) {
            return 5;
        }
        if (durationSec <= 6) {
            return 6;
        }
        return 8;
    }

    private String normalizeMediaAccessPolicy(String value) {
        if (!StringUtils.hasText(value)) {
            return MEDIA_ACCESS_PRIVATE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return MEDIA_ACCESS_PUBLIC.equals(normalized) ? MEDIA_ACCESS_PUBLIC : MEDIA_ACCESS_PRIVATE;
    }

    private void fillCreateAudit(AiVideoProjectSettingPo setting) {
        setting.setCreateBy(resolveOperator());
        setting.setCreateTime(now());
        setting.setUpdateBy(resolveOperator());
        setting.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoProjectSettingPo setting) {
        setting.setUpdateBy(resolveOperator());
        setting.setUpdateTime(now());
    }
}
