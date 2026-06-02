package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.han.aivideo.domain.dto.AivideoAssetConfirmDto;
import com.han.aivideo.domain.dto.AivideoAssetExtractDto;
import com.han.aivideo.domain.dto.AivideoContentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentConfirmDto;
import com.han.aivideo.domain.dto.AivideoTextGenerateDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String TARGET_DOCUMENT = "DOCUMENT";
    private static final String TARGET_CONTENT = "CONTENT_VERSION";
    private static final String TARGET_CHARACTER = "CHARACTER";
    private static final String TARGET_SCENE = "SCENE";
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
            AiTextGenerateResponse response = invokeTextGeneration(project, setting,
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
        vo.setShots(selectShots(projectId));
        return vo;
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
        AiTextGenerateRequest request = new AiTextGenerateRequest();
        request.setTenantId(project.getTenantId());
        request.setModelId(setting != null ? setting.getTextModelId() : null);
        request.setPromptTemplateId(promptTemplateId);
        request.setUserPrompt(userPrompt);
        request.setCustomPrompt(customPrompt);
        request.setVariables(variables);
        request.setSystemPrompt(TEXT_SYSTEM_PROMPT);
        R<AiTextGenerateResponse> result = aiServiceClient.generateText(request);
        if (result == null || result.isFail()) {
            throw new BusinessException(result == null ? "AI 文本生成服务无响应" : result.getMsg());
        }
        if (result.getData() == null || !StringUtils.hasText(result.getData().getContent())) {
            throw new BusinessException("AI 文本生成结果为空");
        }
        return result.getData();
    }

    private void runPolishStream(AiVideoProjectPo project, AiVideoSourceDocumentPo document,
                                 AiVideoProjectSettingPo setting, Long promptTemplateId, String customPrompt,
                                 String userPrompt, Map<String, String> variables, AiVideoGenerationTaskPo task,
                                 String operator, SseEmitter emitter) {
        try {
            AiTextGenerateRequest request = buildTextGenerateRequest(project, setting, promptTemplateId,
                    customPrompt, userPrompt, variables);
            AivideoAiStreamClient.StreamResult result = aiStreamClient.streamText(request,
                    chunk -> sendSse(emitter, "delta", chunk));
            if (!StringUtils.hasText(result.content())) {
                throw new BusinessException("AI 文本生成结果为空");
            }
            AiVideoContentVersionPo version = buildContentVersion(project, document.getDocumentId(), CONTENT_POLISH,
                    "润色稿 v" + nextVersionNo(project.getProjectId(), CONTENT_POLISH),
                    result.content(), null, promptTemplateId, customPrompt, resolveLong(result.meta().get("modelId")), task.getTaskId());
            version.setCreateBy(operator);
            version.setUpdateBy(operator);
            contentVersionMapper.insert(version);
            markTaskSuccess(task, version.getModelId(), null);
            Map<String, Object> meta = new LinkedHashMap<>(result.meta());
            meta.put("versionId", version.getVersionId());
            meta.put("taskId", task.getTaskId());
            sendSse(emitter, "meta", meta);
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithError(emitter, exception.getMessage());
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
                    chunk -> sendSse(emitter, "delta", chunk));
            if (!StringUtils.hasText(result.content())) {
                throw new BusinessException("AI 文本生成结果为空");
            }
            AiVideoContentVersionPo version = buildContentVersion(project, null, CONTENT_SCRIPT,
                    "短剧剧本 v" + nextVersionNo(project.getProjectId(), CONTENT_SCRIPT),
                    result.content(), null, promptTemplateId, customPrompt, resolveLong(result.meta().get("modelId")), task.getTaskId());
            version.setCreateBy(operator);
            version.setUpdateBy(operator);
            contentVersionMapper.insert(version);
            markTaskSuccess(task, version.getModelId(), null);
            Map<String, Object> meta = new LinkedHashMap<>(result.meta());
            meta.put("versionId", version.getVersionId());
            meta.put("taskId", task.getTaskId());
            sendSse(emitter, "meta", meta);
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithError(emitter, exception.getMessage());
        }
    }

    private void runAssetStream(AiVideoProjectPo project, AiVideoProjectSettingPo setting,
                                Long promptTemplateId, String customPrompt, String userPrompt,
                                Map<String, String> variables, AiVideoGenerationTaskPo task,
                                String operator, SseEmitter emitter) {
        try {
            AiTextGenerateRequest request = buildTextGenerateRequest(project, setting, null,
                    null, userPrompt, variables);
            AivideoAiStreamClient.StreamResult result = aiStreamClient.streamText(request,
                    chunk -> sendSse(emitter, "delta", chunk));
            if (!StringUtils.hasText(result.content())) {
                throw new BusinessException("AI 文本生成结果为空");
            }
            AssetPayload payload = parseAssetPayload(result.content());
            AiVideoContentVersionPo assetVersion = transactionTemplate.execute(status -> {
                softDeletePendingAssets(project.getProjectId());
                insertAssets(project, payload, setting);

                AiVideoContentVersionPo version = buildContentVersion(project, null, CONTENT_ASSET_EXTRACT,
                        "结构化资产", result.content(), extractJsonBlock(result.content()),
                        promptTemplateId, customPrompt, resolveLong(result.meta().get("modelId")), task.getTaskId());
                version.setCreateBy(operator);
                version.setUpdateBy(operator);
                contentVersionMapper.insert(version);
                markTaskSuccess(task, version.getModelId(), null);
                return version;
            });
            if (assetVersion == null) {
                throw new BusinessException("资产提取结果保存失败");
            }
            Map<String, Object> meta = new LinkedHashMap<>(result.meta());
            meta.put("versionId", assetVersion.getVersionId());
            meta.put("taskId", task.getTaskId());
            meta.put("assetCounts", buildAssetCounts(project.getProjectId()));
            sendSse(emitter, "meta", meta);
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithError(emitter, exception.getMessage());
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

    private void sendSse(SseEmitter emitter, String type, Object content) {
        try {
            emitter.send(SseEmitter.event().data(XuJsonUtil.toJsonString(Map.of(
                    "type", type,
                    "content", content == null ? "" : content
            ))));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE send failed", exception);
        }
    }

    private void completeWithError(SseEmitter emitter, String message) {
        try {
            sendSse(emitter, "error", StringUtils.hasText(message) ? message : "AI 文本生成失败");
        } finally {
            emitter.complete();
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
        Map<String, Long> characterIdMap = new LinkedHashMap<>();
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

        int duration = normalizeShotDuration(setting != null ? setting.getDefaultShotDuration() : null);
        index = 1;
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
            shot.setActionDesc(item.actionDesc);
            shot.setDialogue(item.dialogue);
            shot.setVoiceOver(item.voiceOver);
            shot.setEmotion(item.emotion);
            shot.setPromptText(item.promptText);
            shot.setConfirmStatus(CONFIRM_PENDING);
            shot.setGenerationStatus(CONFIRM_PENDING);
            shot.setSortOrder(index++);
            shot.setDelFlag(DEL_FLAG_NORMAL);
            fillCreateAudit(shot);
            shotMapper.insert(shot);
        }
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
        if (StringUtils.hasText(item.characterIds)) {
            return item.characterIds.trim();
        }
        if (item.characterNames == null || item.characterNames.isEmpty()) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String name : item.characterNames) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Long id = characterIdMap.get(name.trim());
            ids.add(id == null ? name.trim() : String.valueOf(id));
        }
        return String.join(",", ids);
    }

    private AssetPayload parseAssetPayload(String content) {
        String json = extractJsonBlock(content);
        try {
            AssetPayload payload = XuJsonUtil.parseObject(json, AssetPayload.class);
            if (payload == null || (safeList(payload.characters).isEmpty()
                    && safeList(payload.scenes).isEmpty()
                    && safeList(payload.shots).isEmpty())) {
                throw new BusinessException("资产提取结果为空");
            }
            return payload;
        } catch (RuntimeException exception) {
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
        for (String key : List.of("\"characters\"", "\"scenes\"", "\"shots\"")) {
            int index = text.indexOf(key);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private Map<String, Object> buildAssetCounts(Long projectId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("characters", characterMapper.selectCount(baseCharacterWrapper(projectId)));
        counts.put("scenes", sceneMapper.selectCount(baseSceneWrapper(projectId)));
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
                + "每个镜头的主动作、是否包含强动作、建议时长 5/6/8 秒；超过 3 个动作 beat 必须建议拆镜，不要硬塞进一个镜头。\n\n项目：" + project.getProjectName()
                + "\n目标平台：" + safeValue(project.getTargetPlatform()) + "\n画幅：" + safeValue(project.getDefaultRatio())
                + "\n\n润色文本：\n" + polishedText;
    }

    private String buildAssetPrompt(AiVideoProjectPo project, AiVideoProjectSettingPo setting, String scriptText) {
        int duration = defaultShotDuration(setting);
        return "请严格依据下面三组参考提示词规则，从短剧剧本中提取【角色、场景、分镜】。"
                + "必须只输出 JSON 对象，不要输出解释、Markdown 围栏或额外说明。JSON key 必须保持英文，所有字段值必须使用中文。\n\n"
                + "【角色构建规则】\n"
                + "1. 你是电影级角色概念设计师，需要先解析角色画像：代号、年龄/生命阶段、性别或物种、社会身份或物种身份、人格标签、故事功能。\n"
                + "2. 每个角色必须输出鲜明、可区分的视觉方案；如果是人类，写清年龄、自然发色、具体发型、眼神神态、服装材质、主色辅色、鞋履配饰。\n"
                + "3. 如果角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须在 identityDesc、appearance、promptText 中保留物种本体，写清品种/体型/毛色/眼睛/标志性特征，禁止改成人类演员。\n"
                + "4. 多角色必须在色彩、轮廓、材质或身体特征上显著区别，严禁视觉雷同。\n"
                + "5. promptText 要可直接用于角色图生成，包含单一角色、纯白极简背景、头部/面部特写、全身正侧背三视图、固定自然站姿或动物自然姿态等关键信息。\n\n"
                + "【电影级纯净场景规则】\n"
                + "1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。\n"
                + "2. 场景名称必须四个字以上，不能只写单一名词，要通过修饰词增加辨识度。\n"
                + "3. 场景必须覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。\n"
                + "4. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty、landscape only。\n\n"
                + "【剧本分镜规则】\n"
                + "1. 你是顶级影视剧导演与分镜规划专家，需要面向 Seedance 2.0 / 即梦 2.0 的视频生成逻辑拆解镜头。\n"
                + "2. 全局禁止出现其他人；画面必须通过单人特写、主观视角或环境遮挡，把视觉重心锁定在当前核心主角。\n"
                + "3. 严格区分 dialogue 和 voiceOver：角色直接说的话写入 dialogue；旁白、心理活动、环境氛围写入 voiceOver。\n"
                + "4. 每个分镜必须明确地点；延续场景时在 sceneName 或 actionDesc 中体现“延续上个分镜场景，机位微调”。\n"
                + "5. 动作要衔接，不能瞬移；镜头需包含微动作、眼神、呼吸、肢体、环境变化等可拍内容。\n"
                + "6. shotType、cameraPosition、cameraMovement 要优先使用专业运镜词，如极焦特写、近景推轨、环绕摇镜、慢动作/延时、手持震动。\n"
                + "7. durationSec 只能在 5、6、8 中动态选择，不再固定使用项目默认秒数；项目默认镜头秒数仅作为初始参考：" + duration + "。\n"
                + "8. 动作预算：5 秒=1 个主动作 + 1 个反应/表情 + 1 个结尾状态；6 秒=2 个连续动作 + 结尾状态；8 秒=3 个连续动作 + 明确结尾状态。\n"
                + "9. 超过 3 个动作 beat 必须自动拆成多个 shots，不允许硬塞；强动作如倒地起身、悬浮、变身、俯冲、落水、打斗、救援、掰弯铁栏等额外占预算，优先单独作为一个镜头核心。\n"
                + "10. actionDesc 必须写成视频模型能执行的动作节拍，包含起始状态、主动作、反应/表情和结尾状态；promptText 必须补充构图、目标部位可见和部位发光限制。\n"
                + "11. 出现爪子、手、脚、翅膀、尾巴等部位时，必须要求半身/全身构图并露出目标部位；出现发光时必须写清具体发光部位，禁止用眼睛发光替代爪子/手/脚等目标部位发光。\n\n"
                + "【输出 JSON 结构】\n"
                + "{\"characters\":[{\"characterName\":\"\",\"gender\":\"\",\"ageDesc\":\"\",\"identityDesc\":\"\",\"personalityTags\":[\"\"],"
                + "\"storyRole\":\"\",\"relationshipDesc\":\"\",\"appearance\":\"\",\"hairStyle\":\"\",\"costume\":\"\",\"colorStyle\":\"\","
                + "\"negativeTraits\":\"\",\"promptText\":\"\",\"completeness\":\"\",\"missingFields\":[\"\"]}],"
                + "\"scenes\":[{\"sceneName\":\"\",\"sceneType\":\"\",\"episodeNo\":1,\"timeDesc\":\"\",\"weather\":\"\",\"atmosphere\":\"\","
                + "\"visualFeatures\":\"\",\"colorTone\":\"\",\"props\":\"\",\"negativeElements\":\"\",\"promptText\":\"\",\"completeness\":\"\","
                + "\"missingFields\":[\"\"]}],"
                + "\"shots\":[{\"episodeNo\":1,\"shotNo\":1,\"durationSec\":5,\"sceneName\":\"\",\"characterNames\":[\"\"],"
                + "\"shotType\":\"\",\"cameraPosition\":\"\",\"cameraMovement\":\"\",\"actionDesc\":\"\",\"dialogue\":\"\",\"voiceOver\":\"\","
                + "\"emotion\":\"\",\"promptText\":\"\"}]}\n\n"
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

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
        public String actionDesc;
        public String dialogue;
        public String voiceOver;
        public String emotion;
        public String promptText;
    }
}
