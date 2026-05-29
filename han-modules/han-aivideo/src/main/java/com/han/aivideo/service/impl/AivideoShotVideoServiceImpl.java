package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import com.han.aivideo.enums.AivideoTaskStatus;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoSceneMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import com.han.aivideo.service.IAivideoShotVideoService;
import com.han.api.ai.AiServiceClient;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateResponse;
import com.han.api.ai.domain.AiVideoTaskQueryRequest;
import com.han.api.ai.domain.AiVideoTaskQueryResponse;
import com.han.api.file.FileServiceClient;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Single shot video candidate workflow implementation.
 */
@Service
@RequiredArgsConstructor
public class AivideoShotVideoServiceImpl extends AivideoServiceSupport implements IAivideoShotVideoService {

    private static final String ASSET_SHOT_VIDEO = "SHOT_VIDEO";
    private static final String BIZ_SHOT = "SHOT";
    private static final String TASK_SHOT_VIDEO = "SHOT_VIDEO";
    private static final String STATUS_READY = "READY";
    private static final int DEFAULT_VIDEO_CANDIDATE_COUNT = 1;
    private static final int MAX_VIDEO_BYTES = 300 * 1024 * 1024;
    private static final int POLL_INTERVAL_MILLIS = 5_000;
    private static final int MAX_POLL_TIMES = 3;
    private static final int MAX_TRANSIENT_QUERY_FAILURES = 8;
    private static final int PROVIDER_TASK_REUSE_HOURS = 48;
    private static final String SHOT_VIDEO_SYSTEM_PROMPT = """
            你是电影级短剧分镜视频导演。
            核心规则：
            1. 基于已确认场景图生成单个短剧镜头视频，不生成整剧，不跨镜头。
            2. 必须保持参考图的空间关系、时间、天气、色调和主体环境稳定。
            3. 根据分镜动作、镜头运动、情绪和旁白设计可拍摄的视频动态。
            4. 不要生成字幕、水印、logo、花字和无关文字。
            5. 输出必须适合后续短剧剪辑，节奏清晰，动作可见。
            """;

    private final AiVideoProjectMapper projectMapper;
    private final AiVideoProjectSettingMapper settingMapper;
    private final AiVideoSceneMapper sceneMapper;
    private final AiVideoShotMapper shotMapper;
    private final AiVideoCharacterMapper characterMapper;
    private final AiVideoGenerationTaskMapper taskMapper;
    private final AiVideoMediaAssetMapper mediaAssetMapper;
    private final AiServiceClient aiServiceClient;
    private final FileServiceClient fileServiceClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${han.aivideo.media.public-file-origin:}")
    private String publicFileOrigin;

    @Override
    public AivideoPromptPreviewVo previewShotVideoPrompt(AivideoShotVideoGenerateDto dto) {
        RequestContext context = buildContext(dto, false);
        AivideoPromptPreviewVo vo = new AivideoPromptPreviewVo();
        vo.setPromptTemplateId(context.promptTemplateId());
        vo.setSystemPrompt(SHOT_VIDEO_SYSTEM_PROMPT);
        vo.setUserPrompt(context.prompt());
        vo.setCustomPrompt(dto.getCustomPrompt());
        vo.setEffectivePrompt("系统提示词：\n" + SHOT_VIDEO_SYSTEM_PROMPT + "\n\n用户提示词：\n" + context.prompt());
        return vo;
    }

    @Override
    public SseEmitter generateShotVideosStream(AivideoShotVideoGenerateDto dto) {
        SseEmitter emitter = new SseEmitter(900_000L);
        RequestContext context;
        AiVideoGenerationTaskPo task;
        try {
            context = buildContext(dto, true);
            boolean recoverOnly = Boolean.TRUE.equals(dto.getRecoverOnly());
            task = recoverOnly ? findReusableProviderTask(context, true) : null;
            if (task != null) {
                markTaskRecovering(context, task);
            } else if (recoverOnly) {
                throw new BusinessException("暂无可续查的视频生成任务");
            } else if (hasRunningVideoTask(context.project().getProjectId(), context.shot().getShotId())) {
                throw new BusinessException("该分镜已有视频生成任务执行中，请稍后刷新候选视频");
            } else {
                task = createTask(context);
            }
        } catch (Exception exception) {
            completeWithError(emitter, exception.getMessage());
            return emitter;
        }
        RequestContext streamContext = context;
        AiVideoGenerationTaskPo streamTask = task;
        CompletableFuture.runAsync(() -> runVideoStream(streamContext, streamTask, emitter));
        return emitter;
    }

