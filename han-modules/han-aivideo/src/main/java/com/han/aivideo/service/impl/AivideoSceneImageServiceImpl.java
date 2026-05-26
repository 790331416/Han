package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.aivideo.domain.dto.AivideoMediaSelectDto;
import com.han.aivideo.domain.dto.AivideoSceneImageGenerateDto;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoReviewRecordPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import com.han.aivideo.enums.AivideoTaskStatus;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoReviewRecordMapper;
import com.han.aivideo.mapper.AiVideoSceneMapper;
import com.han.aivideo.service.IAivideoSceneImageService;
import com.han.api.ai.AiServiceClient;
import com.han.api.ai.domain.AiImageCandidate;
import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.file.FileServiceClient;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Scene image candidate workflow implementation.
 */
@Service
@RequiredArgsConstructor
public class AivideoSceneImageServiceImpl extends AivideoServiceSupport implements IAivideoSceneImageService {

    private static final String ASSET_SCENE_IMAGE = "SCENE_IMAGE";
    private static final String BIZ_SCENE = "SCENE";
    private static final String TASK_SCENE_IMAGE = "SCENE_IMAGE";
    private static final String TARGET_MEDIA = "MEDIA_ASSET";
    private static final String ACTION_SELECT = "SELECT";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_SELECTED = "SELECTED";
    private static final int DEFAULT_CANDIDATE_COUNT = 2;
    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final String IMAGE_SYSTEM_PROMPT = "你是电影级纯净场景设计专家。只生成纯场景、无人、无人物、无人物剪影的图片提示词。";

    private final AiVideoProjectMapper projectMapper;
    private final AiVideoProjectSettingMapper settingMapper;
    private final AiVideoSceneMapper sceneMapper;
    private final AiVideoGenerationTaskMapper taskMapper;
    private final AiVideoMediaAssetMapper mediaAssetMapper;
    private final AiVideoReviewRecordMapper reviewRecordMapper;
    private final AiServiceClient aiServiceClient;
    private final FileServiceClient fileServiceClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AivideoPromptPreviewVo previewSceneImagePrompt(AivideoSceneImageGenerateDto dto) {
        RequestContext context = buildContext(dto, false);
        AivideoPromptPreviewVo vo = new AivideoPromptPreviewVo();
        vo.setPromptTemplateId(context.promptTemplateId());
        vo.setSystemPrompt(IMAGE_SYSTEM_PROMPT);
        vo.setUserPrompt(context.prompt());
        vo.setCustomPrompt(dto.getCustomPrompt());
        vo.setEffectivePrompt("系统提示词：\n" + IMAGE_SYSTEM_PROMPT + "\n\n用户提示词：\n" + context.prompt());
        return vo;
    }

    @Override
    public SseEmitter generateSceneImagesStream(AivideoSceneImageGenerateDto dto) {
        SseEmitter emitter = new SseEmitter(300_000L);
        RequestContext context;
        AiVideoGenerationTaskPo task;
        try {
            context = buildContext(dto, true);
            if (hasRunningSceneImageTask(context.project().getProjectId(), context.scene().getSceneId())) {
                throw new BusinessException("该场景已有图片生成任务执行中，请稍后刷新候选图");
            }
            task = createTask(context);
        } catch (Exception exception) {
            completeWithError(emitter, exception.getMessage());
            return emitter;
        }
        CompletableFuture.runAsync(() -> runSceneImageStream(context, task, emitter));
        return emitter;
    }

