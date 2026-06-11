package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.han.aivideo.domain.dto.AivideoAssetConfirmDto;
import com.han.aivideo.domain.dto.AivideoAssetExtractDto;
import com.han.aivideo.domain.dto.AivideoCharacterVoiceUpdateDto;
import com.han.aivideo.domain.dto.AivideoContentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentConfirmDto;
import com.han.aivideo.domain.dto.AivideoShotSceneUpdateDto;
import com.han.aivideo.domain.dto.AivideoTextGenerateDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.aivideo.domain.po.AiVideoReviewRecordPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.po.AiVideoSourceDocumentPo;
import com.han.aivideo.domain.vo.AivideoAssetSummaryVo;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import com.han.aivideo.enums.AivideoProjectStage;
import com.han.aivideo.enums.AivideoTaskStatus;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoContentVersionMapper;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoPropMapper;
import com.han.aivideo.mapper.AiVideoReviewRecordMapper;
import com.han.aivideo.mapper.AiVideoSceneMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import com.han.aivideo.mapper.AiVideoSourceDocumentMapper;
import com.han.aivideo.service.IAivideoTextService;
import com.han.api.ai.AiServiceClient;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI short-drama text workflow implementation.
 */
@Service
@RequiredArgsConstructor
public class AivideoTextServiceImpl extends AivideoServiceSupport implements IAivideoTextService {

    private static final String CONTENT_POLISH = "POLISH";
    private static final String CONTENT_SCRIPT = "SCRIPT";
    private static final String CONTENT_ASSET_EXTRACT = "ASSET_EXTRACT";
    private static final String CONFIRM_PENDING = "PENDING";
    private static final String CONFIRM_APPROVED = "APPROVED";
    private static final String CONFIRM_REJECTED = "REJECTED";
    private static final String TASK_POLISH = "TEXT_POLISH";
    private static final String TASK_SCRIPT = "TEXT_SCRIPT";
    private static final String TASK_ASSET = "ASSET_EXTRACT";
    private static final String TEXT_SYSTEM_PROMPT = "你是专业短剧编剧和影视前期策划助手。请严格按用户要求输出，避免添加无法落地的空泛描述。";
    private static final int ASSET_EXTRACT_MAX_TOKENS = 8192;
    private static final String TARGET_DOCUMENT = "DOCUMENT";
    private static final String TARGET_CONTENT = "CONTENT_VERSION";
    private static final String TARGET_CHARACTER = "CHARACTER";
    private static final String TARGET_SCENE = "SCENE";
    private static final String TARGET_PROP = "PROP";
    private static final String TARGET_SHOT = "SHOT";
    private static final String TARGET_ALL = "ALL";
    private static final String ACTION_CONFIRM = "CONFIRM";
    private static final String ACTION_CANCEL_CONFIRM = "CANCEL_CONFIRM";

    private final AiVideoProjectMapper projectMapper;
    private final AiVideoProjectSettingMapper settingMapper;
    private final AiVideoSourceDocumentMapper documentMapper;
    private final AiVideoGenerationTaskMapper taskMapper;
    private final AiVideoContentVersionMapper contentVersionMapper;
    private final AiVideoCharacterMapper characterMapper;
    private final AiVideoSceneMapper sceneMapper;
    private final AiVideoShotMapper shotMapper;
    private final AiVideoReviewRecordMapper reviewRecordMapper;
    private final AiServiceClient aiServiceClient;
    private final AivideoAiStreamClient aiStreamClient;
    private final TransactionTemplate transactionTemplate;
    private AiVideoPropMapper propMapper;