    @Override
    public List<AiVideoGenerationTaskPo> listShotVideoTasks(Long projectId, Long shotId) {
        if (projectId == null || shotId == null) {
            throw new BusinessException("项目ID和分镜ID不能为空");
        }
        AiVideoProjectPo project = requireProject(projectId);
        requireShot(project.getProjectId(), shotId);
        return taskMapper.selectList(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, project.getProjectId())
                .eq(AiVideoGenerationTaskPo::getTenantId, project.getTenantId())
                .eq(AiVideoGenerationTaskPo::getTaskType, TASK_SHOT_VIDEO)
                .eq(AiVideoGenerationTaskPo::getBizType, BIZ_SHOT)
                .eq(AiVideoGenerationTaskPo::getBizId, shotId)
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoGenerationTaskPo::getUpdateTime)
                .orderByDesc(AiVideoGenerationTaskPo::getTaskId)
                .last("limit 5"));
    }

    private void runVideoStream(RequestContext context, AiVideoGenerationTaskPo task, SseEmitter emitter) {
        try {
            sendSse(emitter, "meta", meta(
                    "event", "task",
                    "taskId", task.getTaskId(),
                    "modelId", context.modelId(),
                    "candidateCount", context.candidateCount()
            ));
            List<AivideoMediaAssetVo> assets = new ArrayList<>();
            boolean recoveringProviderTask = StringUtils.hasText(task.getProviderTaskId());
            int total = recoveringProviderTask ? 1 : context.candidateCount();
            for (int i = 1; i <= total; i++) {
                sendSse(emitter, "meta", meta("event", recoveringProviderTask ? "recovering" : "submitting",
                        "current", i, "total", total));
                AiVideoGenerateResponse submitted = recoveringProviderTask
                        ? toSubmittedResponse(context, task)
                        : invokeVideoGeneration(context);
                if (StringUtils.hasText(submitted.getProviderTaskId())) {
                    task.setProviderTaskId(submitted.getProviderTaskId());
                    markTaskProgress(task, 15);
                    sendSse(emitter, "meta", meta(
                            "event", recoveringProviderTask ? "provider_task_reused" : "provider_task",
                            "providerTaskId", submitted.getProviderTaskId()));
                }
                AivideoMediaAssetVo existingAsset = findExistingCandidateByProviderTaskId(context, submitted.getProviderTaskId());
                if (existingAsset != null) {
                    assets.add(existingAsset);
                    markTaskSuccess(task, context.modelId());
                    sendSse(emitter, "meta", meta("event", "candidate", "asset", existingAsset));
                    continue;
                }
                if (!recoveringProviderTask && StringUtils.hasText(submitted.getProviderTaskId())
                        && !StringUtils.hasText(submitted.getVideoUrl())) {
                    throw new ProviderTaskPendingException("视频任务已提交，稍后点击刷新候选续查结果");
                }
                AiVideoTaskQueryResponse completed = waitForCompletion(context, task, submitted, emitter);
                String videoUrl = firstText(completed != null ? completed.getVideoUrl() : null, submitted.getVideoUrl());
                if (!StringUtils.hasText(videoUrl)) {
                    throw new BusinessException("视频模型未返回可下载视频地址");
                }
                AivideoMediaAssetVo asset = saveCandidate(context, task, submitted, completed, videoUrl, nextCandidateNo(context));
                assets.add(asset);
                markTaskProgress(task, Math.min(95, 25 + (i * 65 / total)));
                sendSse(emitter, "meta", meta("event", "candidate", "asset", asset));
            }
            markTaskSuccess(task, context.modelId());
            sendSse(emitter, "meta", meta("event", "done", "taskId", task.getTaskId(), "assets", assets));
            completeWithDone(emitter);
        } catch (ProviderTaskPendingException exception) {
            markTaskPending(task, exception.getMessage());
            sendSse(emitter, "meta", meta(
                    "event", "pending",
                    "taskId", task.getTaskId(),
                    "providerTaskId", task.getProviderTaskId(),
                    "status", AivideoTaskStatus.RUNNING.name(),
                    "progress", task.getProgress(),
                    "message", exception.getMessage()
            ));
            completeWithDone(emitter);
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithError(emitter, exception.getMessage());
        }
    }

    private RequestContext buildContext(AivideoShotVideoGenerateDto dto, boolean requireVideoModel) {
        if (dto == null || dto.getProjectId() == null || dto.getShotId() == null) {
            throw new BusinessException("项目ID和分镜ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoShotPo shot = requireShot(project.getProjectId(), dto.getShotId());
        AiVideoScenePo scene = requireScene(project.getProjectId(), shot.getSceneId());
        if (scene.getLockedMediaId() == null) {
            throw new BusinessException("请先为该分镜所属场景生成并选择场景图");
        }
        AiVideoMediaAssetPo referenceMedia = requireReferenceSceneImage(project.getProjectId(), scene.getLockedMediaId());
        AiVideoProjectSettingPo projectSetting = selectProjectSetting(project.getProjectId());
        AiVideoProjectSettingPo globalSetting = selectGlobalSetting(project.getTenantId());
        Long modelId = firstLong(dto.getModelId(),
                projectSetting != null ? projectSetting.getVideoModelId() : null,
                globalSetting != null ? globalSetting.getVideoModelId() : null);
        if (requireVideoModel && modelId == null) {
            throw new BusinessException("视频模型未配置，请先在 AI 模型页新增 model_type=VIDEO 的火山模型，并在 AI短剧基础配置中绑定视频模型ID");
        }
        int candidateCount = normalizeCandidateCount(dto.getCandidateCount(), projectSetting, globalSetting);
        String ratio = firstText(dto.getRatio(),
                projectSetting != null ? projectSetting.getDefaultRatio() : null,
                globalSetting != null ? globalSetting.getDefaultRatio() : null,
                project.getDefaultRatio(), "9:16");
        String resolution = firstText(dto.getResolution(),
                projectSetting != null ? projectSetting.getDefaultResolution() : null,
                globalSetting != null ? globalSetting.getDefaultResolution() : null,
                "720p");
        int durationSec = firstInteger(dto.getDurationSec(),
                shot.getDurationSec(),
                projectSetting != null ? projectSetting.getDefaultShotDuration() : null,
                globalSetting != null ? globalSetting.getDefaultShotDuration() : null,
                project.getDefaultShotDuration(), 5);
        Long promptTemplateId = firstLong(
                projectSetting != null ? projectSetting.getVideoPromptTemplateId() : null,
                globalSetting != null ? globalSetting.getVideoPromptTemplateId() : null);
        String referenceImageUrl = buildProviderFileUrl(referenceMedia.getFileUrl());
        Map<String, String> variables = buildVariables(project, scene, shot, referenceImageUrl, ratio, resolution, durationSec);
        String fallbackPrompt = buildShotVideoPrompt(project, scene, shot, ratio, resolution, durationSec, referenceImageUrl);
        String prompt = renderPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        variables.put("candidateCount", String.valueOf(candidateCount));
        return new RequestContext(project, scene, shot, referenceMedia, modelId, promptTemplateId,
                candidateCount, ratio, resolution, durationSec, dto.getCustomPrompt(), prompt,
                referenceImageUrl, variables);
    }

    private AiVideoGenerateResponse invokeVideoGeneration(RequestContext context) {
        AiVideoGenerateRequest request = new AiVideoGenerateRequest();
        request.setTenantId(context.project().getTenantId());
        request.setModelId(context.modelId());
        request.setUserPrompt(context.prompt());
        request.setReferenceImageUrl(context.referenceImageUrl());
        request.setCandidateCount(1);
        request.setRatio(context.ratio());
        request.setResolution(context.resolution());
        request.setDurationSec(context.durationSec());
        request.setVariables(context.variables());
        R<AiVideoGenerateResponse> result = aiServiceClient.generateVideo(request);
        if (result == null || result.isFail() || result.getData() == null) {
            throw new BusinessException(result == null ? "AI服务无响应" : result.getMsg());
        }
        return result.getData();
    }

    private AiVideoTaskQueryResponse waitForCompletion(RequestContext context, AiVideoGenerationTaskPo task,
                                                       AiVideoGenerateResponse submitted, SseEmitter emitter) {
        if (StringUtils.hasText(submitted.getVideoUrl())) {
            return null;
        }
        String providerTaskId = submitted.getProviderTaskId();
        if (!StringUtils.hasText(providerTaskId)) {
            throw new BusinessException("视频模型未返回任务ID");
        }
        int transientFailures = 0;
        for (int i = 1; i <= MAX_POLL_TIMES; i++) {
            sleepBeforeNextPoll();
            AiVideoTaskQueryRequest query = new AiVideoTaskQueryRequest();
            query.setTenantId(context.project().getTenantId());
            query.setModelId(context.modelId());
            query.setProviderTaskId(providerTaskId);
            R<AiVideoTaskQueryResponse> result;
            try {
                result = aiServiceClient.queryVideoTask(query);
            } catch (Exception exception) {
                transientFailures++;
                sendProviderQueryRetry(emitter, providerTaskId, transientFailures, exception.getMessage());
                if (transientFailures > MAX_TRANSIENT_QUERY_FAILURES) {
                    throw new ProviderTaskPendingException("video task is still running; provider query is temporarily unavailable, refresh later");
                }
                continue;
            }
            if (result == null || result.isFail() || result.getData() == null) {
                transientFailures++;
                sendProviderQueryRetry(emitter, providerTaskId, transientFailures,
                        result == null ? "AI service no response" : result.getMsg());
                if (transientFailures > MAX_TRANSIENT_QUERY_FAILURES) {
                    throw new ProviderTaskPendingException("video task is still running; provider query is temporarily unavailable, refresh later");
                }
                continue;
            }
            transientFailures = 0;
            AiVideoTaskQueryResponse response = result.getData();
            int progress = normalizeProgress(response.getProgress(), i);
            markTaskProgress(task, progress);
            sendSse(emitter, "meta", meta(
                    "event", "polling",
                    "providerTaskId", providerTaskId,
                    "status", firstText(response.getTaskStatus(), "RUNNING"),
                    "progress", progress
            ));
            if (isSuccessStatus(response.getTaskStatus()) || StringUtils.hasText(response.getVideoUrl())) {
                return response;
            }
            if (isFailedStatus(response.getTaskStatus())) {
                throw new BusinessException("视频生成失败，供应商状态：" + response.getTaskStatus());
            }
        }
        throw new ProviderTaskPendingException("视频任务仍在生成中，请稍后刷新候选");
    }

    private AivideoMediaAssetVo saveCandidate(RequestContext context, AiVideoGenerationTaskPo task,
                                              AiVideoGenerateResponse submitted,
                                              AiVideoTaskQueryResponse completed,
                                              String videoUrl, int index) {
        VideoBytes videoBytes = downloadVideo(videoUrl);
        String extension = extensionFromMime(videoBytes.mimeType());
        String filename = "aivideo-shot-video-" + context.shot().getShotId() + "-" + task.getTaskId()
                + "-" + index + "." + extension;
        Resource resource = new NamedByteArrayResource(videoBytes.bytes(), filename);
        R<FileDTO> uploadResult = fileServiceClient.upload(resource);
        if (uploadResult == null || uploadResult.isFail()) {
            throw new BusinessException(uploadResult == null ? "文件服务无响应" : uploadResult.getMsg());
        }
        FileDTO file = uploadResult.getData();
        if (file == null || file.getId() == null || !StringUtils.hasText(file.getUrl())) {
            throw new BusinessException("文件服务上传成功但未返回 fileId/fileUrl");
        }

        Map<String, String> params = new LinkedHashMap<>(context.variables());
        params.put("providerTaskId", firstText(submitted.getProviderTaskId(), completed != null ? completed.getProviderTaskId() : ""));
        params.put("providerStatus", firstText(completed != null ? completed.getTaskStatus() : null, submitted.getTaskStatus()));
        params.put("providerVideoUrl", videoUrl);

        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        media.setProjectId(context.project().getProjectId());
        media.setTenantId(context.project().getTenantId());
        media.setAssetType(ASSET_SHOT_VIDEO);
        media.setBizType(BIZ_SHOT);
        media.setBizId(context.shot().getShotId());
        media.setFileId(file.getId());
        media.setFileUrl(toFilePublicPath(file.getUrl()));
        media.setPromptText(submitted.getPrompt());
        media.setModelId(submitted.getModelId());
        media.setTaskId(task.getTaskId());
        media.setParamsJson(XuJsonUtil.toJsonString(params));
        media.setCandidateNo(index);
        media.setSelected(NO);
        media.setAssetStatus(STATUS_READY);
        media.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(media);
        transactionTemplate.executeWithoutResult(status -> mediaAssetMapper.insert(media));
        return toVo(media);
    }

    private AiVideoGenerateResponse toSubmittedResponse(RequestContext context, AiVideoGenerationTaskPo task) {
        AiVideoGenerateResponse response = new AiVideoGenerateResponse();
        response.setModelId(firstLong(task.getModelId(), context.modelId()));
        response.setPrompt(firstText(task.getPromptText(), context.prompt()));
        response.setProviderTaskId(task.getProviderTaskId());
        response.setTaskStatus(task.getTaskStatus());
        response.setProgress(task.getProgress());
        return response;
    }

    private AiVideoGenerationTaskPo findReusableProviderTask(RequestContext context, boolean looseMatch) {
        List<AiVideoGenerationTaskPo> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, context.project().getProjectId())
                .eq(AiVideoGenerationTaskPo::getTenantId, context.project().getTenantId())
                .eq(AiVideoGenerationTaskPo::getTaskType, TASK_SHOT_VIDEO)
                .eq(AiVideoGenerationTaskPo::getBizType, BIZ_SHOT)
                .eq(AiVideoGenerationTaskPo::getBizId, context.shot().getShotId())
                .isNotNull(AiVideoGenerationTaskPo::getProviderTaskId)
                .in(AiVideoGenerationTaskPo::getTaskStatus,
                        AivideoTaskStatus.PENDING.name(),
                        AivideoTaskStatus.RUNNING.name(),
                        AivideoTaskStatus.FAILED.name(),
                        AivideoTaskStatus.SUCCESS.name())
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoGenerationTaskPo::getUpdateTime)
                .orderByDesc(AiVideoGenerationTaskPo::getTaskId)
                .last("limit 10"));
        return tasks.stream()
                .filter(task -> looseMatch ? isReusableProviderTaskForRefresh(task) : isReusableProviderTask(context, task))
                .findFirst()
                .orElse(null);
    }

    private boolean isReusableProviderTaskForRefresh(AiVideoGenerationTaskPo task) {
        if (task == null || !StringUtils.hasText(task.getProviderTaskId())) {
            return false;
        }
        if (AivideoTaskStatus.FAILED.name().equals(normalizeStatus(task.getTaskStatus()))
                && !isRecoverableFailedProviderTask(task)) {
            return false;
        }
        LocalDateTime taskTime = firstTime(task.getStartedTime(), task.getUpdateTime(), task.getCreateTime());
        return taskTime == null || !taskTime.isBefore(now().minusHours(PROVIDER_TASK_REUSE_HOURS));
    }

    private boolean isRecoverableFailedProviderTask(AiVideoGenerationTaskPo task) {
        String message = firstText(task.getErrorMessage());
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("sse")
                || normalized.contains("broken pipe")
                || normalized.contains("read timed out")
                || normalized.contains("timeout")
                || normalized.contains("temporarily unavailable")
                || normalized.contains("connection")
                || normalized.contains("network")
                || normalized.contains("ioexception")
                || normalized.contains("超时")
                || normalized.contains("网络")
                || normalized.contains("连接");
    }

    private boolean isReusableProviderTask(RequestContext context, AiVideoGenerationTaskPo task) {
        if (task == null || !StringUtils.hasText(task.getProviderTaskId())) {
            return false;
        }
        if (!Objects.equals(context.modelId(), task.getModelId())) {
            return false;
        }
        if (!Objects.equals(context.promptTemplateId(), task.getPromptTemplateId())) {
            return false;
        }
        if (!Objects.equals(firstText(task.getCustomPrompt()), firstText(context.customPrompt()))) {
            return false;
        }
        String paramsJson = firstText(task.getParamsJson());
        String referencePath = toFilePublicPath(context.referenceMedia().getFileUrl());
        if (!paramsJson.contains(context.referenceImageUrl()) && !paramsJson.contains(referencePath)) {
            return false;
        }
        if (!paramsJson.contains(context.ratio())
                || !paramsJson.contains(context.resolution())
                || !paramsJson.contains(String.valueOf(context.durationSec()))) {
            return false;
        }
        LocalDateTime taskTime = firstTime(task.getStartedTime(), task.getUpdateTime(), task.getCreateTime());
        return taskTime == null || !taskTime.isBefore(now().minusHours(PROVIDER_TASK_REUSE_HOURS));
    }

    private void markTaskRecovering(RequestContext context, AiVideoGenerationTaskPo task) {
        int progress = task.getProgress() == null ? 15 : Math.min(90, Math.max(15, task.getProgress()));
        task.setModelId(context.modelId());
        task.setPromptTemplateId(context.promptTemplateId());
        task.setPromptText(context.prompt());
        task.setCustomPrompt(context.customPrompt());
        task.setParamsJson(XuJsonUtil.toJsonString(context.variables()));
        task.setTaskStatus(AivideoTaskStatus.RUNNING.name());
        task.setProgress(progress);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setFinishedTime(null);
        if (task.getStartedTime() == null) {
            task.setStartedTime(now());
        }
        fillUpdateAudit(task);
        transactionTemplate.executeWithoutResult(status -> taskMapper.updateById(task));
    }

    private void markTaskPending(AiVideoGenerationTaskPo task, String message) {
        int progress = task.getProgress() == null ? 15 : Math.min(95, Math.max(15, task.getProgress()));
        task.setTaskStatus(AivideoTaskStatus.RUNNING.name());
        task.setProgress(progress);
        task.setErrorMessage(StringUtils.hasText(message) ? message : "video task is still running; refresh candidates later");
        task.setFinishedTime(null);
        if (task.getStartedTime() == null) {
            task.setStartedTime(now());
        }
        fillUpdateAudit(task);
        transactionTemplate.executeWithoutResult(status -> taskMapper.updateById(task));
    }

    private AivideoMediaAssetVo findExistingCandidateByProviderTaskId(RequestContext context, String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return null;
        }
        List<AiVideoMediaAssetPo> assets = mediaAssetMapper.selectList(new LambdaQueryWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, context.project().getProjectId())
                .eq(AiVideoMediaAssetPo::getAssetType, ASSET_SHOT_VIDEO)
                .eq(AiVideoMediaAssetPo::getBizType, BIZ_SHOT)
                .eq(AiVideoMediaAssetPo::getBizId, context.shot().getShotId())
                .eq(AiVideoMediaAssetPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoMediaAssetPo::getCreateTime)
                .orderByDesc(AiVideoMediaAssetPo::getMediaId));
        return assets.stream()
                .filter(asset -> firstText(asset.getParamsJson()).contains(providerTaskId))
                .findFirst()
                .map(this::toVo)
                .orElse(null);
    }

    private int nextCandidateNo(RequestContext context) {
        List<AiVideoMediaAssetPo> assets = mediaAssetMapper.selectList(new LambdaQueryWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, context.project().getProjectId())
                .eq(AiVideoMediaAssetPo::getAssetType, ASSET_SHOT_VIDEO)
                .eq(AiVideoMediaAssetPo::getBizType, BIZ_SHOT)
                .eq(AiVideoMediaAssetPo::getBizId, context.shot().getShotId())
                .eq(AiVideoMediaAssetPo::getDelFlag, DEL_FLAG_NORMAL));
        return assets.stream()
                .map(AiVideoMediaAssetPo::getCandidateNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void sendProviderQueryRetry(SseEmitter emitter, String providerTaskId, int failureCount, String message) {
        sendSse(emitter, "meta", meta(
                "event", "provider_query_retry",
                "providerTaskId", providerTaskId,
                "status", "RUNNING",
                "failureCount", failureCount,
                "message", firstText(message, "provider query is temporarily unavailable; retry later")
        ));
    }

    private boolean hasRunningVideoTask(Long projectId, Long shotId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, projectId)
                .eq(AiVideoGenerationTaskPo::getTaskType, TASK_SHOT_VIDEO)
                .eq(AiVideoGenerationTaskPo::getBizType, BIZ_SHOT)
                .eq(AiVideoGenerationTaskPo::getBizId, shotId)
                .in(AiVideoGenerationTaskPo::getTaskStatus, AivideoTaskStatus.PENDING.name(), AivideoTaskStatus.RUNNING.name())
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)) > 0;
    }

    private AiVideoGenerationTaskPo createTask(RequestContext context) {
        AiVideoGenerationTaskPo task = new AiVideoGenerationTaskPo();
        task.setProjectId(context.project().getProjectId());
        task.setTenantId(context.project().getTenantId());
        task.setTaskType(TASK_SHOT_VIDEO);
        task.setBizType(BIZ_SHOT);
        task.setBizId(context.shot().getShotId());
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
        task.setErrorMessage(message == null ? "视频生成失败" : message);
        task.setFinishedTime(now());
        fillUpdateAudit(task);
        transactionTemplate.executeWithoutResult(status -> taskMapper.updateById(task));
    }

    private Map<String, String> buildVariables(AiVideoProjectPo project, AiVideoScenePo scene, AiVideoShotPo shot,
                                               String referenceImageUrl, String ratio, String resolution, int durationSec) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("projectName", safeValue(project.getProjectName()));
        variables.put("targetPlatform", safeValue(project.getTargetPlatform()));
        variables.put("style", safeValue(project.getDefaultStyle()));
        variables.put("ratio", safeValue(ratio));
        variables.put("resolution", safeValue(resolution));
        variables.put("durationSec", String.valueOf(durationSec));
        variables.put("episodeNo", String.valueOf(firstInteger(shot.getEpisodeNo(), 1)));
        variables.put("shotNo", String.valueOf(firstInteger(shot.getShotNo(), 1)));
        variables.put("sceneName", safeValue(scene.getSceneName()));
        variables.put("sceneType", safeValue(scene.getSceneType()));
        variables.put("sceneTime", safeValue(scene.getTimeDesc()));
        variables.put("weather", safeValue(scene.getWeather()));
        variables.put("atmosphere", safeValue(scene.getAtmosphere()));
        variables.put("visualFeatures", safeValue(scene.getVisualFeatures()));
        variables.put("characterNames", safeValue(resolveCharacterNames(project.getProjectId(), shot.getCharacterIds())));
        variables.put("shotType", safeValue(shot.getShotType()));
        variables.put("cameraPosition", safeValue(shot.getCameraPosition()));
        variables.put("cameraMovement", safeValue(shot.getCameraMovement()));
        variables.put("actionDesc", safeValue(shot.getActionDesc()));
        variables.put("dialogue", safeValue(shot.getDialogue()));
        variables.put("voiceOver", safeValue(shot.getVoiceOver()));
        variables.put("emotion", safeValue(shot.getEmotion()));
        variables.put("shotPromptText", safeValue(shot.getPromptText()));
        variables.put("referenceImageUrl", referenceImageUrl);
        return variables;
    }

    private String buildShotVideoPrompt(AiVideoProjectPo project, AiVideoScenePo scene, AiVideoShotPo shot,
                                        String ratio, String resolution, int durationSec, String referenceImageUrl) {
        return """
                # AI短剧单分镜视频生成

                请基于已选择的场景参考图生成一个短剧单镜头视频。

                ## 输出规格
                - 画幅：%s
                - 清晰度：%s
                - 时长：%s 秒
                - 参考场景图：%s

                ## 分镜信息
                - 项目：%s
                - 风格：%s
                - 集数/镜头：第 %s 集 / 镜头 %s
                - 场景：%s
                - 时间/天气/氛围：%s / %s / %s
                - 视觉特征：%s
                - 出场角色：%s
                - 镜头类型：%s
                - 机位：%s
                - 镜头运动：%s
                - 动作描述：%s
                - 对白：%s
                - 旁白：%s
                - 情绪：%s
                - 原始分镜提示词：%s

                ## 强制规则
                1. 以参考场景图作为空间与光影基准，保持场景一致，不要跳到其他地点。
                2. 镜头只表现当前单个分镜，不扩展前后剧情，不生成多个镜头拼接。
                3. 动作节奏清晰，镜头运动稳定，适合短剧剪辑。
                4. 不要生成字幕、水印、logo、花字、海报字和无关屏幕文字。
                5. 角色、动作和情绪以分镜描述为准；缺失信息用克制、自然的影视表达补齐。
                """.formatted(
                safeValue(ratio), safeValue(resolution), durationSec, referenceImageUrl,
                safeValue(project.getProjectName()), safeValue(project.getDefaultStyle()),
                firstInteger(shot.getEpisodeNo(), 1), firstInteger(shot.getShotNo(), 1),
                safeValue(scene.getSceneName()), safeValue(scene.getTimeDesc()), safeValue(scene.getWeather()),
                safeValue(scene.getAtmosphere()), safeValue(scene.getVisualFeatures()),
                safeValue(resolveCharacterNames(project.getProjectId(), shot.getCharacterIds())),
                safeValue(shot.getShotType()), safeValue(shot.getCameraPosition()), safeValue(shot.getCameraMovement()),
                safeValue(shot.getActionDesc()), safeValue(shot.getDialogue()), safeValue(shot.getVoiceOver()),
                safeValue(shot.getEmotion()), safeValue(shot.getPromptText()));
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
            throw new BusinessException(result == null ? "AI服务无响应" : result.getMsg());
        }
        return result.getData();
    }

    private String resolveCharacterNames(Long projectId, String characterIds) {
        if (!StringUtils.hasText(characterIds)) {
            return "";
        }
        String raw = characterIds.trim();
        List<String> tokens = Arrays.stream(raw.replace("[", "").replace("]", "").replace("\"", "").split("[,，、]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (tokens.isEmpty()) {
            return raw;
        }
        List<String> names = new ArrayList<>();
        for (String token : tokens) {
            try {
                Long characterId = Long.parseLong(token);
                AiVideoCharacterPo character = characterMapper.selectById(characterId);
                if (character != null && Objects.equals(projectId, character.getProjectId())
                        && Integer.valueOf(DEL_FLAG_NORMAL).equals(character.getDelFlag())
                        && StringUtils.hasText(character.getCharacterName())) {
                    names.add(character.getCharacterName());
                }
            } catch (NumberFormatException ignored) {
                names.add(token);
            }
        }
        return String.join("、", names);
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

    private AiVideoShotPo requireShot(Long projectId, Long shotId) {
        AiVideoShotPo shot = shotMapper.selectById(shotId);
        if (shot == null || !Objects.equals(projectId, shot.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(shot.getDelFlag())) {
            throw new BusinessException("分镜资产不存在");
        }
        return shot;
    }

    private AiVideoScenePo requireScene(Long projectId, Long sceneId) {
        if (sceneId == null) {
            throw new BusinessException("分镜未绑定场景，请先补全分镜场景");
        }
        AiVideoScenePo scene = sceneMapper.selectById(sceneId);
        if (scene == null || !Objects.equals(projectId, scene.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(scene.getDelFlag())) {
            throw new BusinessException("分镜绑定的场景资产不存在");
        }
        return scene;
    }

    private AiVideoMediaAssetPo requireReferenceSceneImage(Long projectId, Long mediaId) {
        AiVideoMediaAssetPo media = mediaAssetMapper.selectById(mediaId);
        if (media == null || !Objects.equals(projectId, media.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(media.getDelFlag())
                || !"SCENE_IMAGE".equals(media.getAssetType())
                || !StringUtils.hasText(media.getFileUrl())) {
            throw new BusinessException("已选场景图不存在或未归档，请重新选择场景图");
        }
        return media;
    }

    private AiVideoProjectSettingPo selectProjectSetting(Long projectId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .eq(AiVideoProjectSettingPo::getProjectId, projectId)
                .last("limit 1"));
    }

    private AiVideoProjectSettingPo selectGlobalSetting(Long tenantId) {
        if (tenantId != null && tenantId > 0) {
            AiVideoProjectSettingPo tenantSetting = settingMapper.selectOne(new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                    .isNull(AiVideoProjectSettingPo::getProjectId)
                    .eq(AiVideoProjectSettingPo::getTenantId, tenantId)
                    .orderByDesc(AiVideoProjectSettingPo::getUpdateTime)
                    .orderByDesc(AiVideoProjectSettingPo::getSettingId)
                    .last("limit 1"));
            if (tenantSetting != null) {
                return tenantSetting;
            }
        }
        return settingMapper.selectOne(new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .isNull(AiVideoProjectSettingPo::getProjectId)
                .and(q -> q.eq(AiVideoProjectSettingPo::getTenantId, 0L)
                        .or().isNull(AiVideoProjectSettingPo::getTenantId))
                .orderByDesc(AiVideoProjectSettingPo::getUpdateTime)
                .orderByDesc(AiVideoProjectSettingPo::getSettingId)
                .last("limit 1"));
    }

    private String buildProviderFileUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException("参考图地址为空");
        }
        String trimmed = fileUrl.trim();
        if (trimmed.startsWith("http")) {
            return trimmed;
        }
        String publicPath = toFilePublicPath(trimmed);
        String origin = firstText(publicFileOrigin, "");
        if (!StringUtils.hasText(origin)) {
            throw new BusinessException("场景图外部访问地址未配置，请配置 han.aivideo.media.public-file-origin");
        }
        if (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return origin + publicPath;
    }

    private String toFilePublicPath(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException("媒体资源地址为空");
        }
        String value = fileUrl.trim();
        if (value.startsWith("/file/public/")) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            if (StringUtils.hasText(path) && path.startsWith("/file/public/")) {
                return path;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to the business error below.
        }
        throw new BusinessException("媒体资源地址不是受控文件路径");
    }

    private VideoBytes downloadVideo(String videoUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(videoUrl).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(300_000);
            connection.setRequestProperty("User-Agent", "Han-AIVideo/1.0");
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException("下载视频失败(" + statusCode + ")");
            }
            byte[] bytes = readLimited(connection.getInputStream(), MAX_VIDEO_BYTES);
            String mimeType = firstText(connection.getContentType(), "video/mp4");
            return new VideoBytes(bytes, mimeType);
        } catch (IOException exception) {
            throw new BusinessException("下载视频失败: " + exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] readLimited(InputStream inputStream, int maxBytes) throws IOException {
        try (InputStream stream = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new BusinessException("视频文件超过 300MB，无法归档");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private boolean isSuccessStatus(String status) {
        String normalized = normalizeStatus(status);
        return normalized.contains("SUCCESS") || normalized.contains("SUCCEEDED")
                || normalized.contains("COMPLETED") || normalized.contains("DONE")
                || normalized.contains("FINISHED");
    }

    private boolean isFailedStatus(String status) {
        String normalized = normalizeStatus(status);
        return normalized.contains("FAIL") || normalized.contains("ERROR")
                || normalized.contains("CANCEL");
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().replace('-', '_').toUpperCase();
    }

    private int normalizeProgress(Integer providerProgress, int pollIndex) {
        if (providerProgress != null && providerProgress >= 0) {
            return Math.min(95, Math.max(15, providerProgress));
        }
        return Math.min(90, 15 + pollIndex);
    }

    private int normalizeCandidateCount(Integer requested, AiVideoProjectSettingPo projectSetting,
                                        AiVideoProjectSettingPo globalSetting) {
        int value = requested != null && requested > 0
                ? requested
                : firstInteger(
                projectSetting != null ? projectSetting.getVideoCandidateCount() : null,
                globalSetting != null ? globalSetting.getVideoCandidateCount() : null,
                DEFAULT_VIDEO_CANDIDATE_COUNT);
        return Math.max(1, Math.min(3, value));
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

    private void sleepBeforeNextPoll() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("视频生成轮询被中断");
        }
    }

    private boolean sendSse(SseEmitter emitter, String type, Object content) {
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

    private void completeWithError(SseEmitter emitter, String message) {
        try {
            sendSse(emitter, "error", StringUtils.hasText(message) ? message : "视频生成失败");
        } finally {
            emitter.complete();
        }
    }

    private void completeWithDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException exception) {
            // Client-side disconnects must not mark the provider task as failed.
        } catch (IllegalStateException exception) {
            // Client-side disconnects must not mark the provider task as failed.
        } finally {
            emitter.complete();
        }
    }

    private Map<String, Object> meta(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return map;
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

    private String extensionFromMime(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType.toLowerCase();
        if (normalized.contains("quicktime") || normalized.contains("mov")) {
            return "mov";
        }
        if (normalized.contains("webm")) {
            return "webm";
        }
        return "mp4";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Long firstLong(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int firstInteger(Integer... values) {
        if (values == null) {
            return DEFAULT_VIDEO_CANDIDATE_COUNT;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return DEFAULT_VIDEO_CANDIDATE_COUNT;
    }

    private LocalDateTime firstTime(LocalDateTime... values) {
        if (values == null) {
            return null;
        }
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未填写";
    }

    private record RequestContext(
            AiVideoProjectPo project,
            AiVideoScenePo scene,
            AiVideoShotPo shot,
            AiVideoMediaAssetPo referenceMedia,
            Long modelId,
            Long promptTemplateId,
            int candidateCount,
            String ratio,
            String resolution,
            int durationSec,
            String customPrompt,
            String prompt,
            String referenceImageUrl,
            Map<String, String> variables
    ) {
    }

    private record VideoBytes(byte[] bytes, String mimeType) {
    }

    private static final class ProviderTaskPendingException extends RuntimeException {

        private ProviderTaskPendingException(String message) {
            super(message);
        }
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