    @Override
    public List<AivideoMediaAssetVo> listMedia(Long projectId, String assetType, String bizType, Long bizId) {
        requireProject(projectId);
        LambdaQueryWrapper<AiVideoMediaAssetPo> wrapper = new LambdaQueryWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, projectId)
                .eq(AiVideoMediaAssetPo::getDelFlag, DEL_FLAG_NORMAL)
                .eq(StringUtils.hasText(assetType), AiVideoMediaAssetPo::getAssetType, assetType)
                .eq(StringUtils.hasText(bizType), AiVideoMediaAssetPo::getBizType, bizType)
                .eq(bizId != null, AiVideoMediaAssetPo::getBizId, bizId)
                .orderByDesc(AiVideoMediaAssetPo::getSelected)
                .orderByDesc(AiVideoMediaAssetPo::getCreateTime)
                .orderByAsc(AiVideoMediaAssetPo::getCandidateNo);
        return mediaAssetMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void selectMedia(AivideoMediaSelectDto dto) {
        if (dto == null || dto.getProjectId() == null || dto.getMediaId() == null) {
            throw new BusinessException("项目ID和媒体ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoMediaAssetPo media = mediaAssetMapper.selectById(dto.getMediaId());
        if (media == null || !Objects.equals(project.getProjectId(), media.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(media.getDelFlag())) {
            throw new BusinessException("媒体资产不存在");
        }
        if (!ASSET_SCENE_IMAGE.equals(media.getAssetType()) || !BIZ_SCENE.equals(media.getBizType())) {
            throw new BusinessException("当前媒体资产不是场景候选图");
        }

        mediaAssetMapper.update(null, new LambdaUpdateWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, project.getProjectId())
                .eq(AiVideoMediaAssetPo::getAssetType, media.getAssetType())
                .eq(AiVideoMediaAssetPo::getBizType, media.getBizType())
                .eq(AiVideoMediaAssetPo::getBizId, media.getBizId())
                .set(AiVideoMediaAssetPo::getSelected, NO)
                .set(AiVideoMediaAssetPo::getAssetStatus, STATUS_READY)
                .set(AiVideoMediaAssetPo::getUpdateBy, resolveOperator())
                .set(AiVideoMediaAssetPo::getUpdateTime, now()));

        media.setSelected(YES);
        media.setAssetStatus(STATUS_SELECTED);
        fillUpdateAudit(media);
        mediaAssetMapper.updateById(media);

        AiVideoScenePo scene = requireScene(project.getProjectId(), media.getBizId());
        Long before = scene.getLockedMediaId();
        scene.setLockedMediaId(media.getMediaId());
        fillUpdateAudit(scene);
        sceneMapper.updateById(scene);

        insertReview(project, TARGET_MEDIA, media.getMediaId(), ACTION_SELECT,
                before == null ? "" : String.valueOf(before), String.valueOf(media.getMediaId()), dto.getComment(), null);
    }

    private void runSceneImageStream(RequestContext context, AiVideoGenerationTaskPo task, SseEmitter emitter) {
        try {
            sendSse(emitter, "meta", Map.of(
                    "event", "task",
                    "taskId", task.getTaskId(),
                    "modelId", context.modelId(),
                    "candidateCount", context.candidateCount()
            ));
            AiImageGenerateResponse response = invokeImageGeneration(context);
            List<AivideoMediaAssetVo> assets = new ArrayList<>();
            int total = response.getCandidates() == null ? 0 : response.getCandidates().size();
            if (total == 0) {
                throw new BusinessException("图片模型未返回候选图");
            }
            int index = 0;
            for (AiImageCandidate candidate : response.getCandidates()) {
                index++;
                AivideoMediaAssetVo asset = saveCandidate(context, task, response, candidate, index);
                assets.add(asset);
                markTaskProgress(task, Math.min(95, 20 + (index * 70 / total)));
                sendSse(emitter, "meta", Map.of("event", "candidate", "asset", asset));
            }
            markTaskSuccess(task, response.getModelId());
            sendSse(emitter, "meta", Map.of("event", "done", "taskId", task.getTaskId(), "assets", assets));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithError(emitter, exception.getMessage());
        }
    }

    private RequestContext buildContext(AivideoSceneImageGenerateDto dto, boolean requireImageModel) {
        if (dto == null || dto.getProjectId() == null || dto.getSceneId() == null) {
            throw new BusinessException("项目ID和场景ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoScenePo scene = requireScene(project.getProjectId(), dto.getSceneId());
        AiVideoProjectSettingPo setting = selectSetting(project.getProjectId());
        Long modelId = dto.getModelId() != null ? dto.getModelId() : (setting != null ? setting.getImageModelId() : null);
        if (requireImageModel && modelId == null) {
            throw new BusinessException("图片模型未配置，请先在 AI 模型页新增 model_type=IMAGE 的火山模型，并在 AI短剧基础配置中绑定图片模型ID");
        }
        int candidateCount = normalizeCandidateCount(dto.getCandidateCount(), setting);
        String ratio = firstText(dto.getRatio(), setting != null ? setting.getDefaultRatio() : null, project.getDefaultRatio(), "9:16");
        String resolution = firstText(dto.getResolution(), setting != null ? setting.getDefaultResolution() : null, "720p");
        Long promptTemplateId = setting != null ? setting.getSceneImagePromptTemplateId() : null;
        Map<String, String> variables = buildSceneVariables(project, scene, ratio, resolution);
        String fallbackPrompt = buildSceneImagePrompt(project, scene, ratio, resolution);
        String prompt = renderPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        variables.put("candidateCount", String.valueOf(candidateCount));
        variables.put("size", firstText(dto.getSize(), ""));
        return new RequestContext(project, scene, setting, modelId, promptTemplateId, candidateCount,
                ratio, resolution, dto.getSize(), dto.getCustomPrompt(), prompt, variables);
    }

    private AiImageGenerateResponse invokeImageGeneration(RequestContext context) {
        AiImageGenerateRequest request = new AiImageGenerateRequest();
        request.setTenantId(context.project().getTenantId());
        request.setModelId(context.modelId());
        request.setUserPrompt(context.prompt());
        request.setCandidateCount(context.candidateCount());
        request.setRatio(context.ratio());
        request.setResolution(context.resolution());
        request.setSize(context.size());
        R<AiImageGenerateResponse> result = aiServiceClient.generateImage(request);
        if (result == null || result.isFail()) {
            throw new BusinessException(result == null ? "AI 图片服务无响应" : result.getMsg());
        }
        return result.getData();
    }

    private AivideoMediaAssetVo saveCandidate(RequestContext context, AiVideoGenerationTaskPo task,
                                              AiImageGenerateResponse response, AiImageCandidate candidate, int index) {
        ImageBytes imageBytes = loadImageBytes(candidate);
        String extension = extensionFromMime(imageBytes.mimeType());
        String filename = "aivideo-scene-" + context.scene().getSceneId() + "-" + task.getTaskId()
                + "-" + index + "." + extension;
        Resource resource = new NamedByteArrayResource(imageBytes.bytes(), filename);
        R<FileDTO> uploadResult = fileServiceClient.upload(resource);
        if (uploadResult == null || uploadResult.isFail()) {
            throw new BusinessException(uploadResult == null ? "文件服务无响应" : uploadResult.getMsg());
        }
        FileDTO file = uploadResult.getData();
        if (file == null || file.getId() == null || !StringUtils.hasText(file.getUrl())) {
            throw new BusinessException("文件服务上传成功但未返回 fileId/fileUrl");
        }

        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        media.setProjectId(context.project().getProjectId());
        media.setTenantId(context.project().getTenantId());
        media.setAssetType(ASSET_SCENE_IMAGE);
        media.setBizType(BIZ_SCENE);
        media.setBizId(context.scene().getSceneId());
        media.setFileId(file.getId());
        media.setFileUrl(file.getUrl());
        media.setPromptText(response.getPrompt());
        media.setNegativePrompt("人物, 人影, human, person, face, body, crowd, extra people");
        media.setModelId(response.getModelId());
        media.setTaskId(task.getTaskId());
        media.setParamsJson(XuJsonUtil.toJsonString(context.variables()));
        media.setCandidateNo(index);
        media.setSelected(NO);
        media.setAssetStatus(STATUS_READY);
        media.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(media);
        transactionTemplate.executeWithoutResult(status -> mediaAssetMapper.insert(media));
        return toVo(media);
    }

    private ImageBytes loadImageBytes(AiImageCandidate candidate) {
        if (candidate == null) {
            throw new BusinessException("图片候选为空");
        }
        if (StringUtils.hasText(candidate.getBase64Data())) {
            String payload = candidate.getBase64Data().trim();
            String mimeType = firstText(candidate.getMimeType(), "image/png");
            int commaIndex = payload.indexOf(',');
            if (payload.startsWith("data:") && commaIndex > 0) {
                String header = payload.substring(5, commaIndex);
                int semicolonIndex = header.indexOf(';');
                if (semicolonIndex > 0) {
                    mimeType = header.substring(0, semicolonIndex);
                }
                payload = payload.substring(commaIndex + 1);
            }
            return new ImageBytes(Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8)), mimeType);
        }
        if (StringUtils.hasText(candidate.getUrl())) {
            return downloadImage(candidate.getUrl());
        }
        throw new BusinessException("图片候选未包含 URL 或 Base64 数据");
    }

    private ImageBytes downloadImage(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "Han-AIVideo/1.0");
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException("下载图片失败(" + statusCode + ")");
            }
            byte[] bytes = connection.getInputStream().readNBytes(MAX_IMAGE_BYTES + 1);
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new BusinessException("图片文件超过 20MB，无法归档");
            }
            String mimeType = firstText(connection.getContentType(), "image/png");
            return new ImageBytes(bytes, mimeType);
        } catch (IOException exception) {
            throw new BusinessException("下载图片失败: " + exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String renderPrompt(AiVideoProjectPo project, Long promptTemplateId, String customPrompt,
                                String userPrompt, Map<String, String> variables) {
        AiTextGenerateRequest request = new AiTextGenerateRequest();
        request.setTenantId(project.getTenantId());
        request.setPromptTemplateId(promptTemplateId);
        request.setUserPrompt(userPrompt);
        request.setCustomPrompt(customPrompt);
        request.setVariables(variables);
        R<String> result = aiServiceClient.renderTextPrompt(request);
        if (result == null || result.isFail()) {
            throw new BusinessException(result == null ? "AI Prompt 渲染服务无响应" : result.getMsg());
        }
        if (!StringUtils.hasText(result.getData())) {
            throw new BusinessException("AI Prompt 渲染结果为空");
        }
        return result.getData();
    }

    private Map<String, String> buildSceneVariables(AiVideoProjectPo project, AiVideoScenePo scene,
                                                    String ratio, String resolution) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("projectName", safeValue(project.getProjectName()));
        variables.put("targetPlatform", safeValue(project.getTargetPlatform()));
        variables.put("style", safeValue(project.getDefaultStyle()));
        variables.put("ratio", safeValue(ratio));
        variables.put("resolution", safeValue(resolution));
        variables.put("sceneName", safeValue(scene.getSceneName()));
        variables.put("sceneType", safeValue(scene.getSceneType()));
        variables.put("timeDesc", safeValue(scene.getTimeDesc()));
        variables.put("weather", safeValue(scene.getWeather()));
        variables.put("atmosphere", safeValue(scene.getAtmosphere()));
        variables.put("visualFeatures", safeValue(scene.getVisualFeatures()));
        variables.put("colorTone", safeValue(scene.getColorTone()));
        variables.put("props", safeValue(scene.getProps()));
        variables.put("negativeElements", safeValue(scene.getNegativeElements()));
        variables.put("scenePromptText", safeValue(scene.getPromptText()));
        return variables;
    }

    private String buildSceneImagePrompt(AiVideoProjectPo project, AiVideoScenePo scene, String ratio, String resolution) {
        return """
                请基于以下场景信息生成一张短剧可用的电影级纯净场景图。
                强制要求：
                1. 纯场景、无人、无人物、无人物剪影、无脸、无身体部位，不出现任何角色。
                2. 画面必须可作为后续分镜视频背景，构图清晰，主体环境明确，避免过度抽象。
                3. 保留场景气氛、时间、天气、色调、道具和视觉特征。
                4. 画幅：%s；清晰度目标：%s。
                5. 提示词中必须包含 no humans, empty scene, landscape only。

                项目：%s
                风格：%s
                场景名称：%s
                场景类型：%s
                时间：%s
                天气：%s
                氛围：%s
                视觉特征：%s
                色调：%s
                道具：%s
                原始场景提示词：%s
                """.formatted(
                safeValue(ratio), safeValue(resolution), safeValue(project.getProjectName()),
                safeValue(project.getDefaultStyle()), safeValue(scene.getSceneName()), safeValue(scene.getSceneType()),
                safeValue(scene.getTimeDesc()), safeValue(scene.getWeather()), safeValue(scene.getAtmosphere()),
                safeValue(scene.getVisualFeatures()), safeValue(scene.getColorTone()), safeValue(scene.getProps()),
                safeValue(scene.getPromptText()));
    }

    private int normalizeCandidateCount(Integer requested, AiVideoProjectSettingPo setting) {
        int value = requested != null && requested > 0
                ? requested
                : (setting != null && setting.getImageCandidateCount() != null
                ? setting.getImageCandidateCount()
                : DEFAULT_CANDIDATE_COUNT);
        return Math.max(1, Math.min(4, value));
    }

    private boolean hasRunningSceneImageTask(Long projectId, Long sceneId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, projectId)
                .eq(AiVideoGenerationTaskPo::getTaskType, TASK_SCENE_IMAGE)
                .eq(AiVideoGenerationTaskPo::getBizType, BIZ_SCENE)
                .eq(AiVideoGenerationTaskPo::getBizId, sceneId)
                .in(AiVideoGenerationTaskPo::getTaskStatus, AivideoTaskStatus.PENDING.name(), AivideoTaskStatus.RUNNING.name())
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)) > 0;
    }

    private AiVideoGenerationTaskPo createTask(RequestContext context) {
        AiVideoGenerationTaskPo task = new AiVideoGenerationTaskPo();
        task.setProjectId(context.project().getProjectId());
        task.setTenantId(context.project().getTenantId());
        task.setTaskType(TASK_SCENE_IMAGE);
        task.setBizType(BIZ_SCENE);
        task.setBizId(context.scene().getSceneId());
        task.setModelId(context.modelId());
        task.setPromptTemplateId(context.promptTemplateId());
        task.setPromptText(context.prompt());
        task.setCustomPrompt(context.customPrompt());
        task.setParamsJson(XuJsonUtil.toJsonString(context.variables()));
        task.setTaskStatus(AivideoTaskStatus.RUNNING.name());
        task.setProgress(5);
        task.setStartedTime(now());
        task.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(task);
        taskMapper.insert(task);
        return task;
    }

    private void markTaskProgress(AiVideoGenerationTaskPo task, int progress) {
        task.setProgress(progress);
        fillUpdateAudit(task);
        transactionTemplate.executeWithoutResult(status -> taskMapper.updateById(task));
    }

    private void markTaskSuccess(AiVideoGenerationTaskPo task, Long modelId) {
        task.setModelId(modelId != null ? modelId : task.getModelId());
        task.setTaskStatus(AivideoTaskStatus.SUCCESS.name());
        task.setProgress(100);
        task.setFinishedTime(now());
        fillUpdateAudit(task);
        transactionTemplate.executeWithoutResult(status -> taskMapper.updateById(task));
    }

    private void markTaskFailed(AiVideoGenerationTaskPo task, String message) {
        task.setTaskStatus(AivideoTaskStatus.FAILED.name());
        task.setProgress(100);
        task.setErrorMessage(message == null ? "场景图生成失败" : message);
        task.setFinishedTime(now());
        fillUpdateAudit(task);
        transactionTemplate.executeWithoutResult(status -> taskMapper.updateById(task));
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

    private AiVideoScenePo requireScene(Long projectId, Long sceneId) {
        AiVideoScenePo scene = sceneMapper.selectById(sceneId);
        if (scene == null || !Objects.equals(projectId, scene.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(scene.getDelFlag())) {
            throw new BusinessException("场景资产不存在");
        }
        return scene;
    }

    private AiVideoProjectSettingPo selectSetting(Long projectId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .eq(AiVideoProjectSettingPo::getProjectId, projectId)
                .last("limit 1"));
    }

    private AivideoMediaAssetVo toVo(AiVideoMediaAssetPo media) {
        AivideoMediaAssetVo vo = new AivideoMediaAssetVo();
        vo.setMediaId(media.getMediaId());
        vo.setProjectId(media.getProjectId());
        vo.setAssetType(media.getAssetType());
        vo.setBizType(media.getBizType());
        vo.setBizId(media.getBizId());
        vo.setFileId(media.getFileId());
        vo.setFileUrl(media.getFileUrl());
        vo.setThumbnailFileId(media.getThumbnailFileId());
        vo.setPromptText(media.getPromptText());
        vo.setNegativePrompt(media.getNegativePrompt());
        vo.setModelId(media.getModelId());
        vo.setTaskId(media.getTaskId());
        vo.setParamsJson(media.getParamsJson());
        vo.setCandidateNo(media.getCandidateNo());
        vo.setSelected(media.getSelected());
        vo.setAssetStatus(media.getAssetStatus());
        vo.setCreateTime(media.getCreateTime());
        return vo;
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
            sendSse(emitter, "error", StringUtils.hasText(message) ? message : "场景图生成失败");
        } finally {
            emitter.complete();
        }
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

    private void fillCreateAudit(AiVideoMediaAssetPo media) {
        media.setCreateBy(resolveOperator());
        media.setCreateTime(now());
        media.setUpdateBy(resolveOperator());
        media.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoMediaAssetPo media) {
        media.setUpdateBy(resolveOperator());
        media.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoScenePo scene) {
        scene.setUpdateBy(resolveOperator());
        scene.setUpdateTime(now());
    }

    private String extensionFromMime(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType.toLowerCase();
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return "jpg";
        }
        if (normalized.contains("webp")) {
            return "webp";
        }
        return "png";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未填写";
    }

    private record RequestContext(
            AiVideoProjectPo project,
            AiVideoScenePo scene,
            AiVideoProjectSettingPo setting,
            Long modelId,
            Long promptTemplateId,
            int candidateCount,
            String ratio,
            String resolution,
            String size,
            String customPrompt,
            String prompt,
            Map<String, String> variables
    ) {
    }

    private record ImageBytes(byte[] bytes, String mimeType) {
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
