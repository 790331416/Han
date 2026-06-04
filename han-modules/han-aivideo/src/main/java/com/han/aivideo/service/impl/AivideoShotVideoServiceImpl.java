package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.Locale;
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
    private static final int MAX_VIDEO_REFERENCE_IMAGES = 6;
    private static final int POLL_INTERVAL_MILLIS = 5_000;
    private static final int MAX_POLL_TIMES = 60;
    private static final int MAX_TRANSIENT_QUERY_FAILURES = 8;
    private static final int PROVIDER_TASK_REUSE_HOURS = 48;
    private static final int AUTO_RECOVERY_IDLE_SECONDS = 60;
    private static final int AUTO_RECOVERY_BATCH_SIZE = 5;
    private static final int PROVIDER_TASK_TIMEOUT_MINUTES = 30;
    private static final String SHOT_VIDEO_SYSTEM_PROMPT = """
            你是电影级短剧分镜视频导演。
            核心规则：
            1. 基于已确认场景图生成单个短剧镜头视频，不生成整剧，不跨镜头。
            2. 必须严格执行镜头连续性协议：上一镜头结束姿态就是本镜头起始姿态，不允许跳切、瞬移、突然换姿态。
            3. 必须保持参考图的空间关系、时间、天气、色调和主体环境稳定；若参考图为上一镜头尾帧，优先继承尾帧中的主体位置和姿态。
            4. 严格执行音画双轨协议：视频阶段只负责画面，不新增、不改写、不替换配音、旁白声线、BGM 或音效；对白才允许口型同步，旁白和心理活动必须作为画外音处理，角色不张嘴。
            5. 同一角色、动物或宠物必须保持同一身份与外观锚点，禁止跨镜头换物种、换毛色、换体型、换脸型、换年龄感或丢失项圈/斑纹等标志物。
            6. 若角色参考图是纯白/浅灰棚拍的单主体锚定图，只提取角色身份与外观，不继承白底棚拍背景，不复制同款分身，不把单主体误识别成多个主体。
            7. 同一场景必须保持背景空间、光线、天气、色调、道具和前中后景关系稳定；除非分镜明确切场，不得无故换地点或换背景。
            8. 根据分镜动作、镜头运动、情绪和旁白设计可拍摄的视频动态，动作必须低幅度、渐进、可剪辑。
            9. 遇到“悬浮、飞起、变身、倒地、站起”等强动作词，除非分镜明确写高速飞行，否则默认只做缓慢、低幅度、原地附近变化。
            10. 不要生成字幕、水印、logo、花字和无关文字。
            11. 输出必须适合后续短剧剪辑，节奏清晰，动作可见。
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
                    sendSse(emitter, "meta", meta("event", "candidate", "taskId", task.getTaskId(), "asset", existingAsset));
                    continue;
                }
                AiVideoTaskQueryResponse completed = waitForCompletion(context, task, submitted, emitter);
                String videoUrl = firstText(completed != null ? completed.getVideoUrl() : null, submitted.getVideoUrl());
                if (!StringUtils.hasText(videoUrl)) {
                    throw new BusinessException("视频模型未返回可下载视频地址");
                }
                AivideoMediaAssetVo asset = saveCandidate(context, task, submitted, completed, videoUrl, nextCandidateNo(context));
                assets.add(asset);
                markTaskProgress(task, Math.min(95, 25 + (i * 65 / total)));
                sendSse(emitter, "meta", meta("event", "candidate", "taskId", task.getTaskId(), "asset", asset));
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
            if (shouldKeepProviderTaskPending(task, exception)) {
                String message = buildProviderPendingMessage(exception);
                markTaskPending(task, message);
                sendSse(emitter, "meta", meta(
                        "event", "pending",
                        "taskId", task.getTaskId(),
                        "providerTaskId", task.getProviderTaskId(),
                        "status", AivideoTaskStatus.RUNNING.name(),
                        "progress", task.getProgress(),
                        "message", message
                ));
                completeWithDone(emitter);
                return;
            }
            markTaskFailed(task, exception.getMessage());
            completeWithError(emitter, exception.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${han.aivideo.shot-video.recovery.fixed-delay:60000}",
            initialDelayString = "${han.aivideo.shot-video.recovery.initial-delay:30000}")
    public void recoverStaleShotVideoTasks() {
        List<AiVideoGenerationTaskPo> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getTaskType, TASK_SHOT_VIDEO)
                .eq(AiVideoGenerationTaskPo::getBizType, BIZ_SHOT)
                .in(AiVideoGenerationTaskPo::getTaskStatus, AivideoTaskStatus.PENDING.name(), AivideoTaskStatus.RUNNING.name())
                .isNotNull(AiVideoGenerationTaskPo::getProviderTaskId)
                .ne(AiVideoGenerationTaskPo::getProviderTaskId, "")
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)
                .le(AiVideoGenerationTaskPo::getUpdateTime, now().minusSeconds(AUTO_RECOVERY_IDLE_SECONDS))
                .orderByAsc(AiVideoGenerationTaskPo::getUpdateTime)
                .last("limit " + AUTO_RECOVERY_BATCH_SIZE));
        for (AiVideoGenerationTaskPo task : tasks) {
            recoverStaleShotVideoTask(task);
        }
    }

    private void recoverStaleShotVideoTask(AiVideoGenerationTaskPo task) {
        if (task == null || !StringUtils.hasText(task.getProviderTaskId())) {
            return;
        }
        if (isProviderTaskTimedOut(task)) {
            markTaskFailed(task, "视频任务超过 " + PROVIDER_TASK_TIMEOUT_MINUTES + " 分钟仍未完成，已自动停止续查；请重新生成或检查火山任务日志");
            return;
        }
        try {
            RequestContext context = buildContext(toRecoveryDto(task), true);
            if (!isReusableProviderTask(context, task)) {
                return;
            }
            markTaskRecovering(context, task);
            AivideoMediaAssetVo existingAsset = findExistingCandidateByProviderTaskId(context, task.getProviderTaskId());
            if (existingAsset != null) {
                markTaskSuccess(task, context.modelId());
                return;
            }
            AiVideoGenerateResponse submitted = toSubmittedResponse(context, task);
            AiVideoTaskQueryResponse response = queryProviderTask(context, task.getProviderTaskId());
            markTaskProgress(task, normalizeProgress(response.getProgress(), 1));
            if (isSuccessStatus(response.getTaskStatus()) || StringUtils.hasText(response.getVideoUrl())) {
                String videoUrl = firstText(response.getVideoUrl(), submitted.getVideoUrl());
                if (!StringUtils.hasText(videoUrl)) {
                    markTaskPending(task, "视频任务已完成，但供应商暂未返回可下载视频地址，系统会继续自动续查");
                    return;
                }
                AivideoMediaAssetVo duplicate = findExistingCandidateByProviderTaskId(context, task.getProviderTaskId());
                if (duplicate == null) {
                    saveCandidate(context, task, submitted, response, videoUrl, nextCandidateNo(context));
                }
                markTaskSuccess(task, context.modelId());
                return;
            }
            if (isFailedStatus(response.getTaskStatus())) {
                markTaskFailed(task, "视频生成失败，供应商状态：" + response.getTaskStatus());
                return;
            }
            markTaskPending(task, "视频任务仍在生成中，系统正在自动续查");
        } catch (Exception exception) {
            if (shouldKeepProviderTaskPending(task, exception)) {
                markTaskPending(task, buildProviderPendingMessage(exception));
            }
        }
    }

    private AivideoShotVideoGenerateDto toRecoveryDto(AiVideoGenerationTaskPo task) {
        AivideoShotVideoGenerateDto dto = new AivideoShotVideoGenerateDto();
        dto.setProjectId(task.getProjectId());
        dto.setShotId(task.getBizId());
        dto.setCandidateCount(1);
        dto.setModelId(task.getModelId());
        dto.setCustomPrompt(task.getCustomPrompt());
        Map<String, Object> params = parseTaskParams(task.getParamsJson());
        dto.setRatio(paramText(params, "ratio"));
        dto.setResolution(paramText(params, "resolution"));
        dto.setDurationSec(paramInteger(params, "durationSec"));
        dto.setDefaultStyle(paramText(params, "style"));
        dto.setGenerationStrategy(paramText(params, PARAM_GENERATION_STRATEGY));
        dto.setAudioMode(paramText(params, PARAM_AUDIO_MODE));
        dto.setSubtitleMode(paramText(params, PARAM_SUBTITLE_MODE));
        dto.setReferenceStrategy(paramText(params, PARAM_REFERENCE_STRATEGY));
        dto.setActionIntensity(paramText(params, PARAM_ACTION_INTENSITY));
        dto.setContinuityLevel(paramText(params, PARAM_CONTINUITY_LEVEL));
        dto.setMultiRoleStrategy(paramText(params, PARAM_MULTI_ROLE_STRATEGY));
        dto.setCharacterDesignType(paramText(params, PARAM_CHARACTER_DESIGN_TYPE));
        dto.setRecoverOnly(true);
        return dto;
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
        AiVideoMediaAssetPo sceneReferenceMedia = requireReferenceSceneImage(project.getProjectId(), scene.getLockedMediaId());
        AiVideoShotPo previousShot = findPreviousShot(project, shot);
        if (requireVideoModel) {
            validatePreviousShotReady(previousShot);
        }
        AiVideoMediaAssetPo previousTailFrameMedia = findTailFrameMedia(project.getProjectId(), previousShot);
        List<AiVideoMediaAssetPo> referenceMedias = buildShotVideoReferenceMedias(project.getProjectId(), shot,
                sceneReferenceMedia, previousTailFrameMedia, dto.getReferenceMediaIds());
        AiVideoMediaAssetPo referenceMedia = referenceMedias.get(0);
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
        int durationSec = normalizeAivideoShotDuration(firstInteger(dto.getDurationSec(),
                shot.getDurationSec(),
                projectSetting != null ? projectSetting.getDefaultShotDuration() : null,
                globalSetting != null ? globalSetting.getDefaultShotDuration() : null,
                project.getDefaultShotDuration(), 5));
        Long promptTemplateId = firstLong(
                projectSetting != null ? projectSetting.getVideoPromptTemplateId() : null,
                globalSetting != null ? globalSetting.getVideoPromptTemplateId() : null);
        StrategyContext strategy = resolveStrategy(dto, projectSetting, globalSetting);
        List<String> referenceImageUrls = buildProviderFileUrls(referenceMedias);
        String referenceImageUrl = referenceImageUrls.get(0);
        Map<String, String> variables = buildVariables(project, scene, shot, previousShot, previousTailFrameMedia,
                referenceMedias, referenceImageUrls, ratio, resolution, durationSec, strategy);
        String fallbackPrompt = buildShotVideoPrompt(project, scene, shot, previousShot, previousTailFrameMedia,
                ratio, resolution, durationSec, referenceMedias, referenceImageUrls, strategy);
        String prompt = renderPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        variables.put("candidateCount", String.valueOf(candidateCount));
        return new RequestContext(project, scene, shot, referenceMedia, modelId, promptTemplateId,
                candidateCount, ratio, resolution, durationSec, dto.getCustomPrompt(), prompt,
                referenceImageUrl, referenceImageUrls, referenceMedias, variables, strategy);
    }

    private AiVideoGenerateResponse invokeVideoGeneration(RequestContext context) {
        AiVideoGenerateRequest request = new AiVideoGenerateRequest();
        request.setTenantId(context.project().getTenantId());
        request.setModelId(context.modelId());
        request.setUserPrompt(context.prompt());
        request.setReferenceImageUrl(context.referenceImageUrl());
        request.setReferenceImageUrls(context.referenceImageUrls());
        request.setCandidateCount(1);
        request.setRatio(context.ratio());
        request.setResolution(context.resolution());
        request.setDurationSec(context.durationSec());
        request.setVariables(context.variables());
        request.setReturnLastFrame(true);
        request.setGenerateAudio(shouldGenerateAudio(context.strategy().audioMode()));
        R<AiVideoGenerateResponse> result = aiServiceClient.generateVideo(request);
        if (result == null || result.isFail() || result.getData() == null) {
            throw new BusinessException(result == null ? "AI服务无响应" : result.getMsg());
        }
        return result.getData();
    }

    private AiVideoShotPo findPreviousShot(AiVideoProjectPo project, AiVideoShotPo shot) {
        if (project == null || shot == null) {
            return null;
        }
        LambdaQueryWrapper<AiVideoShotPo> wrapper = new LambdaQueryWrapper<AiVideoShotPo>()
                .eq(AiVideoShotPo::getProjectId, project.getProjectId())
                .eq(AiVideoShotPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoShotPo::getShotNo)
                .orderByDesc(AiVideoShotPo::getSortOrder)
                .orderByDesc(AiVideoShotPo::getShotId)
                .last("limit 1");
        Integer episodeNo = shot.getEpisodeNo();
        if (episodeNo != null) {
            wrapper.eq(AiVideoShotPo::getEpisodeNo, episodeNo);
        }
        if (shot.getShotNo() != null) {
            wrapper.lt(AiVideoShotPo::getShotNo, shot.getShotNo());
        } else if (shot.getSortOrder() != null) {
            wrapper.lt(AiVideoShotPo::getSortOrder, shot.getSortOrder());
        } else {
            wrapper.lt(AiVideoShotPo::getShotId, shot.getShotId());
        }
        return shotMapper.selectOne(wrapper);
    }

    private void validatePreviousShotReady(AiVideoShotPo previousShot) {
        if (previousShot == null) {
            return;
        }
        if (previousShot.getVideoMediaId() == null) {
            throw new BusinessException("请先为上一分镜选择并确认视频，系统需要上一分镜结果作为衔接参考");
        }
    }

    private AiVideoMediaAssetPo findTailFrameMedia(Long projectId, AiVideoShotPo previousShot) {
        if (previousShot == null || previousShot.getTailFrameMediaId() == null) {
            return null;
        }
        AiVideoMediaAssetPo media = mediaAssetMapper.selectById(previousShot.getTailFrameMediaId());
        if (media == null || !Objects.equals(projectId, media.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(media.getDelFlag())
                || !"SHOT_TAIL_FRAME".equals(media.getAssetType())
                || !StringUtils.hasText(media.getFileUrl())) {
            return null;
        }
        return media;
    }

    private List<AiVideoMediaAssetPo> buildShotVideoReferenceMedias(Long projectId, AiVideoShotPo shot,
                                                                    AiVideoMediaAssetPo sceneReferenceMedia,
                                                                    AiVideoMediaAssetPo previousTailFrameMedia,
                                                                    List<Long> explicitReferenceMediaIds) {
        Map<Long, AiVideoMediaAssetPo> references = new LinkedHashMap<>();
        addReferenceMedia(references, previousTailFrameMedia);
        addReferenceMedia(references, sceneReferenceMedia);
        if (explicitReferenceMediaIds != null) {
            for (Long mediaId : explicitReferenceMediaIds) {
                addReferenceMedia(references, requireReferenceImage(projectId, mediaId));
            }
        }
        addCharacterReferenceMedias(projectId, shot, references);
        if (references.isEmpty()) {
            throw new BusinessException("视频生成参考图不能为空，请先选择场景图或角色图");
        }
        return new ArrayList<>(references.values());
    }

    private void addCharacterReferenceMedias(Long projectId, AiVideoShotPo shot, Map<Long, AiVideoMediaAssetPo> references) {
        for (String token : parseCharacterTokens(shot.getCharacterIds())) {
            try {
                Long characterId = Long.parseLong(token);
                AiVideoCharacterPo character = characterMapper.selectById(characterId);
                if (character == null || !Objects.equals(projectId, character.getProjectId())
                        || !Integer.valueOf(DEL_FLAG_NORMAL).equals(character.getDelFlag())
                        || character.getLockedMediaId() == null) {
                    continue;
                }
                addReferenceMedia(references, requireReferenceImage(projectId, character.getLockedMediaId()));
            } catch (NumberFormatException ignored) {
                // 老数据可能存角色名，无法反查锁定图时仅保留文字锚点。
            }
        }
    }

    private AiVideoMediaAssetPo requireReferenceImage(Long projectId, Long mediaId) {
        if (mediaId == null) {
            throw new BusinessException("参考图ID不能为空");
        }
        AiVideoMediaAssetPo media = mediaAssetMapper.selectById(mediaId);
        if (media == null || !Objects.equals(projectId, media.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(media.getDelFlag())
                || !isVideoReferenceAsset(media)
                || !StringUtils.hasText(media.getFileUrl())) {
            throw new BusinessException("参考图不存在、未归档或类型不支持，请重新选择场景图/角色图");
        }
        return media;
    }

    private boolean isVideoReferenceAsset(AiVideoMediaAssetPo media) {
        String assetType = firstText(media.getAssetType());
        if ("SHOT_TAIL_FRAME".equals(assetType)) {
            return true;
        }
        boolean supportedType = "SCENE_IMAGE".equals(assetType) || "CHARACTER_IMAGE".equals(assetType);
        if (!supportedType) {
            return false;
        }
        return YES.equals(media.getSelected()) || "SELECTED".equalsIgnoreCase(firstText(media.getAssetStatus()));
    }

    private void addReferenceMedia(Map<Long, AiVideoMediaAssetPo> references, AiVideoMediaAssetPo media) {
        if (media == null || media.getMediaId() == null || references.containsKey(media.getMediaId())) {
            return;
        }
        if (references.size() >= MAX_VIDEO_REFERENCE_IMAGES) {
            return;
        }
        references.put(media.getMediaId(), media);
    }

    private List<String> buildProviderFileUrls(List<AiVideoMediaAssetPo> referenceMedias) {
        List<String> urls = new ArrayList<>();
        for (AiVideoMediaAssetPo media : referenceMedias) {
            String url = buildProviderFileUrl(media.getFileUrl());
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
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
                    "taskId", task.getTaskId(),
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

    private AiVideoTaskQueryResponse queryProviderTask(RequestContext context, String providerTaskId) {
        AiVideoTaskQueryRequest query = new AiVideoTaskQueryRequest();
        query.setTenantId(context.project().getTenantId());
        query.setModelId(context.modelId());
        query.setProviderTaskId(providerTaskId);
        R<AiVideoTaskQueryResponse> result = aiServiceClient.queryVideoTask(query);
        if (result == null || result.isFail() || result.getData() == null) {
            throw new BusinessException(result == null ? "AI service no response" : result.getMsg());
        }
        return result.getData();
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
        params.put("providerLastFrameUrl", firstText(completed != null ? completed.getLastFrameUrl() : null,
                submitted.getLastFrameUrl()));

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
        if (isProviderTaskTimedOut(task)) {
            markTaskFailed(task, "视频任务超过 " + PROVIDER_TASK_TIMEOUT_MINUTES + " 分钟仍未完成，已自动停止续查；请重新生成或检查火山任务日志");
            return false;
        }
        if (AivideoTaskStatus.FAILED.name().equals(normalizeStatus(task.getTaskStatus()))
                && !isRecoverableFailedProviderTask(task)) {
            return false;
        }
        LocalDateTime taskTime = firstTime(task.getStartedTime(), task.getUpdateTime(), task.getCreateTime());
        return taskTime == null || !taskTime.isBefore(now().minusHours(PROVIDER_TASK_REUSE_HOURS));
    }

    private boolean isProviderTaskTimedOut(AiVideoGenerationTaskPo task) {
        LocalDateTime taskTime = firstTime(task != null ? task.getStartedTime() : null,
                task != null ? task.getCreateTime() : null);
        return taskTime != null && taskTime.isBefore(now().minusMinutes(PROVIDER_TASK_TIMEOUT_MINUTES));
    }

    private boolean isRecoverableFailedProviderTask(AiVideoGenerationTaskPo task) {
        String message = firstText(task.getErrorMessage());
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return isRecoverableProviderMessage(message);
    }

    private boolean shouldKeepProviderTaskPending(AiVideoGenerationTaskPo task, Exception exception) {
        if (task == null || !StringUtils.hasText(task.getProviderTaskId())) {
            return false;
        }
        String message = firstText(exception != null ? exception.getMessage() : null);
        return isRecoverableProviderMessage(message);
    }

    private boolean isRecoverableProviderMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("供应商状态") || normalized.contains("provider status")
                || normalized.contains("exceeds maximum permitted size") || normalized.contains("超过 300mb")) {
            return false;
        }
        return normalized.contains("sse")
                || normalized.contains("broken pipe")
                || normalized.contains("read timed out")
                || normalized.contains("timeout")
                || normalized.contains("temporarily unavailable")
                || normalized.contains("temporary failure")
                || normalized.contains("connection")
                || normalized.contains("network")
                || normalized.contains("busy")
                || normalized.contains("try again")
                || normalized.contains("later")
                || normalized.contains("upload")
                || normalized.contains("download")
                || normalized.contains("ioexception")
                || normalized.contains("超时")
                || normalized.contains("网络")
                || normalized.contains("连接")
                || normalized.contains("系统繁忙")
                || normalized.contains("稍后重试")
                || normalized.contains("稍后刷新")
                || normalized.contains("暂未完成")
                || normalized.contains("文件服务")
                || normalized.contains("上传")
                || normalized.contains("下载")
                || normalized.contains("归档");
    }

    private String buildProviderPendingMessage(Exception exception) {
        String message = firstText(exception != null ? exception.getMessage() : null);
        if (!StringUtils.hasText(message)) {
            return "视频任务已提交，后续查询或归档暂未完成，请稍后刷新候选视频";
        }
        return "视频任务已提交，后续查询或归档暂未完成，请稍后刷新候选视频：" + message;
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
        task.setErrorCode(null);
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
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setFinishedTime(now());
        fillUpdateAudit(task);
        transactionTemplate.executeWithoutResult(status -> taskMapper.update(null, new LambdaUpdateWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getTaskId, task.getTaskId())
                .set(AiVideoGenerationTaskPo::getModelId, task.getModelId())
                .set(AiVideoGenerationTaskPo::getTaskStatus, task.getTaskStatus())
                .set(AiVideoGenerationTaskPo::getProgress, task.getProgress())
                .set(AiVideoGenerationTaskPo::getErrorCode, null)
                .set(AiVideoGenerationTaskPo::getErrorMessage, null)
                .set(AiVideoGenerationTaskPo::getFinishedTime, task.getFinishedTime())
                .set(AiVideoGenerationTaskPo::getUpdateBy, task.getUpdateBy())
                .set(AiVideoGenerationTaskPo::getUpdateTime, task.getUpdateTime())));
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
                                               AiVideoShotPo previousShot, AiVideoMediaAssetPo previousTailFrameMedia,
                                               List<AiVideoMediaAssetPo> referenceMedias, List<String> referenceImageUrls,
                                               String ratio, String resolution, int durationSec,
                                               StrategyContext strategy) {
        Map<String, String> variables = new LinkedHashMap<>();
        String referenceImageUrl = referenceImageUrls.get(0);
        variables.put("projectName", safeValue(project.getProjectName()));
        variables.put("targetPlatform", safeValue(project.getTargetPlatform()));
        variables.put("style", safeValue(strategy.visualStyle()));
        variables.put(PARAM_GENERATION_STRATEGY, strategy.generationStrategy());
        variables.put(PARAM_AUDIO_MODE, strategy.audioMode());
        variables.put(PARAM_SUBTITLE_MODE, strategy.subtitleMode());
        variables.put(PARAM_REFERENCE_STRATEGY, strategy.referenceStrategy());
        variables.put(PARAM_ACTION_INTENSITY, strategy.actionIntensity());
        variables.put(PARAM_CONTINUITY_LEVEL, strategy.continuityLevel());
        variables.put(PARAM_MULTI_ROLE_STRATEGY, strategy.multiRoleStrategy());
        variables.put(PARAM_CHARACTER_DESIGN_TYPE, strategy.characterDesignType());
        variables.put("characterDesignInstruction", characterDesignInstruction(strategy.characterDesignType(), strategy.visualStyle()));
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
        variables.put("characterContinuity", buildCharacterContinuity(project.getProjectId(), shot.getCharacterIds()));
        variables.put("sceneContinuity", buildSceneContinuity(scene, previousShot));
        variables.put("audioVisualProtocol", buildAudioVisualProtocol(shot, strategy));
        variables.put("shotType", safeValue(shot.getShotType()));
        variables.put("cameraPosition", safeValue(shot.getCameraPosition()));
        variables.put("cameraMovement", safeValue(shot.getCameraMovement()));
        variables.put("actionDesc", safeValue(shot.getActionDesc()));
        variables.put("dialogue", safeValue(shot.getDialogue()));
        variables.put("voiceOver", safeValue(shot.getVoiceOver()));
        variables.put("emotion", safeValue(shot.getEmotion()));
        variables.put("shotPromptText", safeValue(shot.getPromptText()));
        variables.put("referenceImageUrl", referenceImageUrl);
        variables.put("referenceImageUrls", formatReferenceImageUrls(referenceImageUrls));
        variables.put("referenceImageCount", String.valueOf(referenceImageUrls.size()));
        variables.put("referenceMediaIds", formatReferenceMediaIds(referenceMedias));
        variables.put("referenceAnchorSummary", buildReferenceAnchorSummary(referenceMedias));
        variables.put("referenceFrameType", previousTailFrameMedia != null ? "上一分镜尾帧 + 当前场景/角色锚点" : "当前场景图 + 角色锚点");
        variables.put("previousShotNo", previousShot == null ? "无" : String.valueOf(firstInteger(previousShot.getShotNo(), 1)));
        variables.put("previousShotSummary", buildPreviousShotSummary(previousShot));
        variables.put("previousEndState", buildPreviousEndState(previousShot));
        variables.put("previousTailFrameUrl", previousTailFrameMedia == null ? "" : referenceImageUrl);
        variables.put("currentStartState", buildCurrentStartState(previousShot, previousTailFrameMedia));
        variables.put("currentEndState", buildCurrentEndState(shot));
        variables.put("motionBoundary", buildMotionBoundary(shot));
        variables.put("continuityNegativePrompt", buildContinuityNegativePrompt(shot));
        variables.put("actionBeats", buildActionBeats(shot, durationSec));
        variables.put("timingPlan", buildTimingPlan(shot, durationSec));
        variables.put("compositionRequirement", buildCompositionRequirement(shot));
        variables.put("bodyPartRequirement", buildBodyPartRequirement(shot));
        variables.put("glowRequirement", buildGlowRequirement(shot));
        return variables;
    }

    private String buildShotVideoPrompt(AiVideoProjectPo project, AiVideoScenePo scene, AiVideoShotPo shot,
                                        AiVideoShotPo previousShot, AiVideoMediaAssetPo previousTailFrameMedia,
                                        String ratio, String resolution, int durationSec,
                                        List<AiVideoMediaAssetPo> referenceMedias, List<String> referenceImageUrls,
                                        StrategyContext strategy) {
        String referenceImageUrl = referenceImageUrls.get(0);
        String referenceAnchorSummary = buildReferenceAnchorSummary(referenceMedias);
        String characterNames = safeValue(resolveCharacterNames(project.getProjectId(), shot.getCharacterIds()));
        String characterContinuity = buildCharacterContinuity(project.getProjectId(), shot.getCharacterIds());
        String sceneContinuity = buildSceneContinuity(scene, previousShot);
        String audioVisualProtocol = buildAudioVisualProtocol(shot, strategy);
        String actionBeats = buildActionBeats(shot, durationSec);
        String timingPlan = buildTimingPlan(shot, durationSec);
        String compositionRequirement = buildCompositionRequirement(shot);
        String bodyPartRequirement = buildBodyPartRequirement(shot);
        String glowRequirement = buildGlowRequirement(shot);
        return """
                # 单分镜视频模型执行版 Prompt

                参考图类型：%s。请基于参考图生成 1 个连续镜头，不要生成多镜头拼接。
                输出规格：%s，%s，约 %s 秒。第一帧参考图地址：%s。
                实际传入参考图共 %s 张：
                %s

                ## 第一帧和连续性
                - 第一帧必须贴合参考图：主体位置、姿态、朝向、体型、毛色/服饰、光影、天气和背景空间保持一致。
                - 上一镜头：%s；上一镜头结束状态：%s。
                - 本镜头起始状态：%s。
                - 本镜头结尾状态：%s。
                - 连续性强度：%s。若为极严格，必须同时继承上一尾帧、同场景锚点和角色锚点；缺少任一锚点时不得擅自改背景或主体。
                - 多参考图优先级：图片1决定起始帧/空间连续性；场景图锁定空间、天气、光线和道具；角色图锁定身份、体型比例、脸型、服装/毛色和标志物。
                - 角色锚定图使用规则：角色图优先于角色文字描述；若文字描述与角色图冲突，必须以角色图中的造型、比例、服装、发型、猫耳/猫尾等标志物为准；不得把白底/浅灰棚拍背景带入剧情场景，不得把单主体锚定图复制成多只同款主体。

                ## 主体、场景、构图
                - 项目/风格：%s / %s。
                - 生成策略：%s；参考素材策略：%s；动作强度：%s；多角色策略：%s；角色造型类型：%s。
                - 角色造型硬规则：%s
                - 场景：%s，%s，%s，%s，视觉特征：%s。
                - 出场主体：%s。
                - 角色一致性：%s。
                - 场景一致性：%s。
                - 构图要求：%s
                - 部位可见要求：%s
                - 发光部位要求：%s

                ## 动作节拍
                %s

                ## 执行顺序
                %s

                ## 镜头语言
                - 景别/机位/运镜：%s / %s / %s。
                - 同一镜头内只保留 1 种主要运镜，运动稳定、低幅度、可剪辑。
                - 动作边界：%s

                ## 音画规则
                %s
                字幕模式：%s。除字幕模式明确要求外，禁止生成字幕、标题字、气泡台词、花字和无关文字。

                ## 负面约束
                %s
                禁止水印、logo、无关文字；禁止换角色、换物种、换毛色、换体型、换背景；禁止用眼睛发光替代指定部位发光。
                """.formatted(
                previousTailFrameMedia != null ? "上一分镜尾帧参考图" : "当前场景图",
                safeValue(ratio), safeValue(resolution), durationSec, referenceImageUrl,
                referenceImageUrls.size(), referenceAnchorSummary,
                previousShot == null ? "无" : "第 " + firstInteger(previousShot.getShotNo(), 1) + " 镜头，" + buildPreviousShotSummary(previousShot),
                buildPreviousEndState(previousShot),
                buildCurrentStartState(previousShot, previousTailFrameMedia),
                buildCurrentEndState(shot),
                strategy.continuityLevel(),
                safeValue(project.getProjectName()), safeValue(strategy.visualStyle()),
                strategy.generationStrategy(), strategy.referenceStrategy(), strategy.actionIntensity(), strategy.multiRoleStrategy(),
                strategy.characterDesignType(), characterDesignInstruction(strategy.characterDesignType(), strategy.visualStyle()),
                safeValue(scene.getSceneName()), safeValue(scene.getTimeDesc()), safeValue(scene.getWeather()),
                safeValue(scene.getAtmosphere()), safeValue(scene.getVisualFeatures()),
                characterNames, characterContinuity, sceneContinuity,
                compositionRequirement, bodyPartRequirement, glowRequirement,
                actionBeats, timingPlan,
                safeValue(shot.getShotType()), safeValue(shot.getCameraPosition()), safeValue(shot.getCameraMovement()),
                buildMotionBoundary(shot),
                audioVisualProtocol,
                strategy.subtitleMode(),
                buildContinuityNegativePrompt(shot));
    }

    private String formatReferenceImageUrls(List<String> referenceImageUrls) {
        if (referenceImageUrls == null || referenceImageUrls.isEmpty()) {
            return "未填写";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < referenceImageUrls.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append("图片").append(i + 1).append("：").append(referenceImageUrls.get(i));
        }
        return builder.toString();
    }

    private String formatReferenceMediaIds(List<AiVideoMediaAssetPo> referenceMedias) {
        if (referenceMedias == null || referenceMedias.isEmpty()) {
            return "";
        }
        return referenceMedias.stream()
                .map(AiVideoMediaAssetPo::getMediaId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String buildReferenceAnchorSummary(List<AiVideoMediaAssetPo> referenceMedias) {
        if (referenceMedias == null || referenceMedias.isEmpty()) {
            return "未传入参考图。";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < referenceMedias.size(); i++) {
            AiVideoMediaAssetPo media = referenceMedias.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append("- 图片").append(i + 1)
                    .append("（mediaId=").append(media.getMediaId()).append("，")
                    .append(referenceAnchorRole(media, i)).append("）：")
                    .append(referenceAnchorDescription(media));
        }
        return builder.toString();
    }

    private String referenceAnchorRole(AiVideoMediaAssetPo media, int index) {
        String assetType = firstText(media.getAssetType());
        if (index == 0) {
            return "first_frame";
        }
        if ("CHARACTER_IMAGE".equals(assetType)) {
            return "reference_image/character_anchor";
        }
        if ("SCENE_IMAGE".equals(assetType)) {
            return "reference_image/scene_anchor";
        }
        if ("SHOT_TAIL_FRAME".equals(assetType)) {
            return "reference_image/tail_frame";
        }
        return "reference_image";
    }

    private String referenceAnchorDescription(AiVideoMediaAssetPo media) {
        String assetType = firstText(media.getAssetType());
        if ("CHARACTER_IMAGE".equals(assetType)) {
            return "角色锚定图，只锁定身份、Q版/比例、脸型、服装/毛色和猫耳猫尾等标志物，不继承白底背景。";
        }
        if ("SCENE_IMAGE".equals(assetType)) {
            return "场景锚定图，锁定空间结构、时间、天气、光线、道具和背景关系。";
        }
        if ("SHOT_TAIL_FRAME".equals(assetType)) {
            return "上一分镜尾帧，优先锁定本镜头第一帧姿态、朝向、位置和光影。";
        }
        return "参考图，按分镜要求补充视觉锚点。";
    }

    private String buildCharacterContinuity(Long projectId, String characterIds) {
        List<String> tokens = parseCharacterTokens(characterIds);
        if (tokens.isEmpty()) {
            return "未指定角色时，必须保持参考图中已有主体，不新增、不替换、不改变主体身份。";
        }
        List<String> details = new ArrayList<>();
        for (String token : tokens) {
            try {
                Long characterId = Long.parseLong(token);
                AiVideoCharacterPo character = characterMapper.selectById(characterId);
                if (character != null && Objects.equals(projectId, character.getProjectId())
                        && Integer.valueOf(DEL_FLAG_NORMAL).equals(character.getDelFlag())) {
                    details.add(buildCharacterContinuityLine(character));
                }
            } catch (NumberFormatException ignored) {
                details.add(token + "：保持分镜已设定的身份、外观、颜色和标志性细节，不替换为其他角色。");
            }
        }
        if (details.isEmpty()) {
            return "出场角色必须继承分镜和参考图中的既定外观，不得跨镜头替换角色或动物。";
        }
        return String.join("\n", details);
    }

    private String buildCharacterContinuityLine(AiVideoCharacterPo character) {
        List<String> fields = new ArrayList<>();
        appendField(fields, "角色名：", character.getCharacterName());
        appendField(fields, "身份/物种：", firstText(character.getIdentityDesc(), character.getStoryRole()));
        appendField(fields, "年龄/性别：", joinNonBlank("/", character.getAgeDesc(), character.getGender()));
        appendField(fields, "外观轮廓：", character.getAppearance());
        appendField(fields, "毛发/发型：", character.getHairStyle());
        appendField(fields, "服饰/身体特征：", character.getCostume());
        appendField(fields, "颜色风格：", character.getColorStyle());
        appendField(fields, "禁改特征：", character.getNegativeTraits());
        appendField(fields, "净化后的角色外观提示词：", sanitizeCharacterImagePromptText(character.getPromptText()));
        if (character.getLockedMediaId() != null) {
            appendField(fields, "已锁定角色图ID：", String.valueOf(character.getLockedMediaId()));
        }
        String detail = fields.isEmpty() ? "角色ID " + character.getCharacterId() : String.join("；", fields);
        return detail + "。同一镜头和跨镜头必须保持为同一角色/同一只动物；如果锁定角色图与文字档案冲突，必须以锁定角色图中的造型、比例、脸型、服装/毛色和猫耳猫尾等标志物为准；不得换物种、毛色、体型、脸型、眼睛、年龄感、项圈、斑纹或其他标志物；角色图只作外观锚定，不继承白底棚拍背景，不复制同款分身。";
    }

    private String buildSceneContinuity(AiVideoScenePo scene, AiVideoShotPo previousShot) {
        List<String> fields = new ArrayList<>();
        appendField(fields, "场景名称：", scene.getSceneName());
        appendField(fields, "场景类型：", scene.getSceneType());
        appendField(fields, "时间：", scene.getTimeDesc());
        appendField(fields, "天气：", scene.getWeather());
        appendField(fields, "氛围：", scene.getAtmosphere());
        appendField(fields, "视觉特征：", scene.getVisualFeatures());
        appendField(fields, "色调：", scene.getColorTone());
        appendField(fields, "核心道具：", scene.getProps());
        appendField(fields, "禁用元素：", scene.getNegativeElements());
        appendField(fields, "原始场景提示词：", scene.getPromptText());
        String relation;
        if (previousShot == null) {
            relation = "首镜头以当前参考场景图建立背景锚点。";
        } else if (Objects.equals(scene.getSceneId(), previousShot.getSceneId())) {
            relation = "本镜头与上一镜头属于同一场景，必须延续上一镜头背景空间和光影。";
        } else {
            relation = "当前镜头绑定场景与上一镜头不同；仅允许按当前场景字段完成明确切场，不额外发明新地点。";
        }
        String detail = fields.isEmpty() ? "按参考图保持场景空间、光线、天气和道具。" : String.join("；", fields);
        return relation + detail + "。背景空间、前中后景、光线、天气、色调和道具不得无故变化；未写明的新物体、陌生建筑或其他角色不要出现。";
    }

    private StrategyContext resolveStrategy(AivideoShotVideoGenerateDto dto,
                                            AiVideoProjectSettingPo projectSetting,
                                            AiVideoProjectSettingPo globalSetting) {
        return new StrategyContext(
                firstText(dto.getDefaultStyle(),
                        strategyText(projectSetting, globalSetting, PARAM_DEFAULT_STYLE, DEFAULT_STYLE)),
                firstText(dto.getGenerationStrategy(),
                        strategyText(projectSetting, globalSetting, PARAM_GENERATION_STRATEGY, DEFAULT_GENERATION_STRATEGY)),
                firstText(dto.getAudioMode(),
                        strategyText(projectSetting, globalSetting, PARAM_AUDIO_MODE, DEFAULT_AUDIO_MODE)),
                firstText(dto.getSubtitleMode(),
                        strategyText(projectSetting, globalSetting, PARAM_SUBTITLE_MODE, DEFAULT_SUBTITLE_MODE)),
                firstText(dto.getReferenceStrategy(),
                        strategyText(projectSetting, globalSetting, PARAM_REFERENCE_STRATEGY, DEFAULT_REFERENCE_STRATEGY)),
                firstText(dto.getActionIntensity(),
                        strategyText(projectSetting, globalSetting, PARAM_ACTION_INTENSITY, DEFAULT_ACTION_INTENSITY)),
                firstText(dto.getContinuityLevel(),
                        strategyText(projectSetting, globalSetting, PARAM_CONTINUITY_LEVEL, DEFAULT_CONTINUITY_LEVEL)),
                firstText(dto.getMultiRoleStrategy(),
                        strategyText(projectSetting, globalSetting, PARAM_MULTI_ROLE_STRATEGY, DEFAULT_MULTI_ROLE_STRATEGY)),
                firstText(dto.getCharacterDesignType(),
                        strategyText(projectSetting, globalSetting, PARAM_CHARACTER_DESIGN_TYPE, DEFAULT_CHARACTER_DESIGN_TYPE))
        );
    }

    private boolean shouldGenerateAudio(String audioMode) {
        String mode = firstText(audioMode, DEFAULT_AUDIO_MODE).toUpperCase(Locale.ROOT);
        return "NATIVE_AUDIO".equals(mode) || "REFERENCE_AUDIO".equals(mode);
    }

    private String buildAudioVisualProtocol(AiVideoShotPo shot, StrategyContext strategy) {
        String dialogue = firstText(shot != null ? shot.getDialogue() : null, "无");
        String voiceOver = firstText(shot != null ? shot.getVoiceOver() : null, "无");
        String mode = firstText(strategy.audioMode(), DEFAULT_AUDIO_MODE).toUpperCase(Locale.ROOT);
        String audioRule;
        if ("NATIVE_AUDIO".equals(mode)) {
            audioRule = "声音模式=原生有声：允许视频模型生成本镜头声音，但必须严格沿用项目声线设定，不得随机改变旁白/角色音色、BGM 或音效风格。";
        } else if ("REFERENCE_AUDIO".equals(mode)) {
            audioRule = "声音模式=参考音频有声：必须使用参考音频作为音色锚点；没有参考音频时不要自行发明新声线，优先保持画面生成稳定。";
        } else if ("POST_TTS".equals(mode)) {
            audioRule = "声音模式=后期 TTS：本阶段只生成画面，不生成配音、BGM 或音效；对白和旁白只作为后期配音脚本保留。";
        } else {
            audioRule = "声音模式=静音：本阶段只生成画面，generate_audio=false，不生成、不替换、不改变配音、旁白声线、BGM 或音效。";
        }
        return audioRule + " 对白：" + dialogue
                + "；旁白：" + voiceOver
                + "。只有对白允许角色张嘴和口型同步；旁白、心理活动和环境描述必须作为画外音处理，角色不张嘴、不做口型，用眼神、呼吸、姿态和环境变化承接情绪。分镜之间保持同一旁白/配音口吻、语速、性别/年龄感和情绪连续，禁止声线突变。";
    }

    private String buildPreviousShotSummary(AiVideoShotPo previousShot) {
        if (previousShot == null) {
            return "首镜头，无上一镜头。";
        }
        return "第 " + firstInteger(previousShot.getShotNo(), 1) + " 镜头：" + firstText(
                previousShot.getActionDesc(), previousShot.getPromptText(), "上一镜头已确认。");
    }

    private String buildPreviousEndState(AiVideoShotPo previousShot) {
        if (previousShot == null) {
            return "无上一镜头，按当前参考图自然开场。";
        }
        String action = firstText(previousShot.getActionDesc(), previousShot.getPromptText());
        if (!StringUtils.hasText(action)) {
            return "上一镜头结束时的主体位置、姿态、表情和光影必须被本镜头继承。";
        }
        return "继承上一镜头结束状态：" + action + "。如果上一镜头尾帧可用，以尾帧中的主体位置、姿态和光影作为准绳。";
    }

    private String buildCurrentStartState(AiVideoShotPo previousShot, AiVideoMediaAssetPo previousTailFrameMedia) {
        if (previousShot == null) {
            return "首镜头从当前参考场景图自然开场，主体位置和动作按本镜头分镜描述进入。";
        }
        if (previousTailFrameMedia != null) {
            return "必须从上一镜头尾帧参考图开始：主体位置、姿态、朝向、光影和环境保持一致，再缓慢进入本镜头动作。";
        }
        return "必须从上一镜头已确认视频的结尾状态开始，不能跳过衔接；如果没有尾帧图，按上一镜头动作描述推断结尾姿态。";
    }

    private String buildActionBeats(AiVideoShotPo shot, int durationSec) {
        List<String> beats = extractActionBeats(firstText(shot != null ? shot.getActionDesc() : null,
                shot != null ? shot.getPromptText() : null));
        if (beats.isEmpty()) {
            return "动作预算：本镜头只表现 1 个清晰主动作、1 个表情反应和 1 个结尾状态，动作缺失时按分镜画面自然补齐。";
        }
        String endState = findEndStateBeat(beats);
        StringBuilder builder = new StringBuilder("动作预算：").append(actionBudgetText(durationSec)).append("\n");
        List<String> selected = selectActionBeats(beats, durationSec);
        for (int i = 0; i < selected.size(); i++) {
            builder.append("- 动作 ").append(i + 1).append("：").append(selected.get(i)).append("\n");
        }
        if (StringUtils.hasText(endState)) {
            builder.append("- 结尾状态：").append(endState).append("\n");
        }
        if (beats.size() > selected.size()) {
            builder.append("- 超预算处理：原始动作超过本时长预算，未列入的后续动作不要硬塞进本镜头，应留给后续分镜。");
        }
        return builder.toString().trim();
    }

    private String buildTimingPlan(AiVideoShotPo shot, int durationSec) {
        List<String> beats = selectActionBeats(extractActionBeats(firstText(shot != null ? shot.getActionDesc() : null,
                shot != null ? shot.getPromptText() : null)), durationSec);
        String action1 = beats.isEmpty() ? "从起始状态进入本镜头主动作，动作缓慢清晰" : beats.get(0);
        String action2 = beats.size() > 1 ? beats.get(1) : firstText(shot != null ? shot.getEmotion() : null, "用眼神、呼吸或姿态表现反应");
        String action3 = beats.size() > 2 ? beats.get(2) : findEndStateBeat(extractActionBeats(firstText(
                shot != null ? shot.getActionDesc() : null, shot != null ? shot.getPromptText() : null)));
        String endState = firstText(action3, "停在本镜头自然结尾状态，便于下一镜头继续");
        if (durationSec <= 5) {
            return "- 前段：从上一尾帧或参考图自然进入，执行主动作：" + action1 + "。\n"
                    + "- 中段：用低幅度表情/呼吸/姿态完成反应：" + action2 + "。\n"
                    + "- 末段：停在结尾状态：" + endState + "。";
        }
        if (durationSec <= 6) {
            return "- 前段：从上一尾帧或参考图自然进入，执行动作一：" + action1 + "。\n"
                    + "- 中段：连续衔接动作二或反应：" + action2 + "。\n"
                    + "- 末段：停在结尾状态：" + endState + "。";
        }
        return "- 前段：从上一尾帧或参考图自然进入，执行动作一：" + action1 + "。\n"
                + "- 中段：连续衔接动作二：" + action2 + "。\n"
                + "- 末段：完成动作三或明确结尾状态：" + endState + "。";
    }

    private String buildCompositionRequirement(AiVideoShotPo shot) {
        List<String> parts = detectTargetParts(collectShotText(shot));
        if (parts.isEmpty()) {
            return "保持参考图主体清晰，优先中景或近景，避免无故切到纯脸部大特写导致动作丢失。";
        }
        return "必须使用半身或全身构图，镜头内持续露出" + String.join("、", parts)
                + "，不要只拍脸部特写。";
    }

    private String buildBodyPartRequirement(AiVideoShotPo shot) {
        List<String> parts = detectTargetParts(collectShotText(shot));
        if (parts.isEmpty()) {
            return "无特定肢体部位时，保持主体动作和表情清楚可见。";
        }
        return "目标部位为：" + String.join("、", parts)
                + "；这些部位必须在关键动作发生时完整可见，不能被裁切、遮挡或移出画面。";
    }

    private String buildGlowRequirement(AiVideoShotPo shot) {
        String text = collectShotText(shot);
        if (!containsAny(text, "发光", "微光", "光芒", "亮起", "闪光")) {
            return "无发光要求时，不要额外增加眼睛、身体或背景发光。";
        }
        List<String> parts = detectTargetParts(text);
        if (parts.isEmpty()) {
            return "发光必须严格按分镜描述的位置出现，禁止擅自改成眼睛发光或全身发光。";
        }
        return "发光部位锁定为：" + String.join("、", parts)
                + "；只能这些部位发光，禁止用眼睛发光、全身发光或背景闪光替代。";
    }

    private int normalizeAivideoShotDuration(Integer durationSec) {
        if (durationSec == null || durationSec <= 5) {
            return 5;
        }
        if (durationSec <= 6) {
            return 6;
        }
        return 8;
    }

    private String buildCurrentEndState(AiVideoShotPo shot) {
        String action = firstText(shot != null ? shot.getActionDesc() : null, shot != null ? shot.getPromptText() : null);
        if (!StringUtils.hasText(action)) {
            return "本镜头结束时保持当前分镜动作的自然结果，便于下一镜头继续。";
        }
        String endState = firstText(findEndStateBeat(extractActionBeats(action)), action);
        StringBuilder builder = new StringBuilder("本镜头结束时停留在当前分镜动作的自然结果：").append(endState).append("。");
        if (containsAny(action, "悬浮", "漂浮", "飞起", "飞到", "飞向")) {
            builder.append(" 若涉及悬浮，默认只允许低空、缓慢、原地附近悬浮，结束时主体仍靠近原位置。");
        }
        return builder.toString();
    }

    private String buildMotionBoundary(AiVideoShotPo shot) {
        String text = collectShotText(shot);
        if (containsAny(text, "悬浮", "漂浮")) {
            return "悬浮必须是缓慢、低空、近地、原地附近变化；默认离地约 5-15 厘米，除非分镜明确写飞向天空。";
        }
        if (containsAny(text, "飞起", "飞到", "飞向", "升空")) {
            return "飞行动作必须有明确起点和终点，速度克制，不能突然冲出画面或改变主体身份。";
        }
        if (containsAny(text, "倒地", "趴", "蜷缩", "抽搐")) {
            return "倒地、趴伏或抽搐动作必须保持低位姿态，动作连续，不要突然站起、跳起或大幅位移。";
        }
        return "动作必须单一、连续、可剪辑，主体不要瞬移、跳切、突然换姿态或离开既定空间。";
    }

    private String buildContinuityNegativePrompt(AiVideoShotPo shot) {
        String text = collectShotText(shot);
        String base = "禁止跳切、瞬移、突然换姿态、突然改变主体大小或身份、突然换场景、突然改变天气光线、字幕、水印、logo、花字、无关文字。";
        if (containsAny(text, "悬浮", "漂浮", "飞起", "飞到", "飞向")) {
            return base + " 禁止一开始就高空飞行，禁止高速升空，禁止翻滚，禁止离开画面中心区域。";
        }
        return base;
    }

    private String actionBudgetText(int durationSec) {
        if (durationSec <= 5) {
            return "5 秒镜头只允许 1 个主动作、1 个反应/表情和 1 个结尾状态";
        }
        if (durationSec <= 6) {
            return "6 秒镜头允许 2 个连续动作和 1 个结尾状态";
        }
        return "8 秒镜头允许 3 个连续动作和 1 个明确结尾状态";
    }

    private List<String> selectActionBeats(List<String> beats, int durationSec) {
        if (beats == null || beats.isEmpty()) {
            return List.of();
        }
        int maxBeats = durationSec <= 5 ? 1 : (durationSec <= 6 ? 2 : 3);
        if (containsAny(String.join("，", beats), "倒地起身", "悬浮", "变身", "俯冲", "落水", "打斗", "救援", "掰弯铁栏")) {
            maxBeats = Math.min(maxBeats, 1);
        }
        List<String> selected = new ArrayList<>();
        for (String beat : beats) {
            if (!StringUtils.hasText(beat)) {
                continue;
            }
            if (selected.size() >= maxBeats) {
                break;
            }
            selected.add(beat);
        }
        return selected;
    }

    private List<String> extractActionBeats(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.replace("\r", "\n")
                .replace("然后", "，")
                .replace("随后", "，")
                .replace("接着", "，")
                .replace("同时", "，")
                .replace("并且", "，")
                .replace("并", "，");
        String[] parts = normalized.split("[，,。；;\\n]+");
        List<String> beats = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                beats.add(part.trim());
            }
        }
        return beats;
    }

    private String findEndStateBeat(List<String> beats) {
        if (beats == null || beats.isEmpty()) {
            return "";
        }
        for (int i = beats.size() - 1; i >= 0; i--) {
            String beat = beats.get(i);
            if (containsAny(beat, "发光", "微光", "光芒", "亮起", "悬浮", "漂浮", "停在", "定格", "倒地", "落地")) {
                return beat;
            }
        }
        return beats.get(beats.size() - 1);
    }

    private List<String> detectTargetParts(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        addPartIfPresent(parts, text, "前爪/爪子", "前爪", "爪子", "爪");
        addPartIfPresent(parts, text, "手", "手", "手掌", "手指", "手臂");
        addPartIfPresent(parts, text, "脚", "脚", "脚掌", "腿", "膝盖");
        addPartIfPresent(parts, text, "翅膀", "翅膀", "羽翼");
        addPartIfPresent(parts, text, "尾巴", "尾巴", "尾");
        return parts;
    }

    private void addPartIfPresent(List<String> parts, String text, String label, String... keywords) {
        if (parts.contains(label)) {
            return;
        }
        if (containsAny(text, keywords)) {
            parts.add(label);
        }
    }

    private String collectShotText(AiVideoShotPo shot) {
        if (shot == null) {
            return "";
        }
        return firstText(shot.getActionDesc()) + "\n"
                + firstText(shot.getPromptText()) + "\n"
                + firstText(shot.getVoiceOver()) + "\n"
                + firstText(shot.getDialogue());
    }

    private boolean containsAny(String text, String... keywords) {
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
        List<String> tokens = parseCharacterTokens(characterIds);
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

    private List<String> parseCharacterTokens(String characterIds) {
        if (!StringUtils.hasText(characterIds)) {
            return List.of();
        }
        return Arrays.stream(characterIds.trim().replace("[", "").replace("]", "").replace("\"", "").split("[,，、]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void appendField(List<String> fields, String label, String value) {
        if (StringUtils.hasText(value)) {
            fields.add(label + value.trim());
        }
    }

    private String joinNonBlank(String delimiter, String... values) {
        if (values == null) {
            return "";
        }
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + delimiter + right)
                .orElse("");
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
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private void completeWithError(SseEmitter emitter, String message) {
        try {
            sendSse(emitter, "error", StringUtils.hasText(message) ? message : "视频生成失败");
        } finally {
            safeComplete(emitter);
        }
    }

    private void completeWithDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException | RuntimeException exception) {
            // Client-side disconnects must not mark the provider task as failed.
        } finally {
            safeComplete(emitter);
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // Client-side disconnects after a successful provider result are not business failures.
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

    private Map<String, Object> parseTaskParams(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> params = XuJsonUtil.parseObject(paramsJson, Map.class);
            return params == null ? Map.of() : params;
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private String paramText(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key)) {
            return null;
        }
        Object value = params.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Integer paramInteger(Map<String, Object> params, String key) {
        String text = paramText(params, key);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
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
            List<String> referenceImageUrls,
            List<AiVideoMediaAssetPo> referenceMedias,
            Map<String, String> variables,
            StrategyContext strategy
    ) {
    }

    private record StrategyContext(
            String visualStyle,
            String generationStrategy,
            String audioMode,
            String subtitleMode,
            String referenceStrategy,
            String actionIntensity,
            String continuityLevel,
            String multiRoleStrategy,
            String characterDesignType
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
