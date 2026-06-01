package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.aivideo.domain.dto.AivideoAdminSettingDto;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.vo.AivideoAdminSettingVo;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.service.IAivideoSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

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
        vo.setRemark(setting.getRemark());
        return vo;
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