    @Autowired(required = false)
    void setPropMapper(AiVideoPropMapper propMapper) {
        this.propMapper = propMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDocument(AivideoDocumentConfirmDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoSourceDocumentPo document = requireDocument(project.getProjectId(), dto.getDocumentId());
        if (StringUtils.hasText(dto.getParsedText())) {
            document.setParsedText(dto.getParsedText().trim());
            document.setCharCount((long) dto.getParsedText().trim().length());
        }
        if (StringUtils.hasText(dto.getChapterJson())) {
            document.setChapterJson(dto.getChapterJson().trim());
        }
        document.setConfirmed(YES);
        document.setParseStatus("CONFIRMED");
        fillUpdateAudit(document);
        documentMapper.updateById(document);

        project.setCurrentStage(AivideoProjectStage.DOCUMENT_PARSED.name());
        fillUpdateAudit(project);
        projectMapper.updateById(project);
        insertReview(project, TARGET_DOCUMENT, document.getDocumentId(), ACTION_CONFIRM, NO, YES, dto.getComment(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiVideoContentVersionPo generatePolish(AivideoTextGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoSourceDocumentPo document = requireDocument(project.getProjectId(), dto.getDocumentId());
        String sourceText = firstText(document.getParsedText(), document.getRawText());
        if (!StringUtils.hasText(sourceText)) {
            throw new BusinessException("原文内容不能为空");
        }
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = setting != null ? setting.getPolishPromptTemplateId() : null;
        String prompt = buildPolishPrompt(project, sourceText);
        Map<String, String> variables = baseVariables(project);
        variables.put("rawText", sourceText);
        variables.put("style", safeValue(project.getDefaultStyle()));

        return runTextTask(project, document.getDocumentId(), CONTENT_POLISH, TASK_POLISH, promptTemplateId,
                dto.getCustomPrompt(), prompt, variables, "润色稿", setting);
    }

    @Override
    public SseEmitter generatePolishStream(AivideoTextGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoSourceDocumentPo document = requireDocument(project.getProjectId(), dto.getDocumentId());
        String sourceText = firstText(document.getParsedText(), document.getRawText());
        if (!StringUtils.hasText(sourceText)) {
            throw new BusinessException("原文内容不能为空");
        }
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = setting != null ? setting.getPolishPromptTemplateId() : null;
        String fallbackPrompt = buildPolishPrompt(project, sourceText);
        Map<String, String> variables = baseVariables(project);
        variables.put("rawText", sourceText);
        variables.put("style", safeValue(project.getDefaultStyle()));
        String taskPrompt = renderUserPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        AiVideoGenerationTaskPo task = createTask(project, TASK_POLISH, TARGET_DOCUMENT, document.getDocumentId(),
                setting != null ? setting.getTextModelId() : null, promptTemplateId, taskPrompt, dto.getCustomPrompt(), variables);
        String operator = resolveOperator();

        SseEmitter emitter = new SseEmitter(300_000L);
        CompletableFuture.runAsync(() -> runPolishStream(project, document, setting, promptTemplateId,
                dto.getCustomPrompt(), fallbackPrompt, variables, task, operator, emitter));
        return emitter;
    }

    @Override
    public AivideoPromptPreviewVo previewPolishPrompt(AivideoTextGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoSourceDocumentPo document = requireDocument(project.getProjectId(), dto.getDocumentId());
        String sourceText = firstText(document.getParsedText(), document.getRawText());
        if (!StringUtils.hasText(sourceText)) {
            throw new BusinessException("原文内容不能为空");
        }
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = setting != null ? setting.getPolishPromptTemplateId() : null;
        String fallbackPrompt = buildPolishPrompt(project, sourceText);
        Map<String, String> variables = baseVariables(project);
        variables.put("rawText", sourceText);
        variables.put("style", safeValue(project.getDefaultStyle()));
        String userPrompt = renderUserPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);

        AivideoPromptPreviewVo vo = new AivideoPromptPreviewVo();
        vo.setPromptTemplateId(promptTemplateId);
        vo.setSystemPrompt(TEXT_SYSTEM_PROMPT);
        vo.setUserPrompt(userPrompt);
        vo.setCustomPrompt(dto.getCustomPrompt());
        vo.setEffectivePrompt("系统提示词：\n" + TEXT_SYSTEM_PROMPT + "\n\n用户提示词：\n" + userPrompt);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPolish(AivideoContentConfirmDto dto) {
        confirmContent(dto, CONTENT_POLISH, AivideoProjectStage.POLISH_CONFIRMED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelConfirmPolish(AivideoContentConfirmDto dto) {
        cancelConfirmContent(dto, CONTENT_POLISH, AivideoProjectStage.DOCUMENT_PARSED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiVideoContentVersionPo generateScript(AivideoTextGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo polish = requireSelectedContent(project.getProjectId(), CONTENT_POLISH, "请先确认润色稿");
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = setting != null ? setting.getScriptPromptTemplateId() : null;
        String prompt = buildScriptPrompt(project, polish.getContentText());
        Map<String, String> variables = baseVariables(project);
        variables.put("polishedText", polish.getContentText());
        variables.put("rawText", polish.getContentText());

        return runTextTask(project, null, CONTENT_SCRIPT, TASK_SCRIPT, promptTemplateId,
                dto.getCustomPrompt(), prompt, variables, "短剧剧本", setting);
    }

    @Override
    public SseEmitter generateScriptStream(AivideoTextGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo polish = requireSelectedContent(project.getProjectId(), CONTENT_POLISH, "请先确认润色稿");
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = setting != null ? setting.getScriptPromptTemplateId() : null;
        String fallbackPrompt = buildScriptPrompt(project, polish.getContentText());
        Map<String, String> variables = baseVariables(project);
        variables.put("polishedText", polish.getContentText());
        variables.put("rawText", polish.getContentText());
        String taskPrompt = renderUserPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        AiVideoGenerationTaskPo task = createTask(project, TASK_SCRIPT, TARGET_CONTENT, polish.getVersionId(),
                setting != null ? setting.getTextModelId() : null, promptTemplateId, taskPrompt, dto.getCustomPrompt(), variables);
        String operator = resolveOperator();

        SseEmitter emitter = new SseEmitter(300_000L);
        CompletableFuture.runAsync(() -> runScriptStream(project, setting, promptTemplateId,
                dto.getCustomPrompt(), fallbackPrompt, variables, task, operator, emitter));
        return emitter;
    }

    @Override
    public AivideoPromptPreviewVo previewScriptPrompt(AivideoTextGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo polish = requireSelectedContent(project.getProjectId(), CONTENT_POLISH, "请先确认润色稿");
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = setting != null ? setting.getScriptPromptTemplateId() : null;
        String fallbackPrompt = buildScriptPrompt(project, polish.getContentText());
        Map<String, String> variables = baseVariables(project);
        variables.put("polishedText", polish.getContentText());
        variables.put("rawText", polish.getContentText());
        String userPrompt = renderUserPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);

        AivideoPromptPreviewVo vo = new AivideoPromptPreviewVo();
        vo.setPromptTemplateId(promptTemplateId);
        vo.setSystemPrompt(TEXT_SYSTEM_PROMPT);
        vo.setUserPrompt(userPrompt);
        vo.setCustomPrompt(dto.getCustomPrompt());
        vo.setEffectivePrompt("系统提示词：\n" + TEXT_SYSTEM_PROMPT + "\n\n用户提示词：\n" + userPrompt);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmScript(AivideoContentConfirmDto dto) {
        confirmContent(dto, CONTENT_SCRIPT, AivideoProjectStage.SCRIPT_CONFIRMED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelConfirmScript(AivideoContentConfirmDto dto) {
        cancelConfirmContent(dto, CONTENT_SCRIPT, AivideoProjectStage.POLISH_CONFIRMED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AivideoAssetSummaryVo extractAssets(AivideoAssetExtractDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo script = requireSelectedContent(project.getProjectId(), CONTENT_SCRIPT, "请先确认短剧剧本");
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = firstTemplateId(setting);
        String fallbackPrompt = buildAssetPrompt(project, setting, script.getContentText());
        Map<String, String> variables = baseVariables(project);
        variables.put("scriptText", script.getContentText());
        variables.put("rawText", script.getContentText());
        variables.put("defaultShotDuration", String.valueOf(defaultShotDuration(setting)));
        String prompt = renderAssetUserPrompt(project, setting, dto.getCustomPrompt(), fallbackPrompt, variables);

        AiVideoGenerationTaskPo task = createTask(project, TASK_ASSET, TARGET_CONTENT, script.getVersionId(),
                setting != null ? setting.getTextModelId() : null,
                promptTemplateId, prompt, dto.getCustomPrompt(), variables);
        try {
            AiTextGenerateResponse response = invokeAssetTextGeneration(project, setting,
                    null, null, prompt, variables);
            AssetPayload payload = parseAssetPayload(response.getContent());
            softDeletePendingAssets(project.getProjectId());
            insertAssets(project, payload, setting);

            AiVideoContentVersionPo assetVersion = buildContentVersion(project, null, CONTENT_ASSET_EXTRACT,
                    "结构化资产", response.getContent(), extractJsonBlock(response.getContent()),
                    promptTemplateId, dto.getCustomPrompt(), response.getModelId(), task.getTaskId());
            contentVersionMapper.insert(assetVersion);
            markTaskSuccess(task, response.getModelId(), response.getTokenCount());
            return selectAssetSummary(project.getProjectId());
        } catch (RuntimeException exception) {
            markTaskFailed(task, exception.getMessage());
            throw exception;
        }
    }

    @Override
    public SseEmitter extractAssetsStream(AivideoAssetExtractDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo script = requireSelectedContent(project.getProjectId(), CONTENT_SCRIPT, "请先确认短剧剧本");
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = firstTemplateId(setting);
        String fallbackPrompt = buildAssetPrompt(project, setting, script.getContentText());
        Map<String, String> variables = baseVariables(project);
        variables.put("scriptText", script.getContentText());
        variables.put("rawText", script.getContentText());
        variables.put("defaultShotDuration", String.valueOf(defaultShotDuration(setting)));
        String taskPrompt = renderAssetUserPrompt(project, setting, dto.getCustomPrompt(), fallbackPrompt, variables);
        AiVideoGenerationTaskPo task = createTask(project, TASK_ASSET, TARGET_CONTENT, script.getVersionId(),
                setting != null ? setting.getTextModelId() : null, promptTemplateId, taskPrompt, dto.getCustomPrompt(), variables);
        String operator = resolveOperator();

        SseEmitter emitter = new SseEmitter(300_000L);
        sendSseSafely(emitter, "meta", Map.of(
                "event", "started",
                "taskId", task.getTaskId(),
                "taskStatus", task.getTaskStatus(),
                "progress", task.getProgress()
        ));
        CompletableFuture.runAsync(() -> runAssetStream(project, setting, promptTemplateId,
                dto.getCustomPrompt(), taskPrompt, variables, task, operator, emitter));
        return emitter;
    }

    @Override
    public AivideoPromptPreviewVo previewAssetPrompt(AivideoAssetExtractDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo script = requireSelectedContent(project.getProjectId(), CONTENT_SCRIPT, "请先确认短剧剧本");
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long promptTemplateId = firstTemplateId(setting);
        String fallbackPrompt = buildAssetPrompt(project, setting, script.getContentText());
        Map<String, String> variables = baseVariables(project);
        variables.put("scriptText", script.getContentText());
        variables.put("rawText", script.getContentText());
        variables.put("defaultShotDuration", String.valueOf(defaultShotDuration(setting)));
        String userPrompt = renderAssetUserPrompt(project, setting, dto.getCustomPrompt(), fallbackPrompt, variables);

        AivideoPromptPreviewVo vo = new AivideoPromptPreviewVo();
        vo.setPromptTemplateId(promptTemplateId);
        vo.setSystemPrompt(TEXT_SYSTEM_PROMPT);
        vo.setUserPrompt(userPrompt);
        vo.setCustomPrompt(dto.getCustomPrompt());
        vo.setEffectivePrompt("系统提示词：\n" + TEXT_SYSTEM_PROMPT + "\n\n用户提示词：\n" + userPrompt);
        return vo;
    }

    @Override
    public AivideoAssetSummaryVo selectAssetSummary(Long projectId) {
        requireProject(projectId);
        AivideoAssetSummaryVo vo = new AivideoAssetSummaryVo();
        vo.setCharacters(selectCharacters(projectId));
        vo.setScenes(selectScenes(projectId));
        vo.setProps(selectProps(projectId));
        vo.setShots(selectShots(projectId));
        return vo;
    }

    @Override
    public AiVideoGenerationTaskPo selectStudioTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException("任务ID不能为空");
        }
        AiVideoGenerationTaskPo task = taskMapper.selectById(taskId);
        if (task == null || !Integer.valueOf(DEL_FLAG_NORMAL).equals(task.getDelFlag())) {
            throw new BusinessException("任务不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(task.getTenantId())) {
            throw new BusinessException("无权访问该任务");
        }
        return task;
    }

    @Override
    public AiVideoGenerationTaskPo selectLatestAssetTask(Long projectId) {
        requireProject(projectId);
        LambdaQueryWrapper<AiVideoGenerationTaskPo> wrapper = new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, projectId)
                .eq(AiVideoGenerationTaskPo::getTaskType, TASK_ASSET)
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoGenerationTaskPo::getUpdateTime)
                .orderByDesc(AiVideoGenerationTaskPo::getTaskId)
                .last("limit 1");
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiVideoGenerationTaskPo::getTenantId, tenantId);
        }
        return taskMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmAsset(AivideoAssetConfirmDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (!StringUtils.hasText(dto.getTargetType())) {
            throw new BusinessException("确认目标类型不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        String targetType = dto.getTargetType().trim().toUpperCase();
        if (TARGET_ALL.equals(targetType)) {
            if (!hasAsset(project.getProjectId())) {
                throw new BusinessException("暂无可确认资产，请先提取资产并确保结构化入库");
            }
            approveAllAssets(project.getProjectId());
            insertReview(project, TARGET_ALL, null, ACTION_CONFIRM, CONFIRM_PENDING, CONFIRM_APPROVED, dto.getComment(), null);
            markProjectStage(project, AivideoProjectStage.ASSET_CONFIRMED);
            return;
        }
        if (dto.getTargetId() == null) {
            throw new BusinessException("确认目标ID不能为空");
        }
        approveSingleAsset(project, targetType, dto.getTargetId(), dto.getComment());
        if (hasAsset(project.getProjectId()) && !hasPendingAsset(project.getProjectId())) {
            markProjectStage(project, AivideoProjectStage.ASSET_CONFIRMED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelConfirmAsset(AivideoAssetConfirmDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (!StringUtils.hasText(dto.getTargetType())) {
            throw new BusinessException("取消确认目标类型不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        String targetType = dto.getTargetType().trim().toUpperCase();
        if (TARGET_ALL.equals(targetType)) {
            cancelAllAssets(project.getProjectId());
            insertReview(project, TARGET_ALL, null, ACTION_CANCEL_CONFIRM, CONFIRM_APPROVED, CONFIRM_PENDING, dto.getComment(), null);
            markProjectStage(project, AivideoProjectStage.SCRIPT_CONFIRMED);
            return;
        }
        if (dto.getTargetId() == null) {
            throw new BusinessException("取消确认目标ID不能为空");
        }
        cancelSingleAsset(project, targetType, dto.getTargetId(), dto.getComment());
        if (hasAsset(project.getProjectId()) && hasPendingAsset(project.getProjectId())) {
            markProjectStage(project, AivideoProjectStage.SCRIPT_CONFIRMED);
        }
    }

    private AiVideoContentVersionPo runTextTask(AiVideoProjectPo project, Long documentId, String contentType,
                                                String taskType, Long promptTemplateId, String customPrompt,
                                                String prompt, Map<String, String> variables, String titlePrefix,
                                                AiVideoProjectSettingPo setting) {
        AiVideoGenerationTaskPo task = createTask(project, taskType,
                documentId != null ? TARGET_DOCUMENT : TARGET_CONTENT,
                documentId, setting != null ? setting.getTextModelId() : null,
                promptTemplateId, prompt, customPrompt, variables);
        try {
            AiTextGenerateResponse response = invokeTextGeneration(project, setting, promptTemplateId,
                    customPrompt, prompt, variables);
            AiVideoContentVersionPo version = buildContentVersion(project, documentId, contentType,
                    titlePrefix + " v" + nextVersionNo(project.getProjectId(), contentType),
                    response.getContent(), null, promptTemplateId, customPrompt, response.getModelId(), task.getTaskId());
            contentVersionMapper.insert(version);
            markTaskSuccess(task, response.getModelId(), response.getTokenCount());
            return version;
        } catch (RuntimeException exception) {
            markTaskFailed(task, exception.getMessage());
            throw exception;
        }
    }

    private AiTextGenerateResponse invokeTextGeneration(AiVideoProjectPo project, AiVideoProjectSettingPo setting,
                                                       Long promptTemplateId, String customPrompt,
                                                       String userPrompt, Map<String, String> variables) {
        AiTextGenerateRequest request = buildTextGenerateRequest(project, setting, promptTemplateId, customPrompt,
                userPrompt, variables);
        R<AiTextGenerateResponse> result = aiServiceClient.generateText(request);
        if (result == null || result.isFail()) {
            throw new BusinessException(result == null ? "AI 文本生成服务无响应" : result.getMsg());
        }
        if (result.getData() == null || !StringUtils.hasText(result.getData().getContent())) {
            throw new BusinessException("AI 文本生成结果为空");
        }
        return result.getData();
    }

    private AiTextGenerateResponse invokeAssetTextGeneration(AiVideoProjectPo project, AiVideoProjectSettingPo setting,
                                                             Long promptTemplateId, String customPrompt,
                                                             String userPrompt, Map<String, String> variables) {
        AiTextGenerateRequest request = buildAssetTextGenerateRequest(project, setting, promptTemplateId, customPrompt,
                userPrompt, variables);
        R<AiTextGenerateResponse> result = aiServiceClient.generateText(request);
        if (result == null || result.isFail()) {
            throw new BusinessException(result == null ? "AI 文本生成服务无响应" : result.getMsg());
        }
        if (result.getData() == null || !StringUtils.hasText(result.getData().getContent())) {
            throw new BusinessException("AI 文本生成结果为空");
        }
        return result.getData();
    }

    private AiTextGenerateRequest buildAssetTextGenerateRequest(AiVideoProjectPo project, AiVideoProjectSettingPo setting,
                                                                Long promptTemplateId, String customPrompt,
                                                                String userPrompt, Map<String, String> variables) {
        AiTextGenerateRequest request = buildTextGenerateRequest(project, setting, promptTemplateId, customPrompt,
                userPrompt, variables);
        request.setMaxTokens(ASSET_EXTRACT_MAX_TOKENS);
        return request;
    }

    private void runPolishStream(AiVideoProjectPo project, AiVideoSourceDocumentPo document,
                                 AiVideoProjectSettingPo setting, Long promptTemplateId, String customPrompt,
                                 String userPrompt, Map<String, String> variables, AiVideoGenerationTaskPo task,
                                 String operator, SseEmitter emitter) {
        try {
            AiTextGenerateRequest request = buildTextGenerateRequest(project, setting, promptTemplateId,
                    customPrompt, userPrompt, variables);
            AivideoAiStreamClient.StreamResult result = aiStreamClient.streamText(request,
                    chunk -> sendSseSafely(emitter, "delta", chunk));
            if (!StringUtils.hasText(result.content())) {
                throw new BusinessException("AI 文本生成结果为空");
            }
            Map<String, Object> resultMeta = safeResultMeta(result);
            AiVideoContentVersionPo version = buildContentVersion(project, document.getDocumentId(), CONTENT_POLISH,
                    "润色稿 v" + nextVersionNo(project.getProjectId(), CONTENT_POLISH),
                    result.content(), null, promptTemplateId, customPrompt, resolveLong(resultMeta.get("modelId")), task.getTaskId());
            version.setCreateBy(operator);
            version.setUpdateBy(operator);
            contentVersionMapper.insert(version);
            markTaskSuccess(task, version.getModelId(), null);
            Map<String, Object> meta = new LinkedHashMap<>(resultMeta);
            meta.put("versionId", version.getVersionId());
            meta.put("taskId", task.getTaskId());
            sendSseSafely(emitter, "meta", meta);
            completeWithDoneSafely(emitter);
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithErrorSafely(emitter, exception.getMessage());
        }
    }

    private void runScriptStream(AiVideoProjectPo project, AiVideoProjectSettingPo setting,
                                 Long promptTemplateId, String customPrompt, String userPrompt,
                                 Map<String, String> variables, AiVideoGenerationTaskPo task,
                                 String operator, SseEmitter emitter) {
        try {
            AiTextGenerateRequest request = buildTextGenerateRequest(project, setting, promptTemplateId,
                    customPrompt, userPrompt, variables);
            AivideoAiStreamClient.StreamResult result = aiStreamClient.streamText(request,
                    chunk -> sendSseSafely(emitter, "delta", chunk));
            if (!StringUtils.hasText(result.content())) {
                throw new BusinessException("AI 文本生成结果为空");
            }
            Map<String, Object> resultMeta = safeResultMeta(result);
            AiVideoContentVersionPo version = buildContentVersion(project, null, CONTENT_SCRIPT,
                    "短剧剧本 v" + nextVersionNo(project.getProjectId(), CONTENT_SCRIPT),
                    result.content(), null, promptTemplateId, customPrompt, resolveLong(resultMeta.get("modelId")), task.getTaskId());
            version.setCreateBy(operator);
            version.setUpdateBy(operator);
            contentVersionMapper.insert(version);
            markTaskSuccess(task, version.getModelId(), null);
            Map<String, Object> meta = new LinkedHashMap<>(resultMeta);
            meta.put("versionId", version.getVersionId());
            meta.put("taskId", task.getTaskId());
            sendSseSafely(emitter, "meta", meta);
            completeWithDoneSafely(emitter);
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithErrorSafely(emitter, exception.getMessage());
        }
    }

    private void runAssetStream(AiVideoProjectPo project, AiVideoProjectSettingPo setting,
                                Long promptTemplateId, String customPrompt, String userPrompt,
                                Map<String, String> variables, AiVideoGenerationTaskPo task,
                                String operator, SseEmitter emitter) {
        String generatedContent = null;
        Long generatedModelId = null;
        try {
            AiTextGenerateRequest request = buildAssetTextGenerateRequest(project, setting, null,
                    null, userPrompt, variables);
            AivideoAiStreamClient.StreamResult result = aiStreamClient.streamText(request,
                    chunk -> sendSseSafely(emitter, "delta", chunk));
            Map<String, Object> resultMeta = safeResultMeta(result);
            generatedContent = result.content();
            generatedModelId = resolveLong(resultMeta.get("modelId"));
            if (!StringUtils.hasText(generatedContent)) {
                throw new BusinessException("AI 文本生成结果为空");
            }
            AssetPayload payload = parseAssetPayload(generatedContent);
            String assetContent = generatedContent;
            Long assetModelId = generatedModelId;
            AiVideoContentVersionPo assetVersion = transactionTemplate.execute(status -> {
                softDeletePendingAssets(project.getProjectId());
                insertAssets(project, payload, setting);

                AiVideoContentVersionPo version = buildContentVersion(project, null, CONTENT_ASSET_EXTRACT,
                        "结构化资产", assetContent, extractJsonBlock(assetContent),
                        promptTemplateId, customPrompt, assetModelId, task.getTaskId());
                version.setCreateBy(operator);
                version.setUpdateBy(operator);
                contentVersionMapper.insert(version);
                markTaskSuccess(task, version.getModelId(), null);
                return version;
            });
            if (assetVersion == null) {
                throw new BusinessException("资产提取结果保存失败");
            }
            Map<String, Object> meta = new LinkedHashMap<>(resultMeta);
            meta.put("versionId", assetVersion.getVersionId());
            meta.put("taskId", task.getTaskId());
            meta.put("assetCounts", buildAssetCounts(project.getProjectId()));
            sendSseSafely(emitter, "meta", meta);
            completeWithDoneSafely(emitter);
        } catch (Exception exception) {
            saveFailedAssetExtractVersion(project, promptTemplateId, customPrompt, task, operator,
                    generatedContent, generatedModelId, exception.getMessage());
            markTaskFailed(task, exception.getMessage());
            completeWithErrorSafely(emitter, exception.getMessage());
        }
    }

    private void saveFailedAssetExtractVersion(AiVideoProjectPo project, Long promptTemplateId, String customPrompt,
                                               AiVideoGenerationTaskPo task, String operator, String content,
                                               Long modelId, String errorMessage) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        try {
            AiVideoContentVersionPo version = buildContentVersion(project, null, CONTENT_ASSET_EXTRACT,
                    "结构化资产（未入库）", content, extractJsonBlock(content),
                    promptTemplateId, customPrompt, modelId, task.getTaskId());
            version.setCreateBy(operator);
            version.setUpdateBy(operator);
            String reason = StringUtils.hasText(errorMessage) ? "\n\n【结构化失败原因】\n" + limit(errorMessage, 500) : "";
            version.setContentText(content + reason);
            contentVersionMapper.insert(version);
        } catch (RuntimeException ignored) {
            // Do not hide the real task failure if best-effort raw output persistence also fails.
        }
    }

    private String renderUserPrompt(AiVideoProjectPo project, Long promptTemplateId, String customPrompt,
                                    String userPrompt, Map<String, String> variables) {
        AiTextGenerateRequest request = buildTextGenerateRequest(project, null, promptTemplateId, customPrompt, userPrompt, variables);
        R<String> result = aiServiceClient.renderTextPrompt(request);
        if (result == null || result.isFail()) {
            throw new BusinessException(result == null ? "AI Prompt 渲染服务无响应" : result.getMsg());
        }
        if (!StringUtils.hasText(result.getData())) {
            throw new BusinessException("AI Prompt 渲染结果为空");
        }
        return result.getData();
    }

    private String renderAssetUserPrompt(AiVideoProjectPo project, AiVideoProjectSettingPo setting, String customPrompt,
                                         String fallbackPrompt, Map<String, String> variables) {
        List<String> promptParts = new ArrayList<>();
        appendRenderedTemplate(promptParts, project, setting != null ? setting.getCharacterPromptTemplateId() : null,
                "角色构建参考", variables);
        appendRenderedTemplate(promptParts, project, setting != null ? setting.getScenePromptTemplateId() : null,
                "场景设计参考", variables);
        appendRenderedTemplate(promptParts, project, setting != null ? setting.getShotPromptTemplateId() : null,
                "分镜拆解参考", variables);
        if (promptParts.isEmpty()) {
            return renderUserPrompt(project, null, customPrompt, fallbackPrompt, variables);
        }
        String mergedPrompt = "请把以下三组提示词作为资产提取参考规则，但最终必须只输出统一 JSON 对象，不要输出解释、Markdown 围栏或额外说明。"
                + "JSON key 必须保持英文，所有字段值必须使用中文；如果参考提示词要求先回复确认，请忽略该确认步骤，直接执行提取。\n\n"
                + String.join("\n\n", promptParts)
                + "\n\n【统一输出约束与兜底结构】\n" + fallbackPrompt;
        return renderUserPrompt(project, null, customPrompt, mergedPrompt, variables);
    }

    private void appendRenderedTemplate(List<String> promptParts, AiVideoProjectPo project, Long promptTemplateId,
                                        String title, Map<String, String> variables) {
        if (promptTemplateId == null) {
            return;
        }
        String rendered = renderUserPrompt(project, promptTemplateId, null, "", variables);
        if (StringUtils.hasText(rendered)) {
            promptParts.add("【" + title + "】\n" + rendered.trim());
        }
    }

    private AiTextGenerateRequest buildTextGenerateRequest(AiVideoProjectPo project, AiVideoProjectSettingPo setting,
                                                           Long promptTemplateId, String customPrompt,
                                                           String userPrompt, Map<String, String> variables) {
        AiTextGenerateRequest request = new AiTextGenerateRequest();
        request.setTenantId(project.getTenantId());
        request.setModelId(setting != null ? setting.getTextModelId() : null);
        request.setPromptTemplateId(promptTemplateId);
        request.setUserPrompt(userPrompt);
        request.setCustomPrompt(customPrompt);
        request.setVariables(variables);
        request.setSystemPrompt(TEXT_SYSTEM_PROMPT);
        return request;
    }

    private static Map<String, Object> safeResultMeta(AivideoAiStreamClient.StreamResult result) {
        if (result == null || result.meta() == null) {
            return Collections.emptyMap();
        }
        return result.meta();
    }

    static boolean sendSseSafely(SseEmitter emitter, String type, Object content) {
        if (emitter == null) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().data(XuJsonUtil.toJsonString(Map.of(
                    "type", type,
                    "content", content == null ? "" : content
            ))));
            return true;
        } catch (IOException exception) {
            return false;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private void completeWithDoneSafely(SseEmitter emitter) {
        try {
            if (emitter != null) {
                emitter.send(SseEmitter.event().data("[DONE]"));
            }
        } catch (IOException | IllegalStateException ignored) {
            // SSE is only an observer channel; persisted task state remains authoritative.
        } finally {
            completeSafely(emitter);
        }
    }

    private void completeWithErrorSafely(SseEmitter emitter, String message) {
        try {
            sendSseSafely(emitter, "error", StringUtils.hasText(message) ? message : "AI 文本生成失败");
        } finally {
            completeSafely(emitter);
        }
    }

    private void completeSafely(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // Spring may already have completed the emitter after client disconnect or timeout.
        }
    }

    private Long resolveLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void confirmContent(AivideoContentConfirmDto dto, String contentType, AivideoProjectStage targetStage) {
        if (dto == null || dto.getProjectId() == null || dto.getVersionId() == null) {
            throw new BusinessException("项目ID和版本ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo version = requireContentVersion(project.getProjectId(), dto.getVersionId(), contentType);
        contentVersionMapper.update(null, new LambdaUpdateWrapper<AiVideoContentVersionPo>()
                .set(AiVideoContentVersionPo::getSelected, NO)
                .eq(AiVideoContentVersionPo::getProjectId, project.getProjectId())
                .eq(AiVideoContentVersionPo::getContentType, contentType)
                .eq(AiVideoContentVersionPo::getDelFlag, DEL_FLAG_NORMAL));
        String beforeStatus = version.getConfirmStatus();
        version.setSelected(YES);
        version.setConfirmStatus(CONFIRM_APPROVED);
        fillUpdateAudit(version);
        contentVersionMapper.updateById(version);
        insertReview(project, TARGET_CONTENT, version.getVersionId(), ACTION_CONFIRM,
                beforeStatus, CONFIRM_APPROVED, dto.getComment(), version.getCustomPrompt());
        markProjectStage(project, targetStage);
    }

    private void cancelConfirmContent(AivideoContentConfirmDto dto, String contentType, AivideoProjectStage targetStage) {
        if (dto == null || dto.getProjectId() == null || dto.getVersionId() == null) {
            throw new BusinessException("项目ID和版本ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoContentVersionPo version = requireContentVersion(project.getProjectId(), dto.getVersionId(), contentType);
        String beforeStatus = version.getConfirmStatus();
        version.setSelected(NO);
        version.setConfirmStatus(CONFIRM_PENDING);
        fillUpdateAudit(version);
        contentVersionMapper.updateById(version);
        insertReview(project, TARGET_CONTENT, version.getVersionId(), ACTION_CANCEL_CONFIRM,
                beforeStatus, CONFIRM_PENDING, dto.getComment(), version.getCustomPrompt());
        markProjectStage(project, targetStage);
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

    private AiVideoSourceDocumentPo requireDocument(Long projectId, Long documentId) {
        AiVideoSourceDocumentPo document;
        if (documentId != null) {
            document = documentMapper.selectById(documentId);
        } else {
            document = documentMapper.selectOne(new LambdaQueryWrapper<AiVideoSourceDocumentPo>()
                    .eq(AiVideoSourceDocumentPo::getProjectId, projectId)
                    .eq(AiVideoSourceDocumentPo::getDelFlag, DEL_FLAG_NORMAL)
                    .orderByDesc(AiVideoSourceDocumentPo::getUpdateTime)
                    .orderByDesc(AiVideoSourceDocumentPo::getCreateTime)
                    .last("limit 1"));
        }
        if (document == null || !Objects.equals(projectId, document.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(document.getDelFlag())) {
            throw new BusinessException("原文不存在");
        }
        return document;
    }

    private AiVideoProjectSettingPo selectSetting(Long projectId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .eq(AiVideoProjectSettingPo::getProjectId, projectId)
                .last("limit 1"));
    }

    private AiVideoContentVersionPo requireSelectedContent(Long projectId, String contentType, String message) {
        AiVideoContentVersionPo version = contentVersionMapper.selectOne(new LambdaQueryWrapper<AiVideoContentVersionPo>()
                .eq(AiVideoContentVersionPo::getProjectId, projectId)
                .eq(AiVideoContentVersionPo::getContentType, contentType)
                .eq(AiVideoContentVersionPo::getSelected, YES)
                .eq(AiVideoContentVersionPo::getConfirmStatus, CONFIRM_APPROVED)
                .eq(AiVideoContentVersionPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoContentVersionPo::getVersionNo)
                .last("limit 1"));
        if (version == null) {
            throw new BusinessException(message);
        }
        return version;
    }

    private AiVideoContentVersionPo requireContentVersion(Long projectId, Long versionId, String contentType) {
        AiVideoContentVersionPo version = contentVersionMapper.selectById(versionId);
        if (version == null || !Objects.equals(projectId, version.getProjectId())
                || !contentType.equals(version.getContentType())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(version.getDelFlag())) {
            throw new BusinessException("内容版本不存在");
        }
        return version;
    }

    private AiVideoContentVersionPo buildContentVersion(AiVideoProjectPo project, Long documentId, String contentType,
                                                       String title, String contentText, String contentJson,
                                                       Long promptTemplateId, String customPrompt,
                                                       Long modelId, Long taskId) {
        AiVideoContentVersionPo version = new AiVideoContentVersionPo();
        version.setProjectId(project.getProjectId());
        version.setTenantId(project.getTenantId());
        version.setDocumentId(documentId);
        version.setContentType(contentType);
        version.setVersionNo(nextVersionNo(project.getProjectId(), contentType));
        version.setTitle(title);
        version.setContentText(contentText);
        version.setContentJson(contentJson);
        version.setPromptTemplateId(promptTemplateId);
        version.setCustomPrompt(customPrompt);
        version.setModelId(modelId);
        version.setTaskId(taskId);
        version.setSelected(NO);
        version.setConfirmStatus(CONFIRM_PENDING);
        version.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(version);
        return version;
    }

    private int nextVersionNo(Long projectId, String contentType) {
        AiVideoContentVersionPo latest = contentVersionMapper.selectOne(new LambdaQueryWrapper<AiVideoContentVersionPo>()
                .eq(AiVideoContentVersionPo::getProjectId, projectId)
                .eq(AiVideoContentVersionPo::getContentType, contentType)
                .eq(AiVideoContentVersionPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoContentVersionPo::getVersionNo)
                .last("limit 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private AiVideoGenerationTaskPo createTask(AiVideoProjectPo project, String taskType, String bizType, Long bizId,
                                               Long modelId, Long promptTemplateId, String prompt,
                                               String customPrompt, Map<String, String> variables) {
        AiVideoGenerationTaskPo task = new AiVideoGenerationTaskPo();
        task.setProjectId(project.getProjectId());
        task.setTenantId(project.getTenantId());
        task.setTaskType(taskType);
        task.setBizType(bizType);
        task.setBizId(bizId);
        task.setModelId(modelId);
        task.setPromptTemplateId(promptTemplateId);
        task.setPromptText(prompt);
        task.setCustomPrompt(customPrompt);
        task.setParamsJson(XuJsonUtil.toJsonString(variables));
        task.setTaskStatus(AivideoTaskStatus.RUNNING.name());
        task.setProgress(5);
        task.setStartedTime(now());
        task.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(task);
        taskMapper.insert(task);
        return task;
    }

    private void markTaskSuccess(AiVideoGenerationTaskPo task, Long modelId, Integer tokenCount) {
        task.setModelId(modelId != null ? modelId : task.getModelId());
        task.setTokenCount(tokenCount);
        task.setTaskStatus(AivideoTaskStatus.SUCCESS.name());
        task.setProgress(100);
        task.setFinishedTime(now());
        fillUpdateAudit(task);
        taskMapper.updateById(task);
    }

    private void markTaskFailed(AiVideoGenerationTaskPo task, String message) {
        task.setTaskStatus(AivideoTaskStatus.FAILED.name());
        task.setProgress(100);
        task.setErrorMessage(limit(message, 1800));
        task.setFinishedTime(now());
        fillUpdateAudit(task);
        taskMapper.updateById(task);
    }

    private void insertAssets(AiVideoProjectPo project, AssetPayload payload, AiVideoProjectSettingPo setting) {
        validateShotSpatialContinuity(toShotContinuitySnapshots(payload == null ? null : payload.shots));

        Map<String, Long> characterIdMap = new LinkedHashMap<>();
        Map<String, VoiceProfilePayload> voiceProfileMap = buildVoiceProfileMap(payload == null ? null : payload.soundDesign);
        int index = 1;
        for (CharacterPayload item : safeList(payload.characters)) {
            AiVideoCharacterPo character = new AiVideoCharacterPo();
            character.setProjectId(project.getProjectId());
            character.setTenantId(project.getTenantId());
            character.setCharacterName(defaultString(item.characterName, "角色" + index));
            character.setGender(item.gender);
            character.setAgeDesc(item.ageDesc);
            character.setIdentityDesc(item.identityDesc);
            character.setPersonalityTags(join(item.personalityTags));
            character.setStoryRole(item.storyRole);
            character.setRelationshipDesc(item.relationshipDesc);
            character.setAppearance(item.appearance);
            character.setHairStyle(item.hairStyle);
            character.setCostume(item.costume);
            character.setColorStyle(item.colorStyle);
            character.setNegativeTraits(item.negativeTraits);
            character.setPromptText(item.promptText);
            character.setCompleteness(item.completeness);
            character.setMissingFields(join(item.missingFields));
            applyVoiceProfile(character, voiceProfileMap.get(normalizeAssetName(character.getCharacterName())));
            character.setConfirmStatus(CONFIRM_PENDING);
            character.setSortOrder(index++);
            character.setDelFlag(DEL_FLAG_NORMAL);
            fillCreateAudit(character);
            characterMapper.insert(character);
            characterIdMap.put(character.getCharacterName(), character.getCharacterId());
        }

        Map<String, Long> sceneIdMap = new LinkedHashMap<>();
        index = 1;
        for (ScenePayload item : safeList(payload.scenes)) {
            AiVideoScenePo scene = new AiVideoScenePo();
            scene.setProjectId(project.getProjectId());
            scene.setTenantId(project.getTenantId());
            scene.setSceneName(defaultString(item.sceneName, "场景" + index));
            scene.setSceneType(item.sceneType);
            scene.setEpisodeNo(item.episodeNo);
            scene.setTimeDesc(item.timeDesc);
            scene.setWeather(item.weather);
            scene.setAtmosphere(item.atmosphere);
            scene.setVisualFeatures(item.visualFeatures);
            scene.setColorTone(item.colorTone);
            scene.setProps(item.props);
            scene.setNegativeElements(item.negativeElements);
            scene.setPromptText(item.promptText);
            scene.setCompleteness(item.completeness);
            scene.setMissingFields(join(item.missingFields));
            scene.setConfirmStatus(CONFIRM_PENDING);
            scene.setSortOrder(index++);
            scene.setDelFlag(DEL_FLAG_NORMAL);
            fillCreateAudit(scene);
            sceneMapper.insert(scene);
            sceneIdMap.put(scene.getSceneName(), scene.getSceneId());
        }

        index = 1;
        for (PropPayload item : safeList(payload.props)) {
            AiVideoPropPo prop = new AiVideoPropPo();
            prop.setProjectId(project.getProjectId());
            prop.setTenantId(project.getTenantId());
            prop.setPropName(defaultString(item.propName, "道具" + index));
            prop.setPropType(item.propType);
            prop.setVisualDesc(item.visualDesc);
            prop.setColor(item.color);
            prop.setMaterial(item.material);
            prop.setShape(item.shape);
            prop.setOwnerCharacterName(item.ownerCharacterName);
            prop.setFirstShotNo(item.firstShotNo);
            prop.setLastHolder(item.lastHolder);
            prop.setContinuityRules(item.continuityRules);
            prop.setPromptText(item.promptText);
            prop.setConfirmStatus(CONFIRM_PENDING);
            prop.setSortOrder(index++);
            prop.setDelFlag(DEL_FLAG_NORMAL);
            fillCreateAudit(prop);
            requirePropMapper().insert(prop);
        }

        Map<String, String> characterNameById = buildCharacterNameById(characterIdMap);
        int duration = normalizeShotDuration(setting != null ? setting.getDefaultShotDuration() : null);
        index = 1;
        AiVideoShotPo previousShot = null;
        int stitchGroupNo = 1;
        for (ShotPayload item : safeList(payload.shots)) {
            AiVideoShotPo shot = new AiVideoShotPo();
            shot.setProjectId(project.getProjectId());
            shot.setTenantId(project.getTenantId());
            shot.setEpisodeNo(item.episodeNo != null ? item.episodeNo : 1);
            shot.setShotNo(item.shotNo != null ? item.shotNo : index);
            shot.setDurationSec(normalizeShotDuration(item.durationSec != null ? item.durationSec : duration));
            shot.setSceneId(resolveSceneId(item, sceneIdMap));
            shot.setCharacterIds(resolveCharacterIds(item, characterIdMap));
            shot.setShotType(item.shotType);
            shot.setCameraPosition(item.cameraPosition);
            shot.setCameraMovement(item.cameraMovement);
            String transitionBeforeType = normalizeTransitionBeforeType(item.transitionBeforeType, shot, previousShot);
            if (previousShot != null && isTransitionBreak(transitionBeforeType)) {
                stitchGroupNo++;
            }
            shot.setTransitionBeforeType(transitionBeforeType);
            shot.setTransitionBeforeDesc(buildTransitionBeforeDesc(item.transitionBeforeDesc, transitionBeforeType, shot,
                    previousShot, sceneIdMap));
            shot.setTransitionEffect(normalizeTransitionEffect(item.transitionEffect, transitionBeforeType));
            shot.setStitchGroupNo(item.stitchGroupNo != null ? item.stitchGroupNo : stitchGroupNo);
            shot.setActionDesc(item.actionDesc);
            shot.setDialogue(item.dialogue);
            shot.setVoiceOver(item.voiceOver);
            shot.setEmotion(item.emotion);
            shot.setBgmCue(item.bgmCue);
            shot.setSfxCues(joinFlexible(item.sfxCues));
            shot.setPromptText(item.promptText);
            normalizePreviousCharacterContinuity(shot, previousShot, characterNameById);
            shot.setConfirmStatus(CONFIRM_PENDING);
            shot.setGenerationStatus(CONFIRM_PENDING);
            shot.setSortOrder(index++);
            shot.setDelFlag(DEL_FLAG_NORMAL);
            fillCreateAudit(shot);
            shotMapper.insert(shot);
            previousShot = shot;
        }
    }

    private Map<String, VoiceProfilePayload> buildVoiceProfileMap(SoundDesignPayload soundDesign) {
        Map<String, VoiceProfilePayload> result = new LinkedHashMap<>();
        if (soundDesign == null || soundDesign.voiceProfiles == null || soundDesign.voiceProfiles.isEmpty()) {
            return result;
        }
        for (VoiceProfilePayload profile : soundDesign.voiceProfiles) {
            String key = normalizeAssetName(profile == null ? null : profile.characterName);
            if (StringUtils.hasText(key)) {
                result.putIfAbsent(key, profile);
            }
        }
        return result;
    }

    private void applyVoiceProfile(AiVideoCharacterPo character, VoiceProfilePayload profile) {
        if (character == null || profile == null) {
            return;
        }
        character.setVoiceMode("POST_TTS");
        character.setVoiceType(trimToNull(profile.recommendedVoiceType));
        character.setVoiceName(defaultString(profile.voiceName, defaultString(character.getCharacterName(), "角色") + "声线"));
        character.setVoiceDesc(limit(joinNonBlank("；",
                profile.voiceStyle,
                profile.speed,
                profile.emotionRange,
                profile.referenceAudioNeed,
                profile.rules), 512));
        character.setVoiceSampleText(limit(firstText(profile.sampleText, profile.referenceAudioNeed), 512));
    }

    private String joinNonBlank(String delimiter, String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return List.of(values).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(delimiter));
    }

    private String normalizeAssetName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("[\\s　]+", "");
    }

    static String normalizeTransitionBeforeType(String value, AiVideoShotPo shot, AiVideoShotPo previousShot) {
        String normalized = value != null ? value.trim().toUpperCase() : "";
        if (previousShot == null) {
            return "OPENING";
        }
        if (!Objects.equals(shot != null ? shot.getSceneId() : null, previousShot.getSceneId())) {
            return List.of("SCENE_CUT", "TIME_JUMP", "MONTAGE").contains(normalized) ? normalized : "SCENE_CUT";
        }
        if (hasNewCharacters(shot, previousShot)) {
            return List.of("SCENE_CUT", "TIME_JUMP", "MONTAGE").contains(normalized) ? normalized : "INSERT";
        }
        return List.of("CONTINUE", "SCENE_CUT", "TIME_JUMP", "MONTAGE", "INSERT").contains(normalized)
                ? normalized : "CONTINUE";
    }

    static boolean isTransitionBreak(String transitionBeforeType) {
        String value = transitionBeforeType != null ? transitionBeforeType.trim().toUpperCase() : "";
        return "SCENE_CUT".equals(value) || "TIME_JUMP".equals(value) || "MONTAGE".equals(value);
    }

    static boolean hasNewCharacters(AiVideoShotPo shot, AiVideoShotPo previousShot) {
        List<String> currentCharacters = parseCharacterTokens(shot != null ? shot.getCharacterIds() : null);
        if (currentCharacters.isEmpty()) {
            return false;
        }
        List<String> previousCharacters = parseCharacterTokens(previousShot != null ? previousShot.getCharacterIds() : null);
        if (previousCharacters.isEmpty()) {
            return true;
        }
        return currentCharacters.stream().anyMatch(character -> !previousCharacters.contains(character));
    }

    static List<String> parseCharacterTokens(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : value.split("[,，、\\s]+")) {
            if (StringUtils.hasText(token)) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }

    private String buildTransitionBeforeDesc(String explicitDesc, String transitionBeforeType, AiVideoShotPo shot,
                                             AiVideoShotPo previousShot, Map<String, Long> sceneIdMap) {
        if (StringUtils.hasText(explicitDesc)) {
            return explicitDesc.trim();
        }
        if (previousShot == null || "OPENING".equals(transitionBeforeType)) {
            return "开场镜头，建立当前场景。";
        }
        String currentSceneName = sceneNameById(sceneIdMap, shot.getSceneId());
        String previousSceneName = sceneNameById(sceneIdMap, previousShot.getSceneId());
        if ("CONTINUE".equals(transitionBeforeType)) {
            return "延续上一镜头，同一场景内连续动作。";
        }
        if ("INSERT".equals(transitionBeforeType)) {
            return "同场景切人/插入镜头，不强制继承上一尾帧。";
        }
        if ("SCENE_CUT".equals(transitionBeforeType)) {
            return "明确切场：" + firstText(previousSceneName, "上一场景") + " -> "
                    + firstText(currentSceneName, "当前场景") + "。";
        }
        if ("TIME_JUMP".equals(transitionBeforeType)) {
            return "时间跳转后进入当前镜头。";
        }
        if ("MONTAGE".equals(transitionBeforeType)) {
            return "蒙太奇转场进入当前镜头。";
        }
        return "插入镜头，不按上一尾帧做连续衔接。";
    }

    private void normalizePreviousCharacterContinuity(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                       Map<String, String> characterNameById) {
        if (shot == null || previousShot == null
                || !Objects.equals(shot.getSceneId(), previousShot.getSceneId())
                || isTransitionBreak(shot.getTransitionBeforeType())) {
            return;
        }
        List<String> previousCharacters = parseCharacterTokens(previousShot.getCharacterIds());
        if (previousCharacters.isEmpty()) {
            return;
        }
        List<String> currentCharacters = parseCharacterTokens(shot.getCharacterIds());
        String shotText = collectShotPoText(shot);
        List<String> missingNames = new ArrayList<>();
        for (String previousCharacter : previousCharacters) {
            String previousName = characterName(previousCharacter, characterNameById);
            if (!StringUtils.hasText(previousName)
                    || containsCharacter(currentCharacters, previousCharacter, previousName)
                    || isOffscreenCharacterMention(shotText, previousName)) {
                continue;
            }
            missingNames.add(previousName);
        }
        if (missingNames.isEmpty()) {
            return;
        }

        String currentNames = currentCharacters.stream()
                .map(character -> characterName(character, characterNameById))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("、"));
        if (!StringUtils.hasText(currentNames)) {
            currentNames = "当前核心角色";
        }
        String missingText = String.join("、", missingNames);
        shot.setActionDesc(normalizeVagueOffscreenReferences(shot.getActionDesc(), missingNames));
        shot.setTransitionBeforeDesc(appendIfMissing(shot.getTransitionBeforeDesc(),
                "同场景单人/插入镜头衔接：上一镜角色" + missingText
                        + "仍在同一场景画外右侧/近旁，不入画；本镜只拍" + currentNames + "单人反应。"));
        shot.setPromptText(appendIfMissing(shot.getPromptText(),
                "本镜画内只出现" + currentNames + "；" + missingText
                        + "在画外右侧/近旁不入画，禁止生成未绑定人物。"));
    }

    private Map<String, String> buildCharacterNameById(Map<String, Long> characterIdMap) {
        if (characterIdMap == null || characterIdMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : characterIdMap.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && entry.getValue() != null) {
                result.put(String.valueOf(entry.getValue()), entry.getKey().trim());
            }
        }
        return result;
    }

    private String normalizeVagueOffscreenReferences(String actionDesc, List<String> missingNames) {
        String target = String.join("、", missingNames);
        String positionedTarget = "画外右侧的" + target;
        String result = StringUtils.hasText(actionDesc) ? actionDesc.trim() : "";
        if (StringUtils.hasText(result)) {
            result = result.replace("画外的同伴", positionedTarget)
                    .replace("画外同伴", positionedTarget)
                    .replace("画外两人", positionedTarget)
                    .replace("画外三人", positionedTarget)
                    .replace("旁边的人", positionedTarget)
                    .replace("看着两人", "看向" + positionedTarget)
                    .replace("看向两人", "看向" + positionedTarget)
                    .replace("望向两人", "望向" + positionedTarget)
                    .replace("与两人对视", "与" + positionedTarget + "对视")
                    .replace("与同伴对视", "与" + positionedTarget + "对视")
                    .replace("和同伴对视", "与" + positionedTarget + "对视")
                    .replace("看向同伴", "看向" + positionedTarget)
                    .replace("望向同伴", "望向" + positionedTarget)
                    .replace("回应同伴", "回应" + positionedTarget);
        }
        if (!StringUtils.hasText(result)) {
            return target + "在画外右侧近旁不入画。";
        }
        boolean containsMissingName = missingNames.stream().anyMatch(result::contains);
        if (containsMissingName) {
            return result;
        }
        return appendIfMissing(result, target + "在画外右侧近旁不入画。");
    }

    private String appendIfMissing(String value, String note) {
        if (!StringUtils.hasText(note)) {
            return value;
        }
        if (!StringUtils.hasText(value)) {
            return note;
        }
        String trimmed = value.trim();
        return trimmed.contains(note) ? trimmed : trimmed + " " + note;
    }

    private String collectShotPoText(AiVideoShotPo shot) {
        if (shot == null) {
            return "";
        }
        return firstText(shot.getActionDesc(), "") + "\n"
                + firstText(shot.getPromptText(), "") + "\n"
                + firstText(shot.getDialogue(), "") + "\n"
                + firstText(shot.getVoiceOver(), "") + "\n"
                + firstText(shot.getTransitionBeforeDesc(), "");
    }

    private String characterName(String characterToken, Map<String, String> characterNameById) {
        if (!StringUtils.hasText(characterToken)) {
            return "";
        }
        String token = characterToken.trim();
        return characterNameById != null ? firstText(characterNameById.get(token), token) : token;
    }

    private boolean containsCharacter(List<String> characterTokens, String characterId, String characterName) {
        if (characterTokens == null || characterTokens.isEmpty()) {
            return false;
        }
        return characterTokens.stream().anyMatch(token -> Objects.equals(token, characterId)
                || (StringUtils.hasText(characterName) && Objects.equals(token, characterName)));
    }

    private String normalizeTransitionEffect(String value, String transitionBeforeType) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        if ("OPENING".equals(transitionBeforeType) || "CONTINUE".equals(transitionBeforeType)) {
            return "hard_cut";
        }
        if ("TIME_JUMP".equals(transitionBeforeType)) {
            return "fade_black";
        }
        if ("MONTAGE".equals(transitionBeforeType)) {
            return "dissolve";
        }
        return "hard_cut";
    }

    private String sceneNameById(Map<String, Long> sceneIdMap, Long sceneId) {
        if (sceneId == null || sceneIdMap == null) {
            return "";
        }
        return sceneIdMap.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), sceneId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    private Long resolveSceneId(ShotPayload item, Map<String, Long> sceneIdMap) {
        if (item.sceneId != null) {
            return item.sceneId;
        }
        if (StringUtils.hasText(item.sceneName)) {
            return sceneIdMap.get(item.sceneName.trim());
        }
        return null;
    }

    private String resolveCharacterIds(ShotPayload item, Map<String, Long> characterIdMap) {
        if (item == null) {
            return null;
        }
        Set<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(item.characterIds)) {
            ids.addAll(parseCharacterTokens(item.characterIds));
        }
        if (item.characterNames != null) {
            for (String name : item.characterNames) {
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                Long id = characterIdMap.get(name.trim());
                ids.add(id == null ? name.trim() : String.valueOf(id));
            }
        }
        addMentionedRelationCharacterIds(ids, characterIdMap, collectShotPayloadText(item));
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    private void addMentionedRelationCharacterIds(Set<String> ids, Map<String, Long> characterIdMap, String text) {
        if (ids == null || characterIdMap == null || characterIdMap.isEmpty()
                || !isRelationshipActionText(text)) {
            return;
        }
        for (Map.Entry<String, Long> entry : characterIdMap.entrySet()) {
            String name = entry.getKey();
            Long id = entry.getValue();
            if (!StringUtils.hasText(name) || id == null || !text.contains(name.trim())) {
                continue;
            }
            String idText = String.valueOf(id);
            String trimmedName = name.trim();
            if (isOffscreenCharacterMention(text, trimmedName)) {
                continue;
            }
            if (!ids.contains(idText) && !ids.contains(trimmedName)) {
                ids.add(idText);
            }
        }
    }

    private String collectShotPayloadText(ShotPayload item) {
        if (item == null) {
            return "";
        }
        return firstText(item.actionDesc, "") + "\n"
                + firstText(item.promptText, "") + "\n"
                + firstText(item.dialogue, "") + "\n"
                + firstText(item.voiceOver, "") + "\n"
                + firstText(item.transitionBeforeDesc, "");
    }

    private boolean isRelationshipActionText(String text) {
        return containsAny(text, "靠近", "凑近", "走向", "看向", "望向", "旁边", "身边",
                "递给", "递向", "递出", "交给", "传给", "拿给", "接过", "接住", "收下",
                "从", "对话", "同框", "两人", "三人", "多人", "一起", "并肩", "互动");
    }

    private boolean isOffscreenCharacterMention(String text, String characterName) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(characterName) || !text.contains(characterName.trim())) {
            return false;
        }
        String name = characterName.trim();
        int index = text.indexOf(name);
        while (index >= 0) {
            String context = text.substring(Math.max(0, index - 16), Math.min(text.length(), index + name.length() + 16));
            if (containsAny(context, "画外", "画外音", "旁白", "不出现", "不入画", "镜外", "离场", "离开",
                    "退出画面", "退出画外", "只闻其声", "声音传来", "脑海", "心里", "内心独白",
                    "单人反应", "只拍", "只露手", "只露肩", "只露背影", "特写裁切")) {
                return true;
            }
            index = text.indexOf(name, index + name.length());
        }
        return false;
    }

    private AssetPayload parseAssetPayload(String content) {
        try {
            String json = extractJsonBlock(content);
            AssetPayload payload = XuJsonUtil.parseObject(json, AssetPayload.class);
            if (payload == null || (safeList(payload.characters).isEmpty()
                    && safeList(payload.scenes).isEmpty()
                    && safeList(payload.props).isEmpty()
                    && safeList(payload.shots).isEmpty())) {
                throw new BusinessException("资产提取结果为空");
            }
            return payload;
        } catch (RuntimeException exception) {
            if (isProbablyTruncatedAssetJson(content)) {
                throw new BusinessException("结构化资产解析失败：JSON 未闭合，疑似输出过长被截断，请减少单次输出或重新生成");
            }
            throw new BusinessException("结构化资产解析失败，请重新生成或补充提示词");
        }
    }

    private String extractJsonBlock(String content) {
        return normalizeAssetJsonBlock(content);
    }

    static String normalizeAssetJsonBlock(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("结构化结果为空");
        }
        String text = content.trim();
        int fenceIndex = text.indexOf("```");
        if (fenceIndex >= 0) {
            int start = text.indexOf('\n', fenceIndex);
            int end = text.indexOf("```", start + 1);
            if (start >= 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        int assetKeyStart = firstAssetKeyIndex(text);
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (assetKeyStart >= 0 && (objectStart < 0 || assetKeyStart < objectStart)) {
            String wrappedFragment = wrapJsonObjectFragment(text);
            if (StringUtils.hasText(wrappedFragment)) {
                return wrappedFragment;
            }
        }
        if (objectStart < 0 || objectEnd <= objectStart) {
            String wrappedFragment = wrapJsonObjectFragment(text);
            if (StringUtils.hasText(wrappedFragment)) {
                return wrappedFragment;
            }
            throw new BusinessException("结构化结果缺少 JSON 对象");
        }
        return text.substring(objectStart, objectEnd + 1);
    }

    private static String wrapJsonObjectFragment(String text) {
        String fragment = text.trim();
        int keyStart = firstAssetKeyIndex(fragment);
        if (keyStart < 0) {
            return null;
        }
        fragment = fragment.substring(keyStart).trim();
        int end = Math.max(fragment.lastIndexOf(']'), fragment.lastIndexOf('}'));
        if (end < 0) {
            return null;
        }
        fragment = fragment.substring(0, end + 1).trim();
        return "{" + fragment + "}";
    }

    private static int firstAssetKeyIndex(String text) {
        int result = -1;
        for (String key : List.of("\"characters\"", "\"scenes\"", "\"props\"", "\"soundDesign\"", "\"shots\"")) {
            int index = text.indexOf(key);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    static boolean isProbablyTruncatedAssetJson(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String text = content.trim();
        if (firstAssetKeyIndex(text) < 0) {
            return false;
        }
        int objectStart = text.indexOf('{');
        if (objectStart < 0) {
            return false;
        }
        return hasUnbalancedJsonDelimiters(text.substring(objectStart));
    }

    private static boolean hasUnbalancedJsonDelimiters(String text) {
        int objectDepth = 0;
        int arrayDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                objectDepth++;
            } else if (current == '}') {
                objectDepth--;
            } else if (current == '[') {
                arrayDepth++;
            } else if (current == ']') {
                arrayDepth--;
            }
        }
        return inString || objectDepth != 0 || arrayDepth != 0;
    }

    private Map<String, Object> buildAssetCounts(Long projectId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("characters", characterMapper.selectCount(baseCharacterWrapper(projectId)));
        counts.put("scenes", sceneMapper.selectCount(baseSceneWrapper(projectId)));
        counts.put("props", propMapper == null ? 0 : propMapper.selectCount(basePropWrapper(projectId)));
        counts.put("shots", shotMapper.selectCount(baseShotWrapper(projectId)));
        return counts;
    }

    private void softDeletePendingAssets(Long projectId) {
        characterMapper.update(null, new LambdaUpdateWrapper<AiVideoCharacterPo>()
                .set(AiVideoCharacterPo::getDelFlag, 1)
                .eq(AiVideoCharacterPo::getProjectId, projectId)
                .eq(AiVideoCharacterPo::getDelFlag, DEL_FLAG_NORMAL)
                .ne(AiVideoCharacterPo::getConfirmStatus, CONFIRM_APPROVED));
        sceneMapper.update(null, new LambdaUpdateWrapper<AiVideoScenePo>()
                .set(AiVideoScenePo::getDelFlag, 1)
                .eq(AiVideoScenePo::getProjectId, projectId)
                .eq(AiVideoScenePo::getDelFlag, DEL_FLAG_NORMAL)
                .ne(AiVideoScenePo::getConfirmStatus, CONFIRM_APPROVED));
        if (propMapper != null) {
            propMapper.update(null, new LambdaUpdateWrapper<AiVideoPropPo>()
                    .set(AiVideoPropPo::getDelFlag, 1)
                    .eq(AiVideoPropPo::getProjectId, projectId)
                    .eq(AiVideoPropPo::getDelFlag, DEL_FLAG_NORMAL)
                    .ne(AiVideoPropPo::getConfirmStatus, CONFIRM_APPROVED));
        }
        shotMapper.update(null, new LambdaUpdateWrapper<AiVideoShotPo>()
                .set(AiVideoShotPo::getDelFlag, 1)
                .eq(AiVideoShotPo::getProjectId, projectId)
                .eq(AiVideoShotPo::getDelFlag, DEL_FLAG_NORMAL)
                .ne(AiVideoShotPo::getConfirmStatus, CONFIRM_APPROVED));
    }

    private void approveAllAssets(Long projectId) {
        characterMapper.update(null, new LambdaUpdateWrapper<AiVideoCharacterPo>()
                .set(AiVideoCharacterPo::getConfirmStatus, CONFIRM_APPROVED)
                .eq(AiVideoCharacterPo::getProjectId, projectId)
                .eq(AiVideoCharacterPo::getDelFlag, DEL_FLAG_NORMAL));
        sceneMapper.update(null, new LambdaUpdateWrapper<AiVideoScenePo>()
                .set(AiVideoScenePo::getConfirmStatus, CONFIRM_APPROVED)
                .eq(AiVideoScenePo::getProjectId, projectId)
                .eq(AiVideoScenePo::getDelFlag, DEL_FLAG_NORMAL));
        if (propMapper != null) {
            propMapper.update(null, new LambdaUpdateWrapper<AiVideoPropPo>()
                    .set(AiVideoPropPo::getConfirmStatus, CONFIRM_APPROVED)
                    .eq(AiVideoPropPo::getProjectId, projectId)
                    .eq(AiVideoPropPo::getDelFlag, DEL_FLAG_NORMAL));
        }
        shotMapper.update(null, new LambdaUpdateWrapper<AiVideoShotPo>()
                .set(AiVideoShotPo::getConfirmStatus, CONFIRM_APPROVED)
                .eq(AiVideoShotPo::getProjectId, projectId)
                .eq(AiVideoShotPo::getDelFlag, DEL_FLAG_NORMAL));
    }

    private void cancelAllAssets(Long projectId) {
        characterMapper.update(null, new LambdaUpdateWrapper<AiVideoCharacterPo>()
                .set(AiVideoCharacterPo::getConfirmStatus, CONFIRM_PENDING)
                .eq(AiVideoCharacterPo::getProjectId, projectId)
                .eq(AiVideoCharacterPo::getDelFlag, DEL_FLAG_NORMAL));
        sceneMapper.update(null, new LambdaUpdateWrapper<AiVideoScenePo>()
                .set(AiVideoScenePo::getConfirmStatus, CONFIRM_PENDING)
                .eq(AiVideoScenePo::getProjectId, projectId)
                .eq(AiVideoScenePo::getDelFlag, DEL_FLAG_NORMAL));
        if (propMapper != null) {
            propMapper.update(null, new LambdaUpdateWrapper<AiVideoPropPo>()
                    .set(AiVideoPropPo::getConfirmStatus, CONFIRM_PENDING)
                    .eq(AiVideoPropPo::getProjectId, projectId)
                    .eq(AiVideoPropPo::getDelFlag, DEL_FLAG_NORMAL));
        }
        shotMapper.update(null, new LambdaUpdateWrapper<AiVideoShotPo>()
                .set(AiVideoShotPo::getConfirmStatus, CONFIRM_PENDING)
                .eq(AiVideoShotPo::getProjectId, projectId)
                .eq(AiVideoShotPo::getDelFlag, DEL_FLAG_NORMAL));
    }

    private void approveSingleAsset(AiVideoProjectPo project, String targetType, Long targetId, String comment) {
        switch (targetType) {
            case TARGET_CHARACTER -> {
                AiVideoCharacterPo character = characterMapper.selectById(targetId);
                if (character == null || !Objects.equals(project.getProjectId(), character.getProjectId())) {
                    throw new BusinessException("角色资产不存在");
                }
                String before = character.getConfirmStatus();
                character.setConfirmStatus(CONFIRM_APPROVED);
                fillUpdateAudit(character);
                characterMapper.updateById(character);
                insertReview(project, TARGET_CHARACTER, targetId, ACTION_CONFIRM, before, CONFIRM_APPROVED, comment, null);
            }
            case TARGET_SCENE -> {
                AiVideoScenePo scene = sceneMapper.selectById(targetId);
                if (scene == null || !Objects.equals(project.getProjectId(), scene.getProjectId())) {
                    throw new BusinessException("场景资产不存在");
                }
                String before = scene.getConfirmStatus();
                scene.setConfirmStatus(CONFIRM_APPROVED);
                fillUpdateAudit(scene);
                sceneMapper.updateById(scene);
                insertReview(project, TARGET_SCENE, targetId, ACTION_CONFIRM, before, CONFIRM_APPROVED, comment, null);
            }
            case TARGET_PROP -> {
                AiVideoPropPo prop = requirePropMapper().selectById(targetId);
                if (prop == null || !Objects.equals(project.getProjectId(), prop.getProjectId())) {
                    throw new BusinessException("道具资产不存在");
                }
                String before = prop.getConfirmStatus();
                prop.setConfirmStatus(CONFIRM_APPROVED);
                fillUpdateAudit(prop);
                requirePropMapper().updateById(prop);
                insertReview(project, TARGET_PROP, targetId, ACTION_CONFIRM, before, CONFIRM_APPROVED, comment, null);
            }
            case TARGET_SHOT -> {
                AiVideoShotPo shot = shotMapper.selectById(targetId);
                if (shot == null || !Objects.equals(project.getProjectId(), shot.getProjectId())) {
                    throw new BusinessException("分镜资产不存在");
                }
                String before = shot.getConfirmStatus();
                shot.setConfirmStatus(CONFIRM_APPROVED);
                fillUpdateAudit(shot);
                shotMapper.updateById(shot);
                insertReview(project, TARGET_SHOT, targetId, ACTION_CONFIRM, before, CONFIRM_APPROVED, comment, null);
            }
            default -> throw new BusinessException("不支持的资产确认类型");
        }
    }

    private void cancelSingleAsset(AiVideoProjectPo project, String targetType, Long targetId, String comment) {
        switch (targetType) {
            case TARGET_CHARACTER -> {
                AiVideoCharacterPo character = characterMapper.selectById(targetId);
                if (character == null || !Objects.equals(project.getProjectId(), character.getProjectId())
                        || !Integer.valueOf(DEL_FLAG_NORMAL).equals(character.getDelFlag())) {
                    throw new BusinessException("角色资产不存在");
                }
                String before = character.getConfirmStatus();
                character.setConfirmStatus(CONFIRM_PENDING);
                fillUpdateAudit(character);
                characterMapper.updateById(character);
                insertReview(project, TARGET_CHARACTER, targetId, ACTION_CANCEL_CONFIRM, before, CONFIRM_PENDING, comment, null);
            }
            case TARGET_SCENE -> {
                AiVideoScenePo scene = sceneMapper.selectById(targetId);
                if (scene == null || !Objects.equals(project.getProjectId(), scene.getProjectId())
                        || !Integer.valueOf(DEL_FLAG_NORMAL).equals(scene.getDelFlag())) {
                    throw new BusinessException("场景资产不存在");
                }
                String before = scene.getConfirmStatus();
                scene.setConfirmStatus(CONFIRM_PENDING);
                fillUpdateAudit(scene);
                sceneMapper.updateById(scene);
                insertReview(project, TARGET_SCENE, targetId, ACTION_CANCEL_CONFIRM, before, CONFIRM_PENDING, comment, null);
            }
            case TARGET_PROP -> {
                AiVideoPropPo prop = requirePropMapper().selectById(targetId);
                if (prop == null || !Objects.equals(project.getProjectId(), prop.getProjectId())
                        || !Integer.valueOf(DEL_FLAG_NORMAL).equals(prop.getDelFlag())) {
                    throw new BusinessException("道具资产不存在");
                }
                String before = prop.getConfirmStatus();
                prop.setConfirmStatus(CONFIRM_PENDING);
                fillUpdateAudit(prop);
                requirePropMapper().updateById(prop);
                insertReview(project, TARGET_PROP, targetId, ACTION_CANCEL_CONFIRM, before, CONFIRM_PENDING, comment, null);
            }
            case TARGET_SHOT -> {
                AiVideoShotPo shot = shotMapper.selectById(targetId);
                if (shot == null || !Objects.equals(project.getProjectId(), shot.getProjectId())
                        || !Integer.valueOf(DEL_FLAG_NORMAL).equals(shot.getDelFlag())) {
                    throw new BusinessException("分镜资产不存在");
                }
                String before = shot.getConfirmStatus();
                shot.setConfirmStatus(CONFIRM_PENDING);
                fillUpdateAudit(shot);
                shotMapper.updateById(shot);
                insertReview(project, TARGET_SHOT, targetId, ACTION_CANCEL_CONFIRM, before, CONFIRM_PENDING, comment, null);
            }
            default -> throw new BusinessException("不支持的资产取消确认类型");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShotScene(AivideoShotSceneUpdateDto dto) {
        if (dto == null || dto.getProjectId() == null || dto.getShotId() == null || dto.getSceneId() == null) {
            throw new BusinessException("项目ID、分镜ID和场景ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoShotPo shot = shotMapper.selectById(dto.getShotId());
        if (shot == null || !Objects.equals(project.getProjectId(), shot.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(shot.getDelFlag())) {
            throw new BusinessException("分镜不存在或不属于当前项目");
        }
        AiVideoScenePo scene = sceneMapper.selectById(dto.getSceneId());
        if (scene == null || !Objects.equals(project.getProjectId(), scene.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(scene.getDelFlag())) {
            throw new BusinessException("场景不存在或不属于当前项目");
        }
        shot.setSceneId(scene.getSceneId());
        refreshShotTransition(project.getProjectId(), shot);
        fillUpdateAudit(shot);
        shotMapper.updateById(shot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCharacterVoice(AivideoCharacterVoiceUpdateDto dto) {
        if (dto == null || dto.getProjectId() == null || dto.getCharacterId() == null) {
            throw new BusinessException("项目ID和角色ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoCharacterPo character = characterMapper.selectById(dto.getCharacterId());
        if (character == null || !Objects.equals(project.getProjectId(), character.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(character.getDelFlag())) {
            throw new BusinessException("角色资产不存在或不属于当前项目");
        }
        character.setVoiceMode(trimToNull(dto.getVoiceMode()));
        character.setVoiceType(trimToNull(dto.getVoiceType()));
        character.setVoiceName(trimToNull(dto.getVoiceName()));
        character.setVoiceDesc(trimToNull(dto.getVoiceDesc()));
        character.setVoiceReferenceMediaId(dto.getVoiceReferenceMediaId());
        character.setVoiceSampleText(trimToNull(dto.getVoiceSampleText()));
        character.setVoiceSpeedRatio(dto.getVoiceSpeedRatio());
        character.setVoiceVolumeRatio(dto.getVoiceVolumeRatio());
        character.setVoicePitchRatio(dto.getVoicePitchRatio());
        fillUpdateAudit(character);
        characterMapper.updateById(character);
    }

    private void refreshShotTransition(Long projectId, AiVideoShotPo shot) {
        List<AiVideoShotPo> orderedShots = selectShots(projectId);
        if (orderedShots == null || orderedShots.isEmpty()) {
            return;
        }
        AiVideoShotPo previousShot = null;
        for (int i = 0; i < orderedShots.size(); i++) {
            AiVideoShotPo item = orderedShots.get(i);
            if (Objects.equals(item.getShotId(), shot.getShotId())) {
                previousShot = i > 0 ? orderedShots.get(i - 1) : null;
                break;
            }
        }
        String transitionType = normalizeTransitionBeforeType(null, shot, previousShot);
        List<AiVideoScenePo> projectScenes = selectScenes(projectId);
        if (projectScenes == null) {
            projectScenes = List.of();
        }
        Map<String, Long> sceneIdMap = projectScenes.stream()
                .filter(item -> item.getSceneId() != null && StringUtils.hasText(item.getSceneName()))
                .collect(Collectors.toMap(AiVideoScenePo::getSceneName, AiVideoScenePo::getSceneId,
                        (left, right) -> left, LinkedHashMap::new));
        shot.setTransitionBeforeType(transitionType);
        shot.setTransitionBeforeDesc(buildTransitionBeforeDesc(null, transitionType, shot, previousShot, sceneIdMap));
        shot.setTransitionEffect(normalizeTransitionEffect(null, transitionType));
    }

    private boolean hasAsset(Long projectId) {
        return characterMapper.selectCount(baseCharacterWrapper(projectId)) > 0
                || sceneMapper.selectCount(baseSceneWrapper(projectId)) > 0
                || shotMapper.selectCount(baseShotWrapper(projectId)) > 0;
    }

    private boolean hasPendingAsset(Long projectId) {
        return characterMapper.selectCount(baseCharacterWrapper(projectId).ne(AiVideoCharacterPo::getConfirmStatus, CONFIRM_APPROVED)) > 0
                || sceneMapper.selectCount(baseSceneWrapper(projectId).ne(AiVideoScenePo::getConfirmStatus, CONFIRM_APPROVED)) > 0
                || shotMapper.selectCount(baseShotWrapper(projectId).ne(AiVideoShotPo::getConfirmStatus, CONFIRM_APPROVED)) > 0;
    }

    private List<AiVideoCharacterPo> selectCharacters(Long projectId) {
        return characterMapper.selectList(baseCharacterWrapper(projectId).orderByAsc(AiVideoCharacterPo::getSortOrder));
    }

    private List<AiVideoScenePo> selectScenes(Long projectId) {
        return sceneMapper.selectList(baseSceneWrapper(projectId).orderByAsc(AiVideoScenePo::getSortOrder));
    }

    private List<AiVideoPropPo> selectProps(Long projectId) {
        return propMapper == null ? List.of() : propMapper.selectList(basePropWrapper(projectId)
                .orderByAsc(AiVideoPropPo::getSortOrder));
    }

    private List<AiVideoShotPo> selectShots(Long projectId) {
        return shotMapper.selectList(baseShotWrapper(projectId)
                .orderByAsc(AiVideoShotPo::getEpisodeNo)
                .orderByAsc(AiVideoShotPo::getShotNo)
                .orderByAsc(AiVideoShotPo::getSortOrder));
    }

    private LambdaQueryWrapper<AiVideoCharacterPo> baseCharacterWrapper(Long projectId) {
        return new LambdaQueryWrapper<AiVideoCharacterPo>()
                .eq(AiVideoCharacterPo::getProjectId, projectId)
                .eq(AiVideoCharacterPo::getDelFlag, DEL_FLAG_NORMAL);
    }

    private LambdaQueryWrapper<AiVideoScenePo> baseSceneWrapper(Long projectId) {
        return new LambdaQueryWrapper<AiVideoScenePo>()
                .eq(AiVideoScenePo::getProjectId, projectId)
                .eq(AiVideoScenePo::getDelFlag, DEL_FLAG_NORMAL);
    }

    private LambdaQueryWrapper<AiVideoPropPo> basePropWrapper(Long projectId) {
        return new LambdaQueryWrapper<AiVideoPropPo>()
                .eq(AiVideoPropPo::getProjectId, projectId)
                .eq(AiVideoPropPo::getDelFlag, DEL_FLAG_NORMAL);
    }

    private LambdaQueryWrapper<AiVideoShotPo> baseShotWrapper(Long projectId) {
        return new LambdaQueryWrapper<AiVideoShotPo>()
                .eq(AiVideoShotPo::getProjectId, projectId)
                .eq(AiVideoShotPo::getDelFlag, DEL_FLAG_NORMAL);
    }

    private void markProjectStage(AiVideoProjectPo project, AivideoProjectStage stage) {
        project.setCurrentStage(stage.name());
        fillUpdateAudit(project);
        projectMapper.updateById(project);
    }

    private void insertReview(AiVideoProjectPo project, String targetType, Long targetId, String actionType,
                              String beforeStatus, String afterStatus, String comment, String extraPrompt) {
        AiVideoReviewRecordPo record = new AiVideoReviewRecordPo();
        record.setProjectId(project.getProjectId());
        record.setTenantId(project.getTenantId());
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setActionType(actionType);
        record.setBeforeStatus(beforeStatus);
        record.setAfterStatus(afterStatus);
        record.setComment(comment);
        record.setExtraPrompt(extraPrompt);
        record.setReviewUserId(currentUserId());
        record.setReviewTime(now());
        record.setCreateTime(now());
        reviewRecordMapper.insert(record);
    }

    private String buildPolishPrompt(AiVideoProjectPo project, String sourceText) {
        return "请将以下原文润色为适合 AI 短剧改编的文本。要求：保留主线与核心冲突，强化角色动机、情绪转折和画面感；"
                + "语言清晰可拍，避免过度文学化；输出完整润色稿。\n\n项目：" + project.getProjectName()
                + "\n风格：" + safeValue(project.getDefaultStyle()) + "\n\n原文：\n" + sourceText;
    }

    private String buildScriptPrompt(AiVideoProjectPo project, String polishedText) {
        return "请将以下润色文本改写为短剧剧本。要求：按场次组织，包含角色、场景、动作、对白、旁白和情绪提示；"
                + "镜头描述要能继续拆分为分镜，避免空泛形容；每个场次必须增加“镜头拆分建议”，写清这段适合拆成几个镜头、"
                + "每个镜头的主动作、是否包含强动作、建议时长 5/6/8 秒；超过 3 个动作 beat 必须建议拆镜，不要硬塞进一个镜头；"
                + "对白、旁白/画外音、心声/心理活动必须三轨分清：对白是角色说出口并可口型同步的台词；旁白/画外音是可发声但角色不张嘴的内容；"
                + "心声/心理活动默认不朗读，优先写成眼神、动作、环境空镜或画面隐喻；低声报数、低声说、耳语、小声说、念出、读出都属于说出口的对白，"
                + "禁止写成旁白、画外音或心声。对白和旁白必须保持说话人连续，跨镜头接同一句话时不能无提示更换声线。"
                + "必须增加【声音设计】小节：为每个主要角色写清角色声线、语速、情绪范围和是否需要固定 voiceType/参考音频；"
                + "为旁白单独写旁白声线；为每个场景或剧情段写背景音乐风格、情绪、起止范围、淡入淡出和人声压低规则；"
                + "为关键动作写音效/环境声建议，如翻纸声、脚步声、门铃、雨声、雷声、塑料碰撞声。"
                + "剧本阶段只定义声音意图，不生成音频；后期语音和混音成片阶段会依据这些声音设计生成配音、BGM 和音效。\n\n项目：" + project.getProjectName()
                + "\n目标平台：" + safeValue(project.getTargetPlatform()) + "\n画幅：" + safeValue(project.getDefaultRatio())
                + "\n\n润色文本：\n" + polishedText;
    }

    private String buildAssetPrompt(AiVideoProjectPo project, AiVideoProjectSettingPo setting, String scriptText) {
        int duration = defaultShotDuration(setting);
        return "请严格依据下面三组参考提示词规则，从短剧剧本中提取【角色、场景、关键道具、分镜】。"
                + "必须只输出 JSON 对象，不要输出解释、Markdown 围栏或额外说明。JSON key 必须保持英文，所有字段值必须使用中文。\n\n"
                + "【最高优先级：紧凑输出，防止 JSON 被截断】\n"
                + "1. 资产阶段只输出可入库的稳定锚点，不在这里写长篇图片/视频执行提示词；角色图、场景图、分镜视频会在后续阶段再扩写 prompt。\n"
                + "2. promptText 必须是短提示：角色/场景不超过 80 个中文字符，分镜不超过 100 个中文字符；禁止写长句、禁止重复画幅/风格堆叠。\n"
                + "3. actionDesc 不超过 60 个中文字符，voiceOver 不超过 80 个中文字符；保留动作节拍核心即可，不能把整段旁白塞进一个字段；心理活动默认不写入 voiceOver。\n"
                + "4. 每个数组元素只保留必要信息，不输出解释性备注、Markdown、编号标题或额外字段；如果信息缺失，用空字符串或空数组。\n"
                + "5. 必须输出完整闭合 JSON，最后一个字符必须是 }。\n\n"
                + "【角色构建规则】\n"
                + "1. 你是电影级角色概念设计师，需要先解析角色画像：代号、年龄/生命阶段、性别或物种、社会身份或物种身份、人格标签、故事功能。\n"
                + "2. 每个角色必须输出鲜明、可区分的视觉方案；如果是人类，写清年龄、自然发色、具体发型、眼神神态、服装材质、主色辅色、鞋履配饰。\n"
                + "3. 如果角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须在 identityDesc、appearance、promptText 中保留物种本体，写清品种/体型/毛色/眼睛/标志性特征，禁止改成人类演员。\n"
                + "4. 多角色必须在色彩、轮廓、材质或身体特征上显著区别，严禁视觉雷同。\n"
                + "5. promptText 要可直接用于 Seedance 视频角色锚定图生成，必须写成单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面 60%-75%。\n"
                + "6. 角色 promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏或同款分身；动物保持自然四足站立，不拟人化。\n\n"
                + "【Seedance 视频场景锚点规则】\n"
                + "1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。\n"
                + "2. 场景名称必须四个字以上，不能只写单一名词，要通过修饰词增加辨识度。\n"
                + "3. 场景必须覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。\n"
                + "4. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty scene、single shot reference。\n"
                + "5. 场景 promptText 必须写成单镜头视频首帧/环境锚点：前景、中景、远景和地面可行动区域清楚，禁止拼图、分栏、设定板、漫画格、文字标签。\n\n"
                + "【剧本分镜规则】\n"
                + "1. 你是顶级影视剧导演与分镜规划专家，需要面向 Seedance 2.0 / 即梦 2.0 的视频生成逻辑拆解镜头。\n"
                + "2. 禁止引入未在角色表、characterNames 或背景人群说明中的无关人物；单人镜头锁定当前核心主角，多人镜头必须按 characterNames 全部入画，不得用单人特写、主观视角或环境遮挡替代同框关系。\n"
                + "3. 严格区分 dialogue、voiceOver 和心理画面：dialogue 只写角色说出口并可口型同步的话；voiceOver 只写可发声但角色不张嘴的旁白/画外音；心理活动默认不写入 voiceOver。\n"
                + "4. dialogue 必须只写当前 characterNames 中角色能直接说出口的话；多角色同镜时必须写成“角色名：台词”。低声报数、低声说、耳语、小声说、念出、读出都属于说出口的对白，必须写入 dialogue，禁止塞进 voiceOver。\n"
                + "5. voiceOver 必须显式标注说话人：统一旁白写“旁白：内容”，角色画外音写“角色名（画外音）：内容”，角色旁白写“角色名（旁白）：内容”；只有确实要被听见的内心独白才写“角色名（内心独白）：内容”。\n"
                + "6. 严禁把海报文字、账本文字、屏幕字卡、价格标签、公告栏文字写入 voiceOver；这类可见文字必须写在 actionDesc 或 promptText 中，除非确实需要旁白朗读。\n"
                + "7. 跨镜头延续同一句话、同一论点或同一段劝说时，必须保持同一说话人；如果画面切到其他角色，必须用“原说话人（画外音）”承接，禁止声线无提示跳到当前画面角色。\n"
                + "7A. 脑海里闪过、想到、意识到、想象、回忆、触感、心里一动等心理内容默认写入 actionDesc/promptText/emotion，用画面表现，不要写成普通 voiceOver。错误示例：voiceOver 写“喵小萌（心声）：脑海里闪过奶茶冰凉甜润的触感”。正确示例：actionDesc 写“喵小萌眼神短暂游离，像想到奶茶的清凉触感，随后握紧班费账本”。\n"
                + "7B. 声音设计资产必须前置输出：角色声线资产写入 soundDesign.voiceProfiles；旁白声线写入 soundDesign.narrationProfile；背景音乐写入 soundDesign.bgmPlan；关键音效/环境声写入 soundDesign.sfxPlan。\n"
                + "7C. 每个分镜必须根据剧本声音设计补充 bgmCue 和 sfxCues：bgmCue 写当前镜头继承/切换/静音的 BGM 意图；sfxCues 写与动作绑定的音效，不能把音效写成旁白。\n"
                + "7D. 角色声线资产必须描述音色、语速、情绪范围、推荐 voiceType 和参考音频需求；BGM 必须描述风格、情绪、作用范围、起止镜头和人声压低规则；SFX 必须描述触发动作、时间点和音量。\n"
                + "8. 每个分镜必须明确地点；延续场景时在 sceneName 或 actionDesc 中体现“延续上个分镜场景，机位微调”。\n"
                + "9. 动作要衔接，不能瞬移；镜头需包含微动作、眼神、呼吸、肢体、环境变化等可拍内容。\n"
                + "10. shotType、cameraPosition、cameraMovement 要优先使用专业运镜词，如极焦特写、近景推轨、环绕摇镜、慢动作/延时、手持震动。\n"
                + "11. durationSec 只能在 5、6、8 中动态选择，不再固定使用项目默认秒数；项目默认镜头秒数仅作为初始参考：" + duration + "。\n"
                + "12. 动作预算：5 秒=1 个主动作 + 1 个反应/表情 + 1 个结尾状态；6 秒=2 个连续动作 + 结尾状态；8 秒=3 个连续动作 + 明确结尾状态。\n"
                + "13. 超过 3 个动作 beat 必须自动拆成多个 shots，不允许硬塞；强动作如倒地起身、悬浮、变身、俯冲、落水、打斗、救援、掰弯铁栏等额外占预算，优先单独作为一个镜头核心。\n"
                + "14. actionDesc 必须写成视频模型能执行的动作节拍，包含起始状态、主动作、反应/表情和结尾状态；promptText 必须补充构图、目标部位可见和部位发光限制。\n"
                + "15. 出现爪子、手、脚、翅膀、尾巴等部位时，必须要求半身/全身构图并露出目标部位；出现发光时必须写清具体发光部位，禁止用眼睛发光替代爪子/手/脚等目标部位发光。\n"
                + "16. 剧情空间连续性是硬约束：后一分镜必须承接前一分镜的主体位置、危险目标、空间关系和结尾状态，不能只因情绪需要突然换地点。\n"
                + "17. 如果上一分镜建立了屋顶、广告牌、铁架、高处、水中、火场、车道等危险目标，下一分镜必须继续该目标、让主角观察/靠近/救援该目标，或在 actionDesc 开头写明过渡动作。\n"
                + "18. 未经剧本铺垫，禁止突然切到狗窝、室内、家里、床下、窝口等新地点；必须先用过渡镜头建立空间关系，或改写为“延续上一镜，镜头回到街边/同一条街道”。\n"
                + "19. 错误示例：上一镜“广告牌铁架上有小身影”，下一镜“狗狗蜷缩在窝的角落”。正确示例：下一镜“延续上一镜，狗狗在街边抬头望向广告牌铁架，身体绷紧准备冲向商铺雨棚”。\n"
                + "20. 错误示例：镜头 A 甜玉米说“廉洁不是做给别人看的”，镜头 B 切到喵小萌并把 voiceOver 写成“而是即使无人知晓……”。正确示例：镜头 B voiceOver 写“甜玉米（画外音）：而是即使无人知晓，也选择对集体负责”。\n\n"
                + "21. 道具交接硬约束：出现接过、递给、展示给、交给、传给、拿给、递来、滚入等动作时，actionDesc/promptText 必须写清 giver、receiver、prop、screenDirection、finalOwner。\n"
                + "22. 禁止只写“展示给画外”“递给画外”“接过某物”“从画外递来”；必须写成“狗小汪从画面左侧把收纳盒递给喵小萌”这类可拍动作。\n"
                + "23. 道具交接必须承接上一镜：上一镜要写清递出/展示对象和结尾姿态，下一镜要写清从谁手中接过、道具进入方向、接过后的归属。\n"
                + "24. 单角色核心镜头如需承接另一个角色递来的道具，必须写“另一角色的手从画面左/右侧入画”，不能让道具凭空出现。\n"
                + "25. 屏幕方向必须稳定：上一镜写画面右侧的接收者，下一镜就要保持对应入画方向，禁止左右关系随机跳变。\n"
                + "26. 错误示例：第4镜“展示给画外”，第5镜“接过收纳盒看了看”。正确示例：第4镜“狗小汪转向画面右侧的喵小萌展示并递出收纳盒”，第5镜“狗小汪的手从画面左侧入画，喵小萌从狗小汪手中接过收纳盒”。\n"
                + "27. 人物在场连续性硬约束：多人同场、对话、靠近、看向、递给、接过、并肩、围观等镜头，actionDesc 或 promptText 必须写“当前镜头在场角色：2人，人物数量=2，画面站位：左侧=喵小萌，右侧=狗小汪；离场说明=无”。\n"
                + "28. 同一 sceneName 且 stitchGroupNo 未变化时，上一镜仍在场角色默认继续在场；除非 transitionBeforeDesc/actionDesc 明确写“某角色退出画外左/右侧、切到某角色单人反应、某角色只露手/肩/背影”，否则不得无说明消失。\n"
                + "29. 多人关系镜头必须写屏幕方向、视线关系和运动方向：例如“喵小萌固定在画面左侧看向右侧狗小汪，狗小汪从画面右侧向左侧靠近喵小萌”；禁止只写“靠近她/走过去/看向旁边”。\n"
                + "30. 上一镜已经建立的画面左侧/右侧关系，下一镜同场景必须继承；如确实换轴、绕场、反打或重新调度，transitionBeforeDesc 必须写清“换轴/反打/重新建立站位”，不能让角色左右随机跳变。\n"
                + "31. 当当前镜头从多人同框切单人反应，仍必须交代其他上一镜角色是否在画外、在哪一侧、是否只露局部；例如“狗小汪仍在画面右侧近旁，仅露肩和手”。\n"
                + "32. 错误示例：上一镜喵小萌左、狗小汪右，下一镜只写“狗小汪凑近喵小萌”导致喵小萌消失。正确示例：“当前镜头在场角色：2人，喵小萌左、狗小汪右，狗小汪从右侧向左侧凑近喵小萌”。\n"
                + "32A. 禁止使用“同伴/对方/两人/三人/旁边的人/画外同伴/画外两人/她/他”代替角色名；凡是对视、看向、回应、靠近、交接，都必须点名角色姓名及画内/画外状态。\n"
                + "32B. 如果 characterNames 只有一个角色但动作涉及对视/看着/靠近/交接/回应/同伴/两人，必须写清其他角色姓名与画外/局部/离场状态；否则必须把该角色加入 characterNames。\n\n"
                + "33. 每个分镜必须输出 transitionBeforeType、transitionBeforeDesc、transitionEffect、stitchGroupNo。transitionBeforeType 只能取 OPENING、CONTINUE、SCENE_CUT、TIME_JUMP、MONTAGE、INSERT。\n"
                + "34. 只有 CONTINUE 才强制使用上一镜尾帧；SCENE_CUT/TIME_JUMP/MONTAGE/INSERT 是明确转场，不要求视频生成阶段继承上一尾帧。\n"
                + "35. 当 sceneName 与上一镜不同，transitionBeforeType 必须写 SCENE_CUT，并在 transitionBeforeDesc 写清“上一场景 -> 当前场景”；不要把切场伪装成连续动作。\n"
                + "36. 当 sceneName 与上一镜相同但切到新角色、不同角色视角、从多人同框切单人反应/表情、物品/票据/手部特写或平行动作时，transitionBeforeType 必须写 INSERT，不要写 CONTINUE；INSERT 不继承上一尾帧。\n"
                + "37. 当当前镜头是空镜、环境镜头、主题升华、叠化、闪回、快速串联、片尾总结或跨多个动作的过渡镜头时，transitionBeforeType 必须写 MONTAGE，并把 transitionEffect 写 dissolve 或 fade_black。\n"
                + "38. 除非剧本明确分集，否则所有 shots 的 episodeNo 固定为 1；不要把场次、地点段落或转场编号写进 episodeNo。\n"
                + "39. stitchGroupNo 表示后期连续拼接组：连续镜头保持同一组，只有遇到 SCENE_CUT/TIME_JUMP/MONTAGE 时组号加 1；INSERT 仍属于同一剪辑组，仅表示同场景切人、插入特写或道具交接，不要在 INSERT 处自动断组。\n\n"
                + "【输出 JSON 结构】\n"
                + "{\"characters\":[{\"characterName\":\"\",\"gender\":\"\",\"ageDesc\":\"\",\"identityDesc\":\"\",\"personalityTags\":[\"\"],"
                + "\"storyRole\":\"\",\"relationshipDesc\":\"\",\"appearance\":\"\",\"hairStyle\":\"\",\"costume\":\"\",\"colorStyle\":\"\","
                + "\"negativeTraits\":\"\",\"promptText\":\"\",\"completeness\":\"\",\"missingFields\":[\"\"]}],"
                + "\"scenes\":[{\"sceneName\":\"\",\"sceneType\":\"\",\"episodeNo\":1,\"timeDesc\":\"\",\"weather\":\"\",\"atmosphere\":\"\","
                + "\"visualFeatures\":\"\",\"colorTone\":\"\",\"props\":\"\",\"negativeElements\":\"\",\"promptText\":\"\",\"completeness\":\"\","
                + "\"missingFields\":[\"\"]}],"
                + "\"props\":[{\"propName\":\"\",\"propType\":\"\",\"visualDesc\":\"\",\"color\":\"\",\"material\":\"\",\"shape\":\"\","
                + "\"ownerCharacterName\":\"\",\"firstShotNo\":1,\"lastHolder\":\"\",\"continuityRules\":\"\",\"promptText\":\"\"}],"
                + "\"soundDesign\":{\"voiceProfiles\":[{\"characterName\":\"\",\"voiceStyle\":\"\",\"speed\":\"\",\"emotionRange\":\"\",\"recommendedVoiceType\":\"\",\"referenceAudioNeed\":\"\",\"rules\":\"\"}],"
                + "\"narrationProfile\":{\"voiceStyle\":\"\",\"speed\":\"\",\"emotionRange\":\"\",\"recommendedVoiceType\":\"\",\"rules\":\"\"},"
                + "\"bgmPlan\":[{\"scope\":\"\",\"mood\":\"\",\"style\":\"\",\"startShot\":1,\"endShot\":1,\"mixRule\":\"\"}],"
                + "\"sfxPlan\":[{\"shotNo\":1,\"effect\":\"\",\"triggerAction\":\"\",\"timing\":\"\",\"volume\":\"\"}]},"
                + "\"shots\":[{\"episodeNo\":1,\"shotNo\":1,\"durationSec\":5,\"sceneName\":\"\",\"characterNames\":[\"\"],"
                + "\"shotType\":\"\",\"cameraPosition\":\"\",\"cameraMovement\":\"\",\"transitionBeforeType\":\"OPENING\","
                + "\"transitionBeforeDesc\":\"\",\"transitionEffect\":\"hard_cut\",\"stitchGroupNo\":1,"
                + "\"actionDesc\":\"\",\"dialogue\":\"\",\"voiceOver\":\"\","
                + "\"emotion\":\"\",\"bgmCue\":\"\",\"sfxCues\":[\"\"],\"promptText\":\"\"}]}\n\n"
                + "项目：" + project.getProjectName()
                + "\n目标平台：" + safeValue(project.getTargetPlatform())
                + "\n画幅：" + safeValue(project.getDefaultRatio())
                + "\n风格：" + safeValue(project.getDefaultStyle())
                + "\n\n剧本：\n" + scriptText;
    }

    private Long firstTemplateId(AiVideoProjectSettingPo setting) {
        if (setting == null) {
            return null;
        }
        if (setting.getCharacterPromptTemplateId() != null) {
            return setting.getCharacterPromptTemplateId();
        }
        if (setting.getScenePromptTemplateId() != null) {
            return setting.getScenePromptTemplateId();
        }
        return setting.getShotPromptTemplateId();
    }

    private int defaultShotDuration(AiVideoProjectSettingPo setting) {
        return normalizeShotDuration(setting != null ? setting.getDefaultShotDuration() : null);
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

    private Map<String, String> baseVariables(AiVideoProjectPo project) {
        Map<String, String> variables = new HashMap<>();
        variables.put("projectName", safeValue(project.getProjectName()));
        variables.put("targetPlatform", safeValue(project.getTargetPlatform()));
        variables.put("ratio", safeValue(project.getDefaultRatio()));
        variables.put("style", safeValue(project.getDefaultStyle()));
        return variables;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : trimToNull(second);
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).collect(Collectors.joining(","));
    }

    private String joinFlexible(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            String joined = list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.joining(","));
            return StringUtils.hasText(joined) ? joined : null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private AiVideoPropMapper requirePropMapper() {
        if (propMapper == null) {
            throw new BusinessException("道具资产能力未初始化");
        }
        return propMapper;
    }

    private List<ShotContinuitySnapshot> toShotContinuitySnapshots(List<ShotPayload> shots) {
        List<ShotContinuitySnapshot> snapshots = new ArrayList<>();
        for (ShotPayload shot : safeList(shots)) {
            if (shot == null) {
                continue;
            }
            snapshots.add(new ShotContinuitySnapshot(shot.shotNo, shot.sceneName, shot.actionDesc, shot.promptText));
        }
        return snapshots;
    }

    static void validateShotSpatialContinuity(List<ShotContinuitySnapshot> shots) {
        if (shots == null || shots.size() < 2) {
            return;
        }
        for (int i = 1; i < shots.size(); i++) {
            ShotContinuitySnapshot previous = shots.get(i - 1);
            ShotContinuitySnapshot current = shots.get(i);
            String previousText = continuityText(previous);
            String currentText = continuityText(current);
            if (hasHighRiskSpatialTarget(previousText)
                    && hasUnintroducedShelterJump(currentText)
                    && !hasSpatialBridge(currentText)) {
                throw new BusinessException("分镜连续性失败：第" + shotNo(previous, i)
                        + "镜建立了广告牌/铁架/高处等危机目标，第" + shotNo(current, i + 1)
                        + "镜突然切到狗窝/室内/窝口等未铺垫地点。请改为延续上一镜的街道与危险目标，"
                        + "或在 actionDesc 开头补充明确过渡动作。");
            }
            if (hasAmbiguousOffscreenHandoff(previousText) && hasPropReceiveAction(currentText)) {
                throw new BusinessException("分镜道具交接失败：第" + shotNo(previous, i)
                        + "镜使用了“展示给画外/递给画外”等模糊对象，第" + shotNo(current, i + 1)
                        + "镜又出现接过道具。请写清 giver、receiver、prop、screenDirection、finalOwner，"
                        + "例如“狗小汪从画面左侧把收纳盒递给喵小萌”。");
            }
            if (hasAmbiguousOffscreenHandoff(currentText)) {
                throw new BusinessException("分镜道具交接失败：第" + shotNo(current, i + 1)
                        + "镜不能只写展示给画外、递给画外或从画外递来；请写清具体接收角色和画面方向。");
            }
            if (hasPropReceiveAction(currentText) && !hasExplicitHandoffSource(currentText)) {
                throw new BusinessException("分镜道具交接失败：第" + shotNo(current, i + 1)
                        + "镜出现接过/接住/收下道具，但没有写清从谁手中、从画面哪一侧递来。"
                        + "请补充交接来源、道具名称、入画方向和接过后的归属。");
            }
        }
    }

    private static String continuityText(ShotContinuitySnapshot shot) {
        if (shot == null) {
            return "";
        }
        return compactText(shot.sceneName(), shot.actionDesc(), shot.promptText());
    }

    private static String compactText(String... values) {
        StringBuilder builder = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private static boolean hasHighRiskSpatialTarget(String text) {
        return containsAny(text, "广告牌", "铁架", "屋顶", "楼顶", "高处", "雨棚", "水中", "落水",
                "火场", "车道", "桥边", "悬崖", "摇摇欲坠", "小身影", "被困");
    }

    private static boolean hasUnintroducedShelterJump(String text) {
        return containsAny(text, "狗窝", "窝口", "窝里", "窝的", "窝角", "室内", "屋内", "家里",
                "床下", "沙发下", "房间");
    }

    private static boolean hasSpatialBridge(String text) {
        return containsAny(text, "延续上一镜", "承接上一镜", "从上一镜", "镜头回到", "切回", "同一条街",
                "同一街道", "街边", "对面商铺", "广告牌", "铁架", "屋顶", "高处", "雨棚", "抬头望向");
    }

    private static boolean hasAmbiguousOffscreenHandoff(String text) {
        return containsAny(text, "展示给画外", "递给画外", "交给画外", "传给画外", "拿给画外",
                "从画外递来", "画外递来", "画外递出");
    }

    private static boolean hasPropReceiveAction(String text) {
        return containsAny(text, "接过", "接住", "收下", "拿过", "接到", "接起");
    }

    private static boolean hasExplicitHandoffSource(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return (text.contains("从") && containsAny(text, "手中", "手里", "手上"))
                || containsAny(text, "手从画面左侧", "手从画面右侧", "手从画面边缘",
                "手从左侧", "手从右侧")
                || containsAny(text, "递给", "交给", "传给", "拿给", "递向")
                || containsAny(text, "递来的", "递过来", "递入画面");
    }

    private static boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static int shotNo(ShotContinuitySnapshot shot, int fallback) {
        return shot != null && shot.shotNo() != null ? shot.shotNo() : fallback;
    }

    private void fillCreateAudit(AiVideoGenerationTaskPo task) {
        task.setCreateBy(resolveOperator());
        task.setCreateTime(now());
        task.setUpdateBy(resolveOperator());
        task.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoGenerationTaskPo task) {
        task.setUpdateBy(resolveOperator());
        task.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoContentVersionPo version) {
        version.setCreateBy(resolveOperator());
        version.setCreateTime(now());
        version.setUpdateBy(resolveOperator());
        version.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoContentVersionPo version) {
        version.setUpdateBy(resolveOperator());
        version.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoProjectPo project) {
        project.setUpdateBy(resolveOperator());
        project.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoSourceDocumentPo document) {
        document.setUpdateBy(resolveOperator());
        document.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoCharacterPo character) {
        character.setCreateBy(resolveOperator());
        character.setCreateTime(now());
        character.setUpdateBy(resolveOperator());
        character.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoCharacterPo character) {
        character.setUpdateBy(resolveOperator());
        character.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoScenePo scene) {
        scene.setCreateBy(resolveOperator());
        scene.setCreateTime(now());
        scene.setUpdateBy(resolveOperator());
        scene.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoScenePo scene) {
        scene.setUpdateBy(resolveOperator());
        scene.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoPropPo prop) {
        prop.setCreateBy(resolveOperator());
        prop.setCreateTime(now());
        prop.setUpdateBy(resolveOperator());
        prop.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoPropPo prop) {
        prop.setUpdateBy(resolveOperator());
        prop.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoShotPo shot) {
        shot.setCreateBy(resolveOperator());
        shot.setCreateTime(now());
        shot.setUpdateBy(resolveOperator());
        shot.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoShotPo shot) {
        shot.setUpdateBy(resolveOperator());
        shot.setUpdateTime(now());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AssetPayload {
        public List<CharacterPayload> characters;
        public List<ScenePayload> scenes;
        public List<PropPayload> props;
        public SoundDesignPayload soundDesign;
        public List<ShotPayload> shots;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CharacterPayload {
        public String characterName;
        public String gender;
        public String ageDesc;
        public String identityDesc;
        public List<String> personalityTags;
        public String storyRole;
        public String relationshipDesc;
        public String appearance;
        public String hairStyle;
        public String costume;
        public String colorStyle;
        public String negativeTraits;
        public String promptText;
        public String completeness;
        public List<String> missingFields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ScenePayload {
        public String sceneName;
        public String sceneType;
        public Integer episodeNo;
        public String timeDesc;
        public String weather;
        public String atmosphere;
        public String visualFeatures;
        public String colorTone;
        public String props;
        public String negativeElements;
        public String promptText;
        public String completeness;
        public List<String> missingFields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PropPayload {
        public String propName;
        public String propType;
        public String visualDesc;
        public String color;
        public String material;
        public String shape;
        public String ownerCharacterName;
        public Integer firstShotNo;
        public String lastHolder;
        public String continuityRules;
        public String promptText;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SoundDesignPayload {
        public List<VoiceProfilePayload> voiceProfiles;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class VoiceProfilePayload {
        public String characterName;
        public String voiceName;
        public String voiceStyle;
        public String speed;
        public String emotionRange;
        public String recommendedVoiceType;
        public String referenceAudioNeed;
        public String rules;
        public String sampleText;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ShotPayload {
        public Integer episodeNo;
        public Integer shotNo;
        public Integer durationSec;
        public Long sceneId;
        public String sceneName;
        public String characterIds;
        public List<String> characterNames;
        public String shotType;
        public String cameraPosition;
        public String cameraMovement;
        public String transitionBeforeType;
        public String transitionBeforeDesc;
        public String transitionEffect;
        public Integer stitchGroupNo;
        public String actionDesc;
        public String dialogue;
        public String voiceOver;
        public String emotion;
        public String bgmCue;
        public Object sfxCues;
        public String promptText;
    }

    record ShotContinuitySnapshot(Integer shotNo, String sceneName, String actionDesc, String promptText) {
    }
}
