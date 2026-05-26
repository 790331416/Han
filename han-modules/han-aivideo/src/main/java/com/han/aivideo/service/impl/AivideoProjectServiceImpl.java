package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.po.AiVideoSourceDocumentPo;
import com.han.aivideo.domain.query.AivideoProjectQuery;
import com.han.aivideo.domain.vo.AivideoProjectDetailVo;
import com.han.aivideo.enums.AivideoProjectStage;
import com.han.aivideo.enums.AivideoProjectStatus;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoContentVersionMapper;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoSceneMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import com.han.aivideo.mapper.AiVideoSourceDocumentMapper;
import com.han.aivideo.service.IAivideoProjectService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AivideoProjectServiceImpl extends AivideoServiceSupport implements IAivideoProjectService {

    private final AiVideoProjectMapper projectMapper;
    private final AiVideoProjectSettingMapper settingMapper;
    private final AiVideoSourceDocumentMapper documentMapper;
    private final AiVideoGenerationTaskMapper taskMapper;
    private final AiVideoContentVersionMapper contentVersionMapper;
    private final AiVideoCharacterMapper characterMapper;
    private final AiVideoSceneMapper sceneMapper;
    private final AiVideoShotMapper shotMapper;

    @Override
    public PageResult<AiVideoProjectPo> selectPage(AivideoProjectQuery query) {
        AivideoProjectQuery safeQuery = query != null ? query : new AivideoProjectQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiVideoProjectPo> page = projectMapper.selectPage(
                new Page<>(pageNum, pageSize),
                buildProjectWrapper(safeQuery)
        );
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AivideoProjectDetailVo selectDetail(Long projectId) {
        AiVideoProjectPo project = requireProject(projectId);
        AivideoProjectDetailVo vo = new AivideoProjectDetailVo();
        vo.setProject(project);
        vo.setSetting(selectSetting(projectId));
        vo.setDocuments(selectDocuments(projectId));
        vo.setContentVersions(selectContentVersions(projectId));
        vo.setCharacters(selectCharacters(projectId));
        vo.setScenes(selectScenes(projectId));
        vo.setShots(selectShots(projectId));
        vo.setLatestTask(selectLatestTask(projectId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(AivideoProjectDto dto) {
        validateProject(dto);
        AiVideoProjectPo project = new AiVideoProjectPo();
        copyProjectFields(dto, project);
        project.setTenantId(resolveTenantIdForWrite());
        project.setOwnerUserId(currentUserId());
        project.setCurrentStage(AivideoProjectStage.DRAFT.name());
        project.setProjectStatus(AivideoProjectStatus.DRAFT.name());
        project.setEstimatedCost(defaultCost(project.getEstimatedCost()));
        project.setActualCost(defaultCost(project.getActualCost()));
        fillCreateAudit(project);
        projectMapper.insert(project);

        AiVideoProjectSettingPo setting = buildSettingSnapshot(project.getProjectId(), dto);
        fillCreateAudit(setting);
        settingMapper.insert(setting);

        if (StringUtils.hasText(dto.getRawText()) || dto.getFileId() != null) {
            AivideoDocumentSaveDto documentDto = new AivideoDocumentSaveDto();
            documentDto.setProjectId(project.getProjectId());
            documentDto.setSourceType(dto.getSourceType());
            documentDto.setFileId(dto.getFileId());
            documentDto.setFileName(dto.getFileName());
            documentDto.setRawText(dto.getRawText());
            saveDocument(documentDto);
        }
        return project.getProjectId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(AivideoProjectDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        validateProject(dto);
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        copyProjectFields(dto, project);
        fillUpdateAudit(project);
        projectMapper.updateById(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDocument(AivideoDocumentSaveDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        requireProject(dto.getProjectId());
        if (!StringUtils.hasText(dto.getRawText()) && dto.getFileId() == null) {
            throw new BusinessException("原文内容或文件ID至少填写一个");
        }
        AiVideoSourceDocumentPo document = new AiVideoSourceDocumentPo();
        document.setProjectId(dto.getProjectId());
        document.setTenantId(resolveTenantIdForWrite());
        document.setSourceType(defaultString(dto.getSourceType(), "TEXT"));
        document.setFileId(dto.getFileId());
        document.setFileName(trimToNull(dto.getFileName()));
        document.setRawText(trimToNull(dto.getRawText()));
        document.setCharCount(dto.getRawText() == null ? 0L : (long) dto.getRawText().length());
        document.setParseStatus("PENDING");
        document.setConfirmed(NO);
        document.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(document);
        documentMapper.insert(document);

        AiVideoProjectPo project = requireProject(dto.getProjectId());
        if (AivideoProjectStage.DRAFT.name().equals(project.getCurrentStage())) {
            project.setCurrentStage(AivideoProjectStage.DOCUMENT_SAVED.name());
            fillUpdateAudit(project);
            projectMapper.updateById(project);
        }
        return document.getDocumentId();
    }

    private LambdaQueryWrapper<AiVideoProjectPo> buildProjectWrapper(AivideoProjectQuery query) {
        LambdaQueryWrapper<AiVideoProjectPo> wrapper = new LambdaQueryWrapper<AiVideoProjectPo>()
                .like(StringUtils.hasText(query.getProjectName()), AiVideoProjectPo::getProjectName, query.getProjectName())
                .eq(StringUtils.hasText(query.getProjectStatus()), AiVideoProjectPo::getProjectStatus, query.getProjectStatus())
                .eq(StringUtils.hasText(query.getCurrentStage()), AiVideoProjectPo::getCurrentStage, query.getCurrentStage())
                .eq(AiVideoProjectPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoProjectPo::getUpdateTime)
                .orderByDesc(AiVideoProjectPo::getCreateTime);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiVideoProjectPo::getTenantId, tenantId);
        }
        Long userId = currentUserId();
        if (userId != null && !currentUserIsAdmin()) {
            wrapper.eq(AiVideoProjectPo::getOwnerUserId, userId);
        }
        return wrapper;
    }

    private AiVideoProjectPo requireProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = projectMapper.selectById(projectId);
        if (project == null || !Integer.valueOf(DEL_FLAG_NORMAL).equals(project.getDelFlag())) {
            throw new BusinessException("项目不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(project.getTenantId())) {
            throw new BusinessException("无权访问该项目");
        }
        Long userId = currentUserId();
        if (userId != null && !currentUserIsAdmin() && !userId.equals(project.getOwnerUserId())) {
            throw new BusinessException("无权访问该项目");
        }
        return project;
    }

    private AiVideoProjectSettingPo selectSetting(Long projectId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .eq(AiVideoProjectSettingPo::getProjectId, projectId)
                .last("limit 1"));
    }

    private List<AiVideoSourceDocumentPo> selectDocuments(Long projectId) {
        return documentMapper.selectList(new LambdaQueryWrapper<AiVideoSourceDocumentPo>()
                .eq(AiVideoSourceDocumentPo::getProjectId, projectId)
                .eq(AiVideoSourceDocumentPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoSourceDocumentPo::getUpdateTime)
                .orderByDesc(AiVideoSourceDocumentPo::getCreateTime));
    }

    private List<AiVideoContentVersionPo> selectContentVersions(Long projectId) {
        return contentVersionMapper.selectList(new LambdaQueryWrapper<AiVideoContentVersionPo>()
                .eq(AiVideoContentVersionPo::getProjectId, projectId)
                .eq(AiVideoContentVersionPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoContentVersionPo::getCreateTime));
    }

    private List<AiVideoCharacterPo> selectCharacters(Long projectId) {
        return characterMapper.selectList(new LambdaQueryWrapper<AiVideoCharacterPo>()
                .eq(AiVideoCharacterPo::getProjectId, projectId)
                .eq(AiVideoCharacterPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByAsc(AiVideoCharacterPo::getSortOrder));
    }

    private List<AiVideoScenePo> selectScenes(Long projectId) {
        return sceneMapper.selectList(new LambdaQueryWrapper<AiVideoScenePo>()
                .eq(AiVideoScenePo::getProjectId, projectId)
                .eq(AiVideoScenePo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByAsc(AiVideoScenePo::getSortOrder));
    }

    private List<AiVideoShotPo> selectShots(Long projectId) {
        return shotMapper.selectList(new LambdaQueryWrapper<AiVideoShotPo>()
                .eq(AiVideoShotPo::getProjectId, projectId)
                .eq(AiVideoShotPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByAsc(AiVideoShotPo::getEpisodeNo)
                .orderByAsc(AiVideoShotPo::getShotNo)
                .orderByAsc(AiVideoShotPo::getSortOrder));
    }

    private AiVideoGenerationTaskPo selectLatestTask(Long projectId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, projectId)
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoGenerationTaskPo::getUpdateTime)
                .orderByDesc(AiVideoGenerationTaskPo::getCreateTime)
                .last("limit 1"));
    }

    private void validateProject(AivideoProjectDto dto) {
        if (dto == null) {
            throw new BusinessException("项目信息不能为空");
        }
        if (!StringUtils.hasText(dto.getProjectName())) {
            throw new BusinessException("项目名称不能为空");
        }
    }

    private void copyProjectFields(AivideoProjectDto source, AiVideoProjectPo target) {
        target.setProjectName(trimToNull(source.getProjectName()));
        target.setTopicType(trimToNull(source.getTopicType()));
        target.setTargetPlatform(trimToNull(source.getTargetPlatform()));
        target.setDefaultRatio(defaultString(source.getDefaultRatio(), "9:16"));
        target.setDefaultStyle(trimToNull(source.getDefaultStyle()));
        target.setDefaultShotDuration(source.getDefaultShotDuration() == null ? 5 : source.getDefaultShotDuration());
        target.setCandidateImageCount(source.getCandidateImageCount() == null ? 2 : source.getCandidateImageCount());
        target.setPreviewMode(defaultString(source.getPreviewMode(), YES));
        target.setBudgetLimit(source.getBudgetLimit());
        target.setSummary(trimToNull(source.getSummary()));
        target.setDelFlag(DEL_FLAG_NORMAL);
    }

    private AiVideoProjectSettingPo buildSettingSnapshot(Long projectId, AivideoProjectDto dto) {
        AiVideoProjectSettingPo global = selectGlobalSetting(resolveTenantIdForWrite());
        AiVideoProjectSettingPo setting = new AiVideoProjectSettingPo();
        setting.setProjectId(projectId);
        setting.setTenantId(resolveTenantIdForWrite());
        setting.setTextModelId(global != null ? global.getTextModelId() : null);
        setting.setImageModelId(global != null ? global.getImageModelId() : null);
        setting.setVideoModelId(global != null ? global.getVideoModelId() : null);
        setting.setPolishPromptTemplateId(global != null ? global.getPolishPromptTemplateId() : null);
        setting.setScriptPromptTemplateId(global != null ? global.getScriptPromptTemplateId() : null);
        setting.setCharacterPromptTemplateId(global != null ? global.getCharacterPromptTemplateId() : null);
        setting.setScenePromptTemplateId(global != null ? global.getScenePromptTemplateId() : null);
        setting.setSceneImagePromptTemplateId(global != null ? global.getSceneImagePromptTemplateId() : null);
        setting.setShotPromptTemplateId(global != null ? global.getShotPromptTemplateId() : null);
        setting.setDefaultRatio(defaultString(dto.getDefaultRatio(), defaultString(global != null ? global.getDefaultRatio() : null, "9:16")));
        setting.setDefaultResolution(defaultString(global != null ? global.getDefaultResolution() : null, "720p"));
        setting.setDefaultShotDuration(dto.getDefaultShotDuration() == null
                ? defaultInteger(global != null ? global.getDefaultShotDuration() : null, 5)
                : dto.getDefaultShotDuration());
        setting.setImageCandidateCount(dto.getCandidateImageCount() == null
                ? defaultInteger(global != null ? global.getImageCandidateCount() : null, 2)
                : dto.getCandidateImageCount());
        setting.setVideoCandidateCount(defaultInteger(global != null ? global.getVideoCandidateCount() : null, 1));
        setting.setPreviewMode(defaultString(dto.getPreviewMode(), defaultString(global != null ? global.getPreviewMode() : null, YES)));
        setting.setContentAuditEnabled(defaultString(global != null ? global.getContentAuditEnabled() : null, YES));
        return setting;
    }

    private AiVideoProjectSettingPo selectGlobalSetting(Long tenantId) {
        LambdaQueryWrapper<AiVideoProjectSettingPo> wrapper = new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .isNull(AiVideoProjectSettingPo::getProjectId)
                .orderByDesc(AiVideoProjectSettingPo::getUpdateTime)
                .last("limit 1");
        if (tenantId != null && tenantId > 0) {
            wrapper.and(q -> q.eq(AiVideoProjectSettingPo::getTenantId, tenantId)
                    .or().eq(AiVideoProjectSettingPo::getTenantId, 0L)
                    .or().isNull(AiVideoProjectSettingPo::getTenantId));
        }
        return settingMapper.selectOne(wrapper);
    }

    private java.math.BigDecimal defaultCost(java.math.BigDecimal cost) {
        return cost == null ? java.math.BigDecimal.ZERO : cost;
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void fillCreateAudit(AiVideoProjectPo project) {
        project.setCreateBy(resolveOperator());
        project.setCreateTime(now());
        project.setUpdateBy(resolveOperator());
        project.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoProjectPo project) {
        project.setUpdateBy(resolveOperator());
        project.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoProjectSettingPo setting) {
        setting.setCreateBy(resolveOperator());
        setting.setCreateTime(now());
        setting.setUpdateBy(resolveOperator());
        setting.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoSourceDocumentPo document) {
        document.setCreateBy(resolveOperator());
        document.setCreateTime(now());
        document.setUpdateBy(resolveOperator());
        document.setUpdateTime(now());
    }
}
