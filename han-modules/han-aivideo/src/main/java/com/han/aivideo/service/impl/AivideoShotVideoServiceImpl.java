package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.aivideo.domain.dto.AivideoShotScriptOptimizeDto;
import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import com.han.aivideo.domain.vo.AivideoShotScriptOptimizeVo;
import com.han.aivideo.enums.AivideoTaskStatus;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoPropMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single shot video candidate workflow implementation.
 */
@Service
@RequiredArgsConstructor
public class AivideoShotVideoServiceImpl extends AivideoServiceSupport implements IAivideoShotVideoService {

    private static final String ASSET_SHOT_VIDEO = "SHOT_VIDEO";
    private static final String ASSET_SHOT_AUDIO = "SHOT_AUDIO";
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
    private static final Pattern EXPLICIT_ONSCREEN_COUNT_PATTERN =
            Pattern.compile("当前镜头在场角色\\s*[:：]\\s*(\\d+|[一二两三四五六七八九十])\\s*人");
    private static final Pattern REMAINING_PEOPLE_PATTERN =
            Pattern.compile("其余\\s*(\\d+|[一二两三四五六七八九十])\\s*人");
    private static final Pattern GROUP_PEOPLE_PATTERN =
            Pattern.compile("(\\d+|[一二两三四五六七八九十])\\s*(人|个角色|名角色|位角色)");
    private static final String SHOT_VIDEO_SYSTEM_PROMPT = """
            你是电影级短剧分镜视频导演。
            核心规则：
            1. 基于已确认场景图生成单个短剧镜头视频，不生成整剧，不跨镜头。
            2. 必须严格执行镜头连续性协议：上一镜头结束姿态就是本镜头起始姿态，不允许跳切、瞬移、突然换姿态。
            3. 必须保持参考图的空间关系、时间、天气、色调和主体环境稳定；若参考图为上一镜头尾帧，优先继承尾帧中的主体位置和姿态。
            4. 严格执行音画三轨协议：对白=说出口并允许口型同步；旁白/画外音=可发声但角色不张嘴；心声/心理活动=默认不朗读、不口型，只通过眼神、表情、动作和画面隐喻表现。带“角色名（画外音）”的旁白必须继承该角色声线，不得切成当前画面角色声线。
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

    @Autowired(required = false)
    private AiVideoPropMapper propMapper;

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
    public AivideoShotScriptOptimizeVo optimizeShotScript(AivideoShotScriptOptimizeDto dto) {
        if (dto == null || dto.getProjectId() == null || dto.getShotId() == null) {
            throw new BusinessException("项目ID和分镜ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoShotPo shot = requireShot(project.getProjectId(), dto.getShotId());
        AiVideoScenePo scene = shot.getSceneId() == null ? null : requireScene(project.getProjectId(), shot.getSceneId());
        AiVideoShotPo previousShot = findPreviousShot(project, shot);
        String userPrompt = buildShotScriptOptimizePrompt(project, scene, shot, previousShot, dto);
        String rawResult = renderPrompt(project, null, dto.getCustomPrompt(), userPrompt, Map.of(
                "projectName", firstText(project.getProjectName()),
                "shotNo", String.valueOf(firstInteger(shot.getShotNo(), 0))
        ));
        String json = normalizeShotScriptOptimizeJson(project, rawResult, userPrompt, dto.getCustomPrompt());
        ShotScriptOptimizePayload payload = parseShotScriptOptimizePayload(json, rawResult);
        if (payload == null) {
            throw new BusinessException("分镜优化结果为空");
        }
        applyShotScriptOptimization(project.getProjectId(), shot, payload);
        shot.setUpdateBy(resolveOperator());
        shot.setUpdateTime(now());
        shotMapper.updateById(shot);

        AivideoShotScriptOptimizeVo vo = new AivideoShotScriptOptimizeVo();
        vo.setShot(shot);
        vo.setOptimizedJson(json);
        vo.setRawResult(rawResult);
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
        if (requireVideoModel && shouldRequirePreviousShotVideo(shot, previousShot)) {
            validatePreviousShotReady(previousShot);
        }
        validateShotRuntimeContinuity(project.getProjectId(), shot, previousShot);
        AiVideoMediaAssetPo previousTailFrameMedia = findTailFrameMedia(project.getProjectId(), previousShot);
        AiVideoMediaAssetPo previousVideoMedia = findPreviousVideoMedia(project.getProjectId(), previousShot);
        AiVideoMediaAssetPo previousAudioMedia = findPreviousAudioMedia(project.getProjectId(), previousShot);
        List<AiVideoMediaAssetPo> referenceMedias = buildShotVideoReferenceMedias(project.getProjectId(), shot,
                previousShot,
                sceneReferenceMedia, previousTailFrameMedia, dto.getReferenceMediaIds());
        boolean referenceImageAsFirstFrame = shouldSendReferenceImageAsFirstFrame(referenceMedias, previousTailFrameMedia);
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
        validateShotActionBudgetAndProps(project.getProjectId(), shot, durationSec);
        validateShotOnscreenCharacterCount(project.getProjectId(), shot);
        Long promptTemplateId = firstLong(
                projectSetting != null ? projectSetting.getVideoPromptTemplateId() : null,
                globalSetting != null ? globalSetting.getVideoPromptTemplateId() : null);
        StrategyContext strategy = resolveStrategy(dto, projectSetting, globalSetting);
        List<String> referenceImageUrls = buildProviderFileUrls(referenceMedias);
        String referenceImageUrl = referenceImageUrls.get(0);
        String referenceVideoUrl = buildPreviousReferenceVideoUrl(shot, previousShot, previousVideoMedia);
        String referenceAudioUrl = buildPreviousReferenceAudioUrl(shot, previousShot, previousAudioMedia, strategy);
        boolean referenceAudioSeedAllowed = previousShot == null;
        Map<String, String> variables = buildVariables(project, scene, shot, previousShot, previousTailFrameMedia,
                referenceMedias, referenceImageUrls, referenceVideoUrl, referenceAudioUrl,
                referenceAudioSeedAllowed, ratio, resolution, durationSec, strategy);
        String fallbackPrompt = buildShotVideoPrompt(project, scene, shot, previousShot, previousTailFrameMedia,
                ratio, resolution, durationSec, referenceMedias, referenceImageUrls,
                referenceVideoUrl, referenceAudioUrl, referenceAudioSeedAllowed, strategy);
        String tailFrameFirstFrameExecutionGuard = buildTailFrameFirstFrameExecutionGuard(shot, previousShot,
                referenceImageAsFirstFrame);
        variables.put("tailFrameFirstFrameExecutionGuard", tailFrameFirstFrameExecutionGuard);
        String prompt = renderPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        prompt = appendTailFrameFirstFrameExecutionGuard(prompt, shot, previousShot, referenceImageAsFirstFrame);
        variables.put("candidateCount", String.valueOf(candidateCount));
        variables.put("referenceImageAsFirstFrame", String.valueOf(referenceImageAsFirstFrame));
        return new RequestContext(project, scene, shot, referenceMedia, modelId, promptTemplateId,
                candidateCount, ratio, resolution, durationSec, dto.getCustomPrompt(), prompt,
                referenceImageUrl, referenceImageUrls, referenceVideoUrl, referenceAudioUrl, referenceMedias,
                referenceImageAsFirstFrame, referenceAudioSeedAllowed, variables, strategy);
    }

    private String buildShotScriptOptimizePrompt(AiVideoProjectPo project, AiVideoScenePo scene,
                                                 AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                 AivideoShotScriptOptimizeDto dto) {
        String failures = dto.getPreflightFailures() == null || dto.getPreflightFailures().isEmpty()
                ? "无，按当前分镜视频预检规则主动优化。"
                : String.join("\n", dto.getPreflightFailures());
        String projectCharacters = selectProjectCharacters(project.getProjectId()).stream()
                .map(item -> item.getCharacterName() + "#" + item.getCharacterId())
                .filter(StringUtils::hasText)
                .toList()
                .toString();
        String projectProps = selectProjectProps(project.getProjectId()).stream()
                .map(item -> firstText(item.getPropName()) + (item.getLockedMediaId() == null ? "" : "#" + item.getLockedMediaId()))
                .filter(StringUtils::hasText)
                .toList()
                .toString();
        return """
                你是短剧分镜连续性审片和修稿助手。请只优化“当前分镜脚本字段”，不要重写整部剧本。

                ## 项目信息
                项目：%s
                场景：%s
                项目角色：%s
                项目道具：%s

                ## 上一分镜
                %s

                ## 当前分镜原始字段
                shotId=%s，episodeNo=%s，shotNo=%s，durationSec=%s
                characterIds=%s
                shotType=%s
                cameraPosition=%s
                cameraMovement=%s
                transitionBeforeType=%s
                transitionBeforeDesc=%s
                actionDesc=%s
                dialogue=%s
                voiceOver=%s
                emotion=%s
                bgmCue=%s
                sfxCues=%s
                promptText=%s
                referenceMediaIds=%s

                ## 当前不合格项
                %s

                ## 用户追加优化要求
                %s

                ## 硬规则
                1. 只输出一个 JSON 对象，不要解释、不要 Markdown。
                2. 只允许返回这些字段：durationSec、shotType、cameraPosition、cameraMovement、transitionBeforeType、transitionBeforeDesc、actionDesc、dialogue、voiceOver、emotion、bgmCue、sfxCues、promptText、characterIds、referenceMediaIds。
                3. 不要改 episodeNo、shotNo、sceneId、keyframeMediaId、tailFrameMediaId、videoMediaId、confirmStatus、generationStatus。
                4. 如果失败项是“上一镜角色疑似无说明消失”，优先判断当前镜头是否是单人镜头、单人反应、特写裁切或插入镜头：
                   - 如果当前只绑定一个画内主体且没有递给/接过/靠近/同框/对话等互动动作，请在 transitionBeforeDesc 或 promptText 中明确：“单人镜头：画内主体锁定为X，上一镜其他角色A、B被裁切在画外不入画，不自动出现。”
                   - 如果当前动作确实需要其他角色入画，请把这些角色写入 characterIds，并在 actionDesc 中点名每个人的位置、动作和结尾状态。
                5. 如果动作里有递给、接过、交给、展示给、拿给，必须补清 giver、receiver、prop、screenDirection、finalOwner。
                6. 如果出现武器、发光物、收纳盒、试卷、账本、价格标签等关键道具，必须在 actionDesc 或 promptText 里锁定颜色、材质、持有人和结尾归属。
                7. 5 秒镜头最多 1 个主动作 + 1 个反应 + 1 个结尾状态；动作过多时压缩当前镜头，不要硬塞。
                8. dialogue 只写真正说出口的台词；voiceOver 只写旁白/画外音；心理活动不要写成会发声的台词。

                ## 返回 JSON 形状说明
                - 只返回确实需要修改的字段；不需要修改的字段请直接省略。
                - 下方空字符串只是字段形状示意，禁止照抄说明文字或把字段说明写入结果。
                {
                  "durationSec": 5,
                  "transitionBeforeDesc": "",
                  "actionDesc": "",
                  "dialogue": "",
                  "voiceOver": "",
                  "promptText": "",
                  "characterIds": "",
                  "referenceMediaIds": ""
                }
                """.formatted(
                firstText(project.getProjectName()),
                scene == null ? "未绑定" : firstText(scene.getSceneName(), String.valueOf(scene.getSceneId())),
                projectCharacters,
                projectProps,
                shotSnapshot(previousShot),
                shot.getShotId(),
                firstInteger(shot.getEpisodeNo(), 0),
                firstInteger(shot.getShotNo(), 0),
                firstInteger(shot.getDurationSec(), 0),
                firstText(shot.getCharacterIds()),
                firstText(shot.getShotType()),
                firstText(shot.getCameraPosition()),
                firstText(shot.getCameraMovement()),
                firstText(shot.getTransitionBeforeType()),
                firstText(shot.getTransitionBeforeDesc()),
                firstText(shot.getActionDesc()),
                firstText(shot.getDialogue()),
                firstText(shot.getVoiceOver()),
                firstText(shot.getEmotion()),
                firstText(shot.getBgmCue()),
                firstText(shot.getSfxCues()),
                firstText(shot.getPromptText()),
                firstText(shot.getReferenceMediaIds()),
                failures,
                firstText(dto.getCustomPrompt(), "无")
        );
    }

    private String normalizeShotScriptOptimizeJson(AiVideoProjectPo project, String rawResult, String sourcePrompt, String customPrompt) {
        try {
            String json = AivideoTextServiceImpl.normalizeAssetJsonBlock(rawResult);
            parseShotScriptOptimizePayload(json, rawResult);
            return json;
        } catch (RuntimeException primaryException) {
            String repairPrompt = buildShotScriptOptimizeJsonRepairPrompt(rawResult, sourcePrompt, primaryException.getMessage());
            String repaired = renderPrompt(project, null, customPrompt, repairPrompt, Map.of(
                    "projectName", firstText(project.getProjectName()),
                    "repairType", "shot-script-optimize-json"
            ));
            try {
                String json = AivideoTextServiceImpl.normalizeAssetJsonBlock(repaired);
                parseShotScriptOptimizePayload(json, repaired);
                return json;
            } catch (RuntimeException repairException) {
                throw new BusinessException("分镜优化结果不是可用 JSON，请补充提示词后重试。原始输出："
                        + truncateForError(rawResult, 300));
            }
        }
    }

    private ShotScriptOptimizePayload parseShotScriptOptimizePayload(String json, String sourceText) {
        try {
            ShotScriptOptimizePayload payload = XuJsonUtil.parseObject(json, ShotScriptOptimizePayload.class);
            if (payload == null) {
                throw new BusinessException("分镜优化结果为空");
            }
            return payload;
        } catch (RuntimeException exception) {
            throw new BusinessException("分镜优化 JSON 解析失败：" + truncateForError(sourceText, 300));
        }
    }

    private String buildShotScriptOptimizeJsonRepairPrompt(String rawResult, String sourcePrompt, String errorMessage) {
        return """
                你是严格 JSON 修复器。请把【模型原始输出】整理成一个可被后端直接解析的 JSON 对象。

                ## 解析失败原因
                %s

                ## 原始优化任务
                %s

                ## 模型原始输出
                %s

                ## 硬规则
                1. 只输出一个 JSON 对象，不要解释、不要 Markdown、不要代码围栏。
                2. 只允许使用这些字段：durationSec、shotType、cameraPosition、cameraMovement、transitionBeforeType、transitionBeforeDesc、actionDesc、dialogue、voiceOver、emotion、bgmCue、sfxCues、promptText、characterIds、referenceMediaIds。
                3. 如果原始输出没有明确给出某个字段，就不要编造该字段；确实需要保留空值时用空字符串。
                4. dialogue 只放说出口的台词；voiceOver 只放旁白/画外音；心理活动不能写成台词。

                ## 返回结构示例
                - 空字符串只是字段形状示意，禁止照抄空值以外的说明文字。
                {
                  "transitionBeforeDesc": "",
                  "actionDesc": "",
                  "promptText": "",
                  "characterIds": ""
                }
                """.formatted(
                truncateForPrompt(errorMessage, 500),
                truncateForPrompt(sourcePrompt, 2500),
                truncateForPrompt(rawResult, 2500)
        );
    }

    private String shotSnapshot(AiVideoShotPo shot) {
        if (shot == null) {
            return "无上一分镜。";
        }
        return "shotNo=" + firstInteger(shot.getShotNo(), 0)
                + "，characterIds=" + firstText(shot.getCharacterIds())
                + "，transitionBeforeType=" + firstText(shot.getTransitionBeforeType())
                + "，transitionBeforeDesc=" + firstText(shot.getTransitionBeforeDesc())
                + "，actionDesc=" + firstText(shot.getActionDesc())
                + "，promptText=" + firstText(shot.getPromptText());
    }

    private void applyShotScriptOptimization(Long projectId, AiVideoShotPo shot, ShotScriptOptimizePayload payload) {
        if (payload.durationSec != null) {
            shot.setDurationSec(normalizeAivideoShotDuration(payload.durationSec));
        }
        String shotType = sanitizeShotOptimizeText(payload.shotType);
        if (StringUtils.hasText(shotType)) {
            shot.setShotType(shotType);
        }
        String cameraPosition = sanitizeShotOptimizeText(payload.cameraPosition);
        if (StringUtils.hasText(cameraPosition)) {
            shot.setCameraPosition(cameraPosition);
        }
        String cameraMovement = sanitizeShotOptimizeText(payload.cameraMovement);
        if (StringUtils.hasText(cameraMovement)) {
            shot.setCameraMovement(cameraMovement);
        }
        String transitionBeforeType = sanitizeShotOptimizeText(payload.transitionBeforeType);
        if (StringUtils.hasText(transitionBeforeType)) {
            shot.setTransitionBeforeType(transitionBeforeType);
        }
        String transitionBeforeDesc = sanitizeShotOptimizeText(payload.transitionBeforeDesc);
        if (StringUtils.hasText(transitionBeforeDesc)) {
            shot.setTransitionBeforeDesc(transitionBeforeDesc);
        }
        String actionDesc = sanitizeShotOptimizeText(payload.actionDesc);
        if (StringUtils.hasText(actionDesc)) {
            shot.setActionDesc(actionDesc);
        }
        String dialogue = sanitizeShotOptimizeText(payload.dialogue);
        if (StringUtils.hasText(dialogue)) {
            shot.setDialogue(dialogue);
        }
        String voiceOver = sanitizeShotOptimizeText(payload.voiceOver);
        if (StringUtils.hasText(voiceOver)) {
            shot.setVoiceOver(voiceOver);
        }
        String emotion = sanitizeShotOptimizeText(payload.emotion);
        if (StringUtils.hasText(emotion)) {
            shot.setEmotion(emotion);
        }
        String bgmCue = sanitizeShotOptimizeText(payload.bgmCue);
        if (StringUtils.hasText(bgmCue)) {
            shot.setBgmCue(bgmCue);
        }
        String sfxCues = sanitizeShotOptimizeText(payload.sfxCues);
        if (StringUtils.hasText(sfxCues)) {
            shot.setSfxCues(sfxCues);
        }
        String promptText = sanitizeShotOptimizeText(payload.promptText);
        if (StringUtils.hasText(promptText)) {
            shot.setPromptText(promptText);
        }
        String characterIds = sanitizeOptimizedCharacterIds(projectId, payload.characterIds);
        if (StringUtils.hasText(characterIds)) {
            shot.setCharacterIds(characterIds);
        }
        String referenceMediaIds = sanitizeReferenceMediaIds(payload.referenceMediaIds);
        if (StringUtils.hasText(referenceMediaIds)) {
            shot.setReferenceMediaIds(referenceMediaIds);
        }
    }

    private String sanitizeShotOptimizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return isShotOptimizePlaceholder(trimmed) ? null : trimmed;
    }

    private boolean isShotOptimizePlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String text = value.trim();
        if ("X".equalsIgnoreCase(text)) {
            return true;
        }
        return text.contains("只写当前镜头可执行动作")
                || text.contains("控制在本镜秒数预算内")
                || text.contains("给视频模型看的补充执行提示")
                || text.contains("只写当前镜头画内角色名称或ID")
                || text.contains("多个用逗号分隔")
                || text.contains("名称或ID")
                || text.contains("明确说明上一镜角色/道具/方位如何衔接")
                || text.contains("锁定画内角色、道具、方位和结尾状态")
                || text.contains("X完成一个主动作")
                || text.contains("画内主体锁定为X");
    }

    private String sanitizeOptimizedCharacterIds(Long projectId, String value) {
        String sanitized = sanitizeShotOptimizeText(value);
        if (!StringUtils.hasText(sanitized) || projectId == null) {
            return null;
        }
        List<String> tokens = parseCharacterTokens(sanitized);
        if (tokens.isEmpty()) {
            return null;
        }
        List<AiVideoCharacterPo> characters = selectProjectCharacters(projectId);
        if (characters.isEmpty()) {
            return null;
        }
        Set<String> validTokens = new LinkedHashSet<>();
        for (String token : tokens) {
            String matched = matchProjectCharacterToken(token, characters);
            if (StringUtils.hasText(matched)) {
                validTokens.add(matched);
            }
        }
        return validTokens.isEmpty() ? null : String.join(",", validTokens);
    }

    private String matchProjectCharacterToken(String token, List<AiVideoCharacterPo> characters) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalized = token.trim();
        for (AiVideoCharacterPo character : characters) {
            if (character == null || !StringUtils.hasText(character.getCharacterName())) {
                continue;
            }
            String idText = character.getCharacterId() == null ? "" : String.valueOf(character.getCharacterId());
            if (normalized.equals(idText)) {
                return idText;
            }
            if (characterNameAliases(character.getCharacterName()).contains(normalized)) {
                return character.getCharacterName().trim();
            }
        }
        return null;
    }

    private String sanitizeReferenceMediaIds(String value) {
        String sanitized = sanitizeShotOptimizeText(value);
        if (!StringUtils.hasText(sanitized)) {
            return null;
        }
        List<String> mediaIds = Arrays.stream(sanitized.replace("[", "").replace("]", "").replace("\"", "")
                        .split("[,，、]"))
                .map(String::trim)
                .filter(item -> item.matches("\\d+"))
                .toList();
        return mediaIds.isEmpty() ? null : String.join(",", mediaIds);
    }

    private AiVideoGenerateResponse invokeVideoGeneration(RequestContext context) {
        AiVideoGenerateRequest request = new AiVideoGenerateRequest();
        request.setTenantId(context.project().getTenantId());
        request.setModelId(context.modelId());
        request.setUserPrompt(context.prompt());
        request.setReferenceImageUrl(context.referenceImageUrl());
        request.setReferenceImageUrls(context.referenceImageUrls());
        request.setReferenceImageAsFirstFrame(context.referenceImageAsFirstFrame());
        request.setReferenceVideoUrl(context.referenceVideoUrl());
        request.setReferenceAudioUrl(context.referenceAudioUrl());
        request.setCandidateCount(1);
        request.setRatio(context.ratio());
        request.setResolution(context.resolution());
        request.setDurationSec(context.durationSec());
        request.setVariables(context.variables());
        request.setReturnLastFrame(true);
        request.setGenerateAudio(shouldGenerateAudio(context.strategy().audioMode(), context.referenceAudioUrl(),
                context.referenceAudioSeedAllowed()));
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

    private boolean shouldRequirePreviousShotVideo(AiVideoShotPo shot, AiVideoShotPo previousShot) {
        if (shot == null || previousShot == null) {
            return false;
        }
        String transitionType = shot.getTransitionBeforeType();
        if (StringUtils.hasText(transitionType)) {
            return "CONTINUE".equalsIgnoreCase(transitionType.trim());
        }
        return "CONTINUE".equals(AivideoTextServiceImpl.normalizeTransitionBeforeType(null, shot, previousShot));
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

    private AiVideoMediaAssetPo findPreviousVideoMedia(Long projectId, AiVideoShotPo previousShot) {
        if (previousShot == null || previousShot.getVideoMediaId() == null) {
            return null;
        }
        AiVideoMediaAssetPo media = mediaAssetMapper.selectById(previousShot.getVideoMediaId());
        if (media == null || !Objects.equals(projectId, media.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(media.getDelFlag())
                || !ASSET_SHOT_VIDEO.equals(media.getAssetType())
                || !StringUtils.hasText(media.getFileUrl())) {
            return null;
        }
        return media;
    }

    private AiVideoMediaAssetPo findPreviousAudioMedia(Long projectId, AiVideoShotPo previousShot) {
        if (previousShot == null || previousShot.getShotId() == null) {
            return null;
        }
        return mediaAssetMapper.selectList(new LambdaQueryWrapper<AiVideoMediaAssetPo>()
                        .eq(AiVideoMediaAssetPo::getProjectId, projectId)
                        .eq(AiVideoMediaAssetPo::getAssetType, ASSET_SHOT_AUDIO)
                        .eq(AiVideoMediaAssetPo::getBizType, BIZ_SHOT)
                        .eq(AiVideoMediaAssetPo::getBizId, previousShot.getShotId())
                        .eq(AiVideoMediaAssetPo::getSelected, YES)
                        .eq(AiVideoMediaAssetPo::getDelFlag, DEL_FLAG_NORMAL)
                        .orderByDesc(AiVideoMediaAssetPo::getUpdateTime)
                        .orderByDesc(AiVideoMediaAssetPo::getMediaId)
                        .last("limit 1"))
                .stream()
                .filter(media -> StringUtils.hasText(media.getFileUrl()))
                .findFirst()
                .orElse(null);
    }

    private List<AiVideoMediaAssetPo> buildShotVideoReferenceMedias(Long projectId, AiVideoShotPo shot,
                                                                    AiVideoShotPo previousShot,
                                                                    AiVideoMediaAssetPo sceneReferenceMedia,
                                                                    AiVideoMediaAssetPo previousTailFrameMedia,
                                                                    List<Long> explicitReferenceMediaIds) {
        Map<Long, AiVideoMediaAssetPo> references = new LinkedHashMap<>();
        if (shouldUsePreviousTailFrameOnly(shot, previousShot, previousTailFrameMedia, explicitReferenceMediaIds)) {
            addReferenceMedia(references, previousTailFrameMedia);
            return new ArrayList<>(references.values());
        }
        if (shouldUsePreviousTailFrameAsReference(shot, previousShot, previousTailFrameMedia)) {
            addReferenceMedia(references, previousTailFrameMedia);
        }
        addReferenceMedia(references, sceneReferenceMedia);
        if (explicitReferenceMediaIds != null) {
            for (Long mediaId : explicitReferenceMediaIds) {
                addReferenceMedia(references, requireReferenceImage(projectId, mediaId));
            }
        }
        addRequiredPropReferenceMedias(projectId, shot, references);
        addCharacterReferenceMedias(projectId, shot, references);
        if (references.isEmpty()) {
            throw new BusinessException("视频生成参考图不能为空，请先选择场景图或角色图");
        }
        return new ArrayList<>(references.values());
    }

    private boolean shouldUsePreviousTailFrameOnly(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                   AiVideoMediaAssetPo previousTailFrameMedia,
                                                   List<Long> explicitReferenceMediaIds) {
        return previousTailFrameMedia != null
                && shouldRequirePreviousShotVideo(shot, previousShot);
    }

    private boolean shouldUsePreviousTailFrameAsReference(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                          AiVideoMediaAssetPo previousTailFrameMedia) {
        return previousTailFrameMedia != null
                && !shouldRequirePreviousShotVideo(shot, previousShot)
                && shouldUsePreviousVisualReferenceForInsertHandoff(shot, previousShot);
    }

    private String buildPreviousReferenceVideoUrl(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                  AiVideoMediaAssetPo previousVideoMedia) {
        if (!shouldUsePreviousVideoAsReference(shot, previousShot, previousVideoMedia)) {
            return "";
        }
        return buildProviderFileUrl(previousVideoMedia.getFileUrl());
    }

    private String buildPreviousReferenceAudioUrl(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                  AiVideoMediaAssetPo previousAudioMedia, StrategyContext strategy) {
        String mode = firstText(strategy != null ? strategy.audioMode() : null, DEFAULT_AUDIO_MODE)
                .toUpperCase(Locale.ROOT);
        if (!"REFERENCE_AUDIO".equals(mode)
                || previousAudioMedia == null
                || !ASSET_SHOT_AUDIO.equals(previousAudioMedia.getAssetType())
                || !StringUtils.hasText(previousAudioMedia.getFileUrl())) {
            return "";
        }
        boolean canInheritPreviousAudio = shouldRequirePreviousShotVideo(shot, previousShot)
                || shouldUsePreviousVisualReferenceForInsertHandoff(shot, previousShot);
        if (!canInheritPreviousAudio) {
            return "";
        }
        return buildProviderFileUrl(previousAudioMedia.getFileUrl());
    }

    private boolean shouldUsePreviousVideoAsReference(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                      AiVideoMediaAssetPo previousVideoMedia) {
        return previousVideoMedia != null
                && StringUtils.hasText(previousVideoMedia.getFileUrl())
                && shouldUsePreviousVisualReferenceForInsertHandoff(shot, previousShot);
    }

    private boolean shouldUsePreviousVisualReferenceForInsertHandoff(AiVideoShotPo shot, AiVideoShotPo previousShot) {
        if (shot == null || previousShot == null || !Objects.equals(shot.getSceneId(), previousShot.getSceneId())) {
            return false;
        }
        String transitionType = firstText(shot.getTransitionBeforeType());
        String text = String.join(" ",
                firstText(shot.getTransitionBeforeType()),
                firstText(shot.getTransitionBeforeDesc()),
                firstText(shot.getActionDesc()),
                firstText(shot.getPromptText()));
        if (containsAny(text, "不继承上一尾帧", "不使用上一尾帧", "禁止使用上一尾帧", "不要使用上一尾帧",
                "不继承上一镜尾帧", "不使用上一镜尾帧")) {
            return false;
        }
        boolean insertLike = "INSERT".equalsIgnoreCase(transitionType)
                || containsAny(text, "插入镜头", "同场景切人", "同场景道具交接");
        boolean handoffLike = containsAny(text,
                "承接上一镜", "继承上一尾帧", "上一镜道具", "道具交接", "接过", "接住", "收下",
                "递给", "递出", "交给", "传给");
        boolean relationshipLike = isRelationshipActionText(text);
        return insertLike && (handoffLike || relationshipLike);
    }

    private boolean shouldSendReferenceImageAsFirstFrame(List<AiVideoMediaAssetPo> referenceMedias,
                                                         AiVideoMediaAssetPo previousTailFrameMedia) {
        return previousTailFrameMedia != null
                && referenceMedias != null
                && referenceMedias.size() == 1
                && Objects.equals(referenceMedias.get(0).getMediaId(), previousTailFrameMedia.getMediaId());
    }

    private void addCharacterReferenceMedias(Long projectId, AiVideoShotPo shot, Map<Long, AiVideoMediaAssetPo> references) {
        for (String token : parseCharacterTokens(resolveEffectiveCharacterIds(projectId, shot))) {
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

    private void addRequiredPropReferenceMedias(Long projectId, AiVideoShotPo shot,
                                                Map<Long, AiVideoMediaAssetPo> references) {
        if (projectId == null || shot == null) {
            return;
        }
        List<String> requiredPropNames = AivideoShotRuleAnalyzer.detectRequiredPropNames(collectShotText(shot));
        if (requiredPropNames.isEmpty()) {
            return;
        }
        List<AiVideoPropPo> projectProps = selectProjectProps(projectId);
        for (String requiredName : requiredPropNames) {
            AiVideoPropPo prop = findMatchedProp(requiredName, projectProps);
            if (prop == null || prop.getLockedMediaId() == null) {
                continue;
            }
            addReferenceMedia(references, requireReferenceImage(projectId, prop.getLockedMediaId()));
        }
    }

    private AiVideoPropPo findMatchedProp(String requiredName, List<AiVideoPropPo> projectProps) {
        if (!StringUtils.hasText(requiredName) || projectProps == null) {
            return null;
        }
        for (AiVideoPropPo prop : projectProps) {
            if (prop == null || Integer.valueOf(1).equals(prop.getDelFlag())) {
                continue;
            }
            String propName = prop.getPropName();
            if (!StringUtils.hasText(propName)) {
                continue;
            }
            if (propName.contains(requiredName) || requiredName.contains(propName)) {
                return prop;
            }
        }
        return null;
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
            throw new BusinessException("参考图不存在、未归档或类型不支持，请重新选择场景图/角色图/道具图");
        }
        return media;
    }

    private boolean isVideoReferenceAsset(AiVideoMediaAssetPo media) {
        String assetType = firstText(media.getAssetType());
        if ("SHOT_TAIL_FRAME".equals(assetType)) {
            return true;
        }
        boolean supportedType = "SCENE_IMAGE".equals(assetType)
                || "CHARACTER_IMAGE".equals(assetType)
                || "PROP_IMAGE".equals(assetType);
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
                                               String referenceVideoUrl, String referenceAudioUrl,
                                               boolean referenceAudioSeedAllowed,
                                               String ratio, String resolution, int durationSec,
                                               StrategyContext strategy) {
        Map<String, String> variables = new LinkedHashMap<>();
        String referenceImageUrl = referenceImageUrls.get(0);
        AiVideoMediaAssetPo effectivePreviousTailFrameMedia =
                containsReferenceMedia(referenceMedias, previousTailFrameMedia) ? previousTailFrameMedia : null;
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
        String onscreenCharacterIds = shot != null ? shot.getCharacterIds() : null;
        String referenceCharacterIds = resolveEffectiveCharacterIds(project.getProjectId(), shot);
        String characterNames = resolveCharacterNames(project.getProjectId(), onscreenCharacterIds);
        String referenceCharacterNames = resolveCharacterNames(project.getProjectId(), referenceCharacterIds);
        String previousCharacterNames = resolveCharacterNames(project.getProjectId(),
                previousShot != null ? previousShot.getCharacterIds() : null);
        variables.put("characterNames", safeValue(characterNames));
        variables.put("referenceCharacterNames", safeValue(referenceCharacterNames));
        variables.put("relationshipReferenceRequirement",
                buildRelationshipReferenceRequirement(referenceCharacterNames, characterNames));
        variables.put("characterContinuity", buildCharacterContinuity(project.getProjectId(), referenceCharacterIds));
        variables.put("sceneContinuity", buildSceneContinuity(scene, previousShot));
        variables.put("blockingContinuityRequirement",
                buildBlockingContinuityRequirement(shot, previousShot, characterNames, previousCharacterNames));
        variables.put("audioVisualProtocol", buildAudioVisualProtocol(shot, strategy, referenceAudioUrl,
                referenceAudioSeedAllowed, characterNames));
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
        variables.put("referenceVideoUrl", safeValue(referenceVideoUrl));
        variables.put("referenceVideoProtocol", buildReferenceVideoProtocol(referenceVideoUrl));
        variables.put("referenceAudioUrl", safeValue(referenceAudioUrl));
        variables.put("referenceAudioProtocol", buildReferenceAudioProtocol(referenceAudioUrl, strategy,
                referenceAudioSeedAllowed));
        variables.put("referenceMediaIds", formatReferenceMediaIds(referenceMedias));
        variables.put("referenceAnchorSummary", buildReferenceAnchorSummary(referenceMedias));
        variables.put("referenceFrameType", buildReferenceFrameType(referenceMedias, previousTailFrameMedia));
        variables.put("previousShotNo", previousShot == null ? "无" : String.valueOf(firstInteger(previousShot.getShotNo(), 1)));
        variables.put("previousShotSummary", buildPreviousShotSummary(previousShot));
        variables.put("previousEndState", buildPreviousEndState(previousShot));
        variables.put("previousTailFrameUrl", findReferenceImageUrl(referenceMedias, referenceImageUrls, previousTailFrameMedia));
        variables.put("currentStartState", buildCurrentStartState(previousShot, effectivePreviousTailFrameMedia));
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
                                        String referenceVideoUrl, String referenceAudioUrl,
                                        boolean referenceAudioSeedAllowed, StrategyContext strategy) {
        String referenceImageUrl = referenceImageUrls.get(0);
        AiVideoMediaAssetPo effectivePreviousTailFrameMedia =
                containsReferenceMedia(referenceMedias, previousTailFrameMedia) ? previousTailFrameMedia : null;
        String referenceAnchorSummary = buildReferenceAnchorSummary(referenceMedias);
        String onscreenCharacterIds = shot != null ? shot.getCharacterIds() : null;
        String referenceCharacterIds = resolveEffectiveCharacterIds(project.getProjectId(), shot);
        String characterNames = resolveCharacterNames(project.getProjectId(), onscreenCharacterIds);
        String referenceCharacterNames = resolveCharacterNames(project.getProjectId(), referenceCharacterIds);
        String relationshipReferenceRequirement =
                buildRelationshipReferenceRequirement(referenceCharacterNames, characterNames);
        String previousCharacterNames = resolveCharacterNames(project.getProjectId(),
                previousShot != null ? previousShot.getCharacterIds() : null);
        String characterContinuity = buildCharacterContinuity(project.getProjectId(), referenceCharacterIds);
        String sceneContinuity = buildSceneContinuity(scene, previousShot);
        String blockingContinuityRequirement =
                buildBlockingContinuityRequirement(shot, previousShot, characterNames, previousCharacterNames);
        String audioVisualProtocol = buildAudioVisualProtocol(shot, strategy, referenceAudioUrl,
                referenceAudioSeedAllowed, characterNames);
        String actionBeats = buildActionBeats(shot, durationSec);
        String timingPlan = buildTimingPlan(shot, durationSec);
        String compositionRequirement = buildCompositionRequirement(shot);
        String bodyPartRequirement = buildBodyPartRequirement(shot);
        String glowRequirement = buildGlowRequirement(shot);
        return """
                # 单分镜视频模型执行版 Prompt

                参考图类型：%s。请基于参考图生成 1 个连续镜头，不要生成多镜头拼接。
                输出规格：%s，%s，约 %s 秒。主参考图地址：%s。
                实际传入参考图共 %s 张：
                %s

                ## 起始帧和连续性
                - 起始帧必须贴合主参考图：主体位置、姿态、朝向、体型、毛色/服饰、光影、天气和背景空间保持一致。
                - 上一镜头：%s；上一镜头结束状态：%s。
                - 本镜头起始状态：%s。
                - 本镜头结尾状态：%s。
                - 连续性强度：%s。若为极严格，必须同时继承上一尾帧、同场景锚点和角色锚点；缺少任一锚点时不得擅自改背景或主体。
                - 多参考图优先级：图片1作为主锚点决定起始姿态/空间连续性；场景图锁定空间、天气、光线和道具；角色图锁定身份、体型比例、脸型、服装/毛色和标志物。
                - 角色锚定图使用规则：角色图优先于角色文字描述；若文字描述与角色图冲突，必须以角色图中的造型、比例、服装、发型、猫耳/猫尾等标志物为准；不得把白底/浅灰棚拍背景带入剧情场景，不得把单主体锚定图复制成多只同款主体。
                - 参考视频规则：%s
                - 参考音频规则：%s

                ## 主体、场景、构图
                - 项目/风格：%s / %s。
                - 生成策略：%s；参考素材策略：%s；动作强度：%s；多角色策略：%s；角色造型类型：%s。
                - 角色造型硬规则：%s
                - 场景：%s，%s，%s，%s，视觉特征：%s。
                - 出场主体：%s。
                - %s
                - 角色一致性：%s。
                - 场景一致性：%s。
                - 构图要求：%s
                - 人物数量与站位连续性：%s
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
                buildReferenceFrameType(referenceMedias, previousTailFrameMedia),
                safeValue(ratio), safeValue(resolution), durationSec, referenceImageUrl,
                referenceImageUrls.size(), referenceAnchorSummary,
                previousShot == null ? "无" : "第 " + firstInteger(previousShot.getShotNo(), 1) + " 镜头，" + buildPreviousShotSummary(previousShot),
                buildPreviousEndState(previousShot),
                buildCurrentStartState(previousShot, effectivePreviousTailFrameMedia),
                buildCurrentEndState(shot),
                strategy.continuityLevel(),
                buildReferenceVideoProtocol(referenceVideoUrl),
                buildReferenceAudioProtocol(referenceAudioUrl, strategy, referenceAudioSeedAllowed),
                safeValue(project.getProjectName()), safeValue(strategy.visualStyle()),
                strategy.generationStrategy(), strategy.referenceStrategy(), strategy.actionIntensity(), strategy.multiRoleStrategy(),
                strategy.characterDesignType(), characterDesignInstruction(strategy.characterDesignType(), strategy.visualStyle()),
                safeValue(scene.getSceneName()), safeValue(scene.getTimeDesc()), safeValue(scene.getWeather()),
                safeValue(scene.getAtmosphere()), safeValue(scene.getVisualFeatures()),
                safeValue(characterNames), relationshipReferenceRequirement, characterContinuity, sceneContinuity,
                compositionRequirement, blockingContinuityRequirement, bodyPartRequirement, glowRequirement,
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

    private String appendTailFrameFirstFrameExecutionGuard(String prompt, AiVideoShotPo shot,
                                                           AiVideoShotPo previousShot,
                                                           boolean referenceImageAsFirstFrame) {
        String guard = buildTailFrameFirstFrameExecutionGuard(shot, previousShot, referenceImageAsFirstFrame);
        if (!StringUtils.hasText(guard)
                || (StringUtils.hasText(prompt) && prompt.contains("\u4E0A\u4E00\u5C3E\u5E27\u9996\u5E27\u6267\u884C\u534F\u8BAE"))) {
            return prompt;
        }
        if (!StringUtils.hasText(prompt)) {
            return guard;
        }
        return prompt.trim() + "\n\n" + guard;
    }

    private String buildTailFrameFirstFrameExecutionGuard(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                          boolean referenceImageAsFirstFrame) {
        if (!referenceImageAsFirstFrame) {
            return "";
        }
        String currentShotLabel = shot == null
                ? "\u5F53\u524D\u955C\u5934"
                : "\u7B2C" + firstInteger(shot.getShotNo(), 1) + "\u955C\u5934";
        String previousShotLabel = previousShot == null
                ? "\u4E0A\u4E00\u955C\u5934"
                : "\u7B2C" + firstInteger(previousShot.getShotNo(), 1) + "\u955C\u5934";
        String currentAction = firstText(shot != null ? shot.getActionDesc() : null,
                shot != null ? shot.getPromptText() : null,
                "\u5F53\u524D\u955C\u5934\u52A8\u4F5C");
        String currentEndState = buildCurrentEndState(shot);
        return """
                ## \u4E0A\u4E00\u5C3E\u5E27\u9996\u5E27\u6267\u884C\u534F\u8BAE(\u786C\u6027)
                - \u5F53\u524D\u751F\u6210\u76EE\u6807\u662F%s,\u4E0D\u662F%s; \u4E0A\u4E00\u5C3E\u5E27\u53EA\u5141\u8BB8\u4F5C\u4E3A\u7B2C0\u5E27/\u8D77\u59CB\u59FF\u6001\u951A\u70B9.
                - \u7981\u6B62\u628A\u4E0A\u4E00\u5C3E\u5E27\u6216\u4E0A\u4E00\u955C\u89C6\u9891\u5EF6\u957F\u6210\u6574\u6BB5; \u7981\u6B62\u91CD\u590D\u4E0A\u4E00\u955C\u52A8\u4F5C/\u53F0\u8BCD/\u6784\u56FE\u63A8\u8FDB; \u7981\u6B62\u751F\u6210\u4E0E\u4E0A\u4E00\u955C\u51E0\u4E4E\u76F8\u540C\u7684\u5019\u9009.
                - 0-0.5\u79D2: \u8D34\u5408\u4E0A\u4E00\u5C3E\u5E27\u7684\u89D2\u8272\u4F4D\u7F6E/\u671D\u5411/\u59FF\u6001/\u5149\u5F71/\u573A\u666F\u7A7A\u95F4.
                - 0.5\u79D2\u540E: \u5FC5\u987B\u5207\u5165\u5E76\u5B8C\u6210\u5F53\u524D\u955C\u52A8\u4F5C: %s.
                - \u7ED3\u5C3E\u5FC5\u987B\u505C\u5728\u5F53\u524D\u955C\u7ED3\u5C3E\u72B6\u6001: %s.
                """.formatted(currentShotLabel, previousShotLabel, safeValue(currentAction), safeValue(currentEndState)).trim();
    }

    private String buildReferenceFrameType(List<AiVideoMediaAssetPo> referenceMedias,
                                           AiVideoMediaAssetPo previousTailFrameMedia) {
        if (!containsReferenceMedia(referenceMedias, previousTailFrameMedia)) {
            return "当前场景图 + 角色锚点";
        }
        if (referenceMedias != null && referenceMedias.size() == 1) {
            return "上一分镜尾帧首帧模式";
        }
        return "多模态参考图（包含上一分镜尾帧连续性参考 + 当前场景/角色锚点）";
    }

    private String findReferenceImageUrl(List<AiVideoMediaAssetPo> referenceMedias, List<String> referenceImageUrls,
                                         AiVideoMediaAssetPo target) {
        if (target == null || referenceMedias == null || referenceImageUrls == null) {
            return "";
        }
        for (int i = 0; i < referenceMedias.size() && i < referenceImageUrls.size(); i++) {
            AiVideoMediaAssetPo media = referenceMedias.get(i);
            if (media != null && Objects.equals(media.getMediaId(), target.getMediaId())) {
                return referenceImageUrls.get(i);
            }
        }
        return "";
    }

    private boolean containsReferenceMedia(List<AiVideoMediaAssetPo> referenceMedias, AiVideoMediaAssetPo target) {
        return target != null
                && referenceMedias != null
                && referenceMedias.stream()
                .filter(Objects::nonNull)
                .anyMatch(media -> Objects.equals(media.getMediaId(), target.getMediaId()));
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

    private String buildReferenceVideoProtocol(String referenceVideoUrl) {
        if (!StringUtils.hasText(referenceVideoUrl)) {
            return "未传入参考视频；仅依据参考图、分镜文本和上一镜头摘要保持连续。";
        }
        return "已传入上一分镜完整视频作为 reference_video。必须参考视频1的同场景空间、光影、运镜节奏、角色相对位置和道具交接动作逻辑；"
                + "本镜头从视频1结尾动作之后承接，但不要复刻整段上一镜，不要把 reference_video 当成 first_frame/last_frame，不要忽略当前场景图和角色图锚点。";
    }

    private String buildReferenceAudioProtocol(String referenceAudioUrl, StrategyContext strategy) {
        return buildReferenceAudioProtocol(referenceAudioUrl, strategy, false);
    }

    private String buildReferenceAudioProtocol(String referenceAudioUrl, StrategyContext strategy,
                                               boolean referenceAudioSeedAllowed) {
        String mode = firstText(strategy != null ? strategy.audioMode() : null, DEFAULT_AUDIO_MODE)
                .toUpperCase(Locale.ROOT);
        if (!"REFERENCE_AUDIO".equals(mode)) {
            return "当前声音模式不是参考音频有声；不传入 reference_audio。";
        }
        if (referenceAudioSeedAllowed && !StringUtils.hasText(referenceAudioUrl)) {
            return "参考音频有声模式的首镜/无上一镜：允许本镜生成第一段种子音频；确认本镜视频后系统会提取该音频，供后续分镜作为 reference_audio。";
        }
        if (!StringUtils.hasText(referenceAudioUrl)) {
            return "参考音频有声模式但上一镜未归档可用音频；本次 generate_audio=false，避免模型随机改写声线。";
        }
        return "已传入上一分镜提取音频作为 reference_audio。只允许继承音色、语速、口吻、旁白/角色声线和环境声风格；"
                + "不得复读上一镜台词，不得照搬上一镜音轨内容，本镜仍必须按当前对白和旁白生成新声音。";
    }

    private String referenceAnchorRole(AiVideoMediaAssetPo media, int index) {
        String assetType = firstText(media.getAssetType());
        if ("CHARACTER_IMAGE".equals(assetType)) {
            return "reference_image/character_anchor";
        }
        if ("SCENE_IMAGE".equals(assetType)) {
            return "reference_image/scene_anchor";
        }
        if ("PROP_IMAGE".equals(assetType)) {
            return "reference_image/prop_anchor";
        }
        if ("SHOT_TAIL_FRAME".equals(assetType)) {
            return "tail_frame_anchor";
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
        if ("PROP_IMAGE".equals(assetType)) {
            return "道具锚定图，只锁定关键道具的颜色、材质、形状、尺寸、归属和交接连续性，不扩展成新角色或新背景。";
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
        String characterDesignType = firstText(dto.getCharacterDesignType(),
                strategyText(projectSetting, globalSetting, PARAM_CHARACTER_DESIGN_TYPE, DEFAULT_CHARACTER_DESIGN_TYPE));
        String visualStyle = resolveEffectiveVideoVisualStyle(
                firstText(dto.getDefaultStyle(),
                        strategyText(projectSetting, globalSetting, PARAM_DEFAULT_STYLE, DEFAULT_STYLE)),
                characterDesignType);
        return new StrategyContext(
                visualStyle,
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
                characterDesignType
        );
    }

    private String resolveEffectiveVideoVisualStyle(String visualStyle, String characterDesignType) {
        String type = firstText(characterDesignType).toUpperCase(Locale.ROOT);
        String rawType = firstText(characterDesignType);
        if ("CHIBI_FULL_BODY".equals(type)
                || rawType.contains("Q版")
                || rawType.contains("萌系")
                || rawType.contains("大头")) {
            return "Q版3D卡通少儿绘本风";
        }
        return firstText(visualStyle, DEFAULT_STYLE);
    }

    private boolean shouldGenerateAudio(String audioMode, String referenceAudioUrl) {
        return shouldGenerateAudio(audioMode, referenceAudioUrl, false);
    }

    private boolean shouldGenerateAudio(String audioMode, String referenceAudioUrl, boolean referenceAudioSeedAllowed) {
        String mode = firstText(audioMode, DEFAULT_AUDIO_MODE).toUpperCase(Locale.ROOT);
        return "NATIVE_AUDIO".equals(mode)
                || ("REFERENCE_AUDIO".equals(mode)
                && (StringUtils.hasText(referenceAudioUrl) || referenceAudioSeedAllowed));
    }

    private String buildAudioVisualProtocol(AiVideoShotPo shot, StrategyContext strategy) {
        return buildAudioVisualProtocol(shot, strategy, "");
    }

    private String buildAudioVisualProtocol(AiVideoShotPo shot, StrategyContext strategy, String referenceAudioUrl) {
        return buildAudioVisualProtocol(shot, strategy, referenceAudioUrl, false);
    }

    private String buildAudioVisualProtocol(AiVideoShotPo shot, StrategyContext strategy, String referenceAudioUrl,
                                            boolean referenceAudioSeedAllowed) {
        return buildAudioVisualProtocol(shot, strategy, referenceAudioUrl, referenceAudioSeedAllowed, "");
    }

    private String buildAudioVisualProtocol(AiVideoShotPo shot, StrategyContext strategy, String referenceAudioUrl,
                                            boolean referenceAudioSeedAllowed, String characterNames) {
        ShotAudioTracks tracks = resolveShotAudioTracks(shot, characterNames);
        String mode = firstText(strategy.audioMode(), DEFAULT_AUDIO_MODE).toUpperCase(Locale.ROOT);
        String audioRule;
        if ("NATIVE_AUDIO".equals(mode)) {
            audioRule = "声音模式=原生有声：允许视频模型生成本镜头声音，但必须严格沿用项目声线设定，不得随机改变旁白/角色音色、BGM 或音效风格。";
        } else if ("REFERENCE_AUDIO".equals(mode)) {
            audioRule = referenceAudioSeedAllowed && !StringUtils.hasText(referenceAudioUrl)
                    ? "声音模式=参考音频有声：本镜是首镜/无上一镜，允许生成第一段种子音频；必须严格按项目声线设定生成，确认后会被提取为后续 reference_audio。"
                    : StringUtils.hasText(referenceAudioUrl)
                    ? "声音模式=参考音频有声：已传入上一镜 reference_audio；必须使用它作为音色锚点，只继承音色、语速、口吻和环境声风格，不得复读上一镜台词或照搬上一镜音轨。"
                    : "声音模式=参考音频有声：当前没有可用参考音频，本次强制 generate_audio=false，不让模型自行发明新声线。";
        } else if ("POST_TTS".equals(mode)) {
            audioRule = "声音模式=后期 TTS：本阶段只生成画面，不生成配音、BGM 或音效；对白和旁白只作为后期配音脚本保留。";
        } else {
            audioRule = "声音模式=静音：本阶段只生成画面，generate_audio=false，不生成、不替换、不改变配音、旁白声线、BGM 或音效。";
        }
        return audioRule
                + " 对白（说出口/口型同步）：" + tracks.spokenDialogue()
                + "；旁白/画外音（可发声/不口型）：" + tracks.audibleVoiceOver()
                + "；心声/心理活动（不发声/不口型，仅画面表现）：" + tracks.internalThought()
                + "。只有对白允许角色张嘴和口型同步；旁白/画外音可发声但画面中的角色不张嘴、不做口型。"
                + "心声和心理画面默认不可朗读，必须用眼神、呼吸、姿态、手部迟疑、环境空镜或画面隐喻承接情绪；"
                + "只有明确写成“角色名（内心独白）”或“角色名（旁白）”时，才可作为可发声旁白。"
                + "如果旁白以“角色名（画外音）”或“角色名（旁白）”开头，必须沿用该角色声线，即使画面切到其他角色也不能换声线；"
                + "如果旁白以“旁白：”开头，必须使用统一旁白声线。低声报数、低声说、耳语、小声说、念出、读出属于说出口的对白，不要放到旁白轨。"
                + "海报文字、账本文字、屏幕字卡只作为画面可见内容，不要自动朗读。"
                + "分镜之间保持同一旁白/配音口吻、语速、性别/年龄感和情绪连续，禁止声线突变。";
    }

    private ShotAudioTracks resolveShotAudioTracks(AiVideoShotPo shot, String characterNames) {
        List<String> spoken = new ArrayList<>();
        List<String> audible = new ArrayList<>();
        List<String> thought = new ArrayList<>();
        String dialogue = firstText(shot != null ? shot.getDialogue() : null);
        if (StringUtils.hasText(dialogue)) {
            spoken.add(normalizeDialogueSpeaker(dialogue, characterNames));
        }
        String voiceOver = firstText(shot != null ? shot.getVoiceOver() : null);
        if (StringUtils.hasText(voiceOver)) {
            if (isSpokenLikeVoiceOver(voiceOver)) {
                spoken.add(voiceOver.trim());
            } else if (isInternalThoughtVoiceOver(voiceOver)) {
                thought.add(voiceOver.trim());
            } else {
                audible.add(voiceOver.trim());
            }
        }
        return new ShotAudioTracks(
                firstText(String.join("；", spoken), "无"),
                firstText(String.join("；", audible), "无"),
                firstText(String.join("；", thought), "无"));
    }

    private String normalizeDialogueSpeaker(String dialogue, String characterNames) {
        String text = dialogue.trim();
        if (containsAny(text, "：", ":")) {
            return text;
        }
        List<String> names = splitCharacterNames(characterNames);
        return names.size() == 1 ? names.get(0) + "：" + text : text;
    }

    private List<String> splitCharacterNames(String characterNames) {
        if (!StringUtils.hasText(characterNames) || "未填写".equals(characterNames.trim())) {
            return List.of();
        }
        String normalized = characterNames.replace('，', '、')
                .replace(',', '、')
                .replace(' ', '、');
        return Arrays.stream(normalized.split("、"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(name -> !"未填写".equals(name))
                .toList();
    }

    private boolean isSpokenLikeVoiceOver(String text) {
        if (!StringUtils.hasText(text) || containsAny(text, "（画外音）", "(画外音)", "（旁白）", "(旁白)", "旁白：")) {
            return false;
        }
        return containsAny(text,
                "（低声报数）", "(低声报数)", "（低声）", "(低声)", "（小声）", "(小声)",
                "（耳语）", "(耳语)", "低声说", "低声报数", "小声说", "耳语",
                "念出", "读出", "说：", "问：", "回答：", "喊：");
    }

    private boolean isInternalThoughtVoiceOver(String text) {
        if (!StringUtils.hasText(text) || containsAny(text, "（内心独白）", "(内心独白)")) {
            return false;
        }
        if (containsAny(text, "（心声）", "(心声)", "心声：", "心理活动")) {
            return true;
        }
        return containsAny(text, "脑海里", "心里", "想到", "意识到", "想象", "回忆", "闪过", "触感", "念头")
                && !containsAny(text, "（画外音）", "(画外音)", "（旁白）", "(旁白)", "旁白：");
    }

    private record ShotAudioTracks(String spokenDialogue, String audibleVoiceOver, String internalThought) {
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
            return "首镜头从当前参考场景图或角色图的静态起始姿态开始，主体已在位；不得从画外走入、跑入、走出来或新增入场动作。";
        }
        if (previousTailFrameMedia != null) {
            return "必须从上一镜头尾帧参考图开始：主体位置、姿态、朝向、光影和环境保持一致，保持0-0.5秒后再执行本镜头动作。";
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
        String action1 = beats.isEmpty() ? "主体保持起始姿态0-0.5秒后执行本镜头主动作，动作缓慢清晰" : beats.get(0);
        String action2 = beats.size() > 1 ? beats.get(1) : firstText(shot != null ? shot.getEmotion() : null, "用眼神、呼吸或姿态表现反应");
        String action3 = beats.size() > 2 ? beats.get(2) : findEndStateBeat(extractActionBeats(firstText(
                shot != null ? shot.getActionDesc() : null, shot != null ? shot.getPromptText() : null)));
        String endState = firstText(action3, "停在本镜头自然结尾状态，便于下一镜头继续");
        if (durationSec <= 5) {
            return "- 前段：从上一尾帧或参考图的既有姿态开始，保持0-0.5秒后执行主动作：" + action1 + "。\n"
                    + "- 中段：用低幅度表情/呼吸/姿态完成反应：" + action2 + "。\n"
                    + "- 末段：停在结尾状态：" + endState + "。";
        }
        if (durationSec <= 6) {
            return "- 前段：从上一尾帧或参考图的既有姿态开始，保持0-0.5秒后执行动作一：" + action1 + "。\n"
                    + "- 中段：连续衔接动作二或反应：" + action2 + "。\n"
                    + "- 末段：停在结尾状态：" + endState + "。";
        }
        return "- 前段：从上一尾帧或参考图的既有姿态开始，保持0-0.5秒后执行动作一：" + action1 + "。\n"
                + "- 中段：连续衔接动作二：" + action2 + "。\n"
                + "- 末段：完成动作三或明确结尾状态：" + endState + "。";
    }

    private String buildCompositionRequirement(AiVideoShotPo shot) {
        String text = collectShotText(shot);
        String relationshipRequirement = buildRelationshipCompositionRequirement(text);
        List<String> parts = detectTargetParts(text);
        if (StringUtils.hasText(relationshipRequirement)) {
            if (parts.isEmpty()) {
                return relationshipRequirement;
            }
            return relationshipRequirement + " 同时必须使用半身或全身构图，镜头内持续露出"
                    + String.join("、", parts) + "，不要只拍脸部特写。";
        }
        if (parts.isEmpty()) {
            return "保持参考图主体清晰，优先中景或近景，避免无故切到纯脸部大特写导致动作丢失。";
        }
        return "必须使用半身或全身构图，镜头内持续露出" + String.join("、", parts)
                + "，不要只拍脸部特写。";
    }

    private void validateShotRuntimeContinuity(Long projectId, AiVideoShotPo shot, AiVideoShotPo previousShot) {
        if (shot == null || previousShot == null) {
            return;
        }
        String text = collectShotText(shot);
        String previousText = collectShotText(previousShot);
        validateRuntimePropHandoff(text);
        if (!sameSceneWithoutHardBreak(shot, previousShot)) {
            return;
        }
        validateRuntimeCharacterPresence(projectId, shot, previousShot, text);
        validateRuntimeFacingContinuity(text, previousText);
    }

    private void validateShotActionBudgetAndProps(Long projectId, AiVideoShotPo shot, int durationSec) {
        if (shot == null) {
            return;
        }
        String text = collectShotText(shot);
        AivideoShotRuleAnalyzer.validateActionBudgetOrThrow(
                shot.getShotNo(), durationSec, shot.getActionDesc(), shot.getPromptText());
        AivideoShotRuleAnalyzer.validateRequiredPropsOrThrow(text, selectProjectProps(projectId), true);
    }

    private void validateShotOnscreenCharacterCount(Long projectId, AiVideoShotPo shot) {
        if (shot == null) {
            return;
        }
        String text = collectShotText(shot);
        int expectedCount = inferExpectedOnscreenCount(text, selectProjectCharacters(projectId).size());
        if (expectedCount <= 0) {
            return;
        }
        List<String> boundTokens = parseCharacterTokens(shot.getCharacterIds());
        if (boundTokens.size() >= expectedCount) {
            return;
        }
        String names = resolveCharacterNames(projectId, shot.getCharacterIds());
        throw new BusinessException("画内角色绑定不足：第" + shot.getShotNo()
                + "镜文案推断至少需要" + expectedCount + "个画内角色，但当前只绑定了"
                + boundTokens.size() + "个（" + (StringUtils.hasText(names) ? names : "未绑定")
                + "）。请回资产分镜补齐全部角色，不要让视频模型根据“其余三人/全员/四人”自行脑补。");
    }

    private int inferExpectedOnscreenCount(String text, int allCharacterCount) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int expected = 0;
        Matcher explicitMatcher = EXPLICIT_ONSCREEN_COUNT_PATTERN.matcher(text);
        while (explicitMatcher.find()) {
            expected = Math.max(expected, parseChineseOrArabicNumber(explicitMatcher.group(1)));
        }
        Matcher remainingMatcher = REMAINING_PEOPLE_PATTERN.matcher(text);
        while (remainingMatcher.find()) {
            int remaining = parseChineseOrArabicNumber(remainingMatcher.group(1));
            if (remaining > 0) {
                expected = Math.max(expected, remaining + 1);
            }
        }
        Matcher groupMatcher = GROUP_PEOPLE_PATTERN.matcher(text);
        while (groupMatcher.find()) {
            expected = Math.max(expected, parseChineseOrArabicNumber(groupMatcher.group(1)));
        }
        if (containsAny(text, "全员", "所有角色", "全部角色", "全体角色") && allCharacterCount > 0) {
            expected = Math.max(expected, allCharacterCount);
        }
        return expected;
    }

    private int parseChineseOrArabicNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d+")) {
            return Integer.parseInt(trimmed);
        }
        return switch (trimmed) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 0;
        };
    }

    private List<AiVideoPropPo> selectProjectProps(Long projectId) {
        if (projectId == null || propMapper == null) {
            return List.of();
        }
        return propMapper.selectList(new LambdaQueryWrapper<AiVideoPropPo>()
                .eq(AiVideoPropPo::getProjectId, projectId)
                .eq(AiVideoPropPo::getDelFlag, DEL_FLAG_NORMAL));
    }

    private void validateRuntimePropHandoff(String text) {
        if (!hasReceiveLikeAction(text)) {
            return;
        }
        if (hasExplicitHandoffSource(text)) {
            return;
        }
        throw new BusinessException("道具交接描述不完整：本镜存在接过/收下/交接动作，必须写清谁递给谁、具体道具、从画面哪边来、最后谁拿着；不要只写“接过”或“展示给画外”。");
    }

    private boolean hasReceiveLikeAction(String text) {
        return containsAny(text, "接过", "接住", "收下", "拿到", "道具交接", "递过来的", "传过来的");
    }

    private boolean hasExplicitHandoffSource(String text) {
        boolean source = containsAny(text,
                "递给", "交给", "传给", "拿给", "递向", "从", "手里", "手中",
                "画面左侧", "画面右侧", "左侧", "右侧", "左手", "右手");
        boolean receiver = containsAny(text, "接过", "接住", "收下", "拿到", "递给", "交给", "传给", "拿给");
        return source && receiver;
    }

    private void validateRuntimeCharacterPresence(Long projectId, AiVideoShotPo shot,
                                                  AiVideoShotPo previousShot, String text) {
        List<String> previousNames = parseCharacterNameList(
                resolveCharacterNames(projectId, resolveEffectiveCharacterIds(projectId, previousShot)));
        List<String> currentNames = parseCharacterNameList(
                resolveCharacterNames(projectId, resolveEffectiveCharacterIds(projectId, shot)));
        if (previousNames.isEmpty()) {
            return;
        }
        List<String> missingNames = previousNames.stream()
                .filter(name -> !currentNames.contains(name))
                .filter(name -> !hasCharacterExitOrCropExplanation(text, name))
                .toList();
        if (missingNames.isEmpty()) {
            return;
        }
        throw new BusinessException("上一镜角色疑似无说明消失：" + String.join("、", missingNames)
                + "；当前镜头与上一镜仍属同场景/非硬切，但上一镜画内角色未在本镜继续出现，也没有针对该角色写明离场、画外、单人反应或裁切说明；请补充衔接说明或修正本镜画内人物。");
    }

    private boolean hasCharacterExitOrCropExplanation(String text, String characterName) {
        if (isOffscreenCharacterMention(text, characterName)) {
            return true;
        }
        return containsAny(text,
                "单人反应", "只拍", "只露手", "只露肩", "只露背影", "特写裁切", "裁切",
                "离场", "离开", "退出画面", "退出画外", "不出现", "不入画", "画外");
    }

    private void validateRuntimeFacingContinuity(String text, String previousText) {
        if (!containsAny(previousText, "背对镜头", "背对", "背影", "侧身", "侧面对")) {
            return;
        }
        if (!containsAny(text, "正面对着镜头", "面对镜头", "正对镜头", "对着镜头", "正面说话")) {
            return;
        }
        if (containsAny(text,
                "转身", "转过身", "回头", "回过头", "反打", "换轴", "镜头绕到",
                "切到正面", "重新建立", "重新调度", "切场", "明确切场", "硬切")) {
            return;
        }
        throw new BusinessException("朝向衔接不完整：上一镜角色背对/侧身，本镜变成正面对镜头说话，必须写清转身、反打、换轴、镜头绕到正面或明确切场。");
    }

    private String buildBlockingContinuityRequirement(AiVideoShotPo shot, AiVideoShotPo previousShot,
                                                       String characterNames, String previousCharacterNames) {
        String text = collectShotText(shot);
        String previousText = collectShotText(previousShot);
        List<String> currentNames = parseCharacterNameList(characterNames);
        List<String> previousNames = parseCharacterNameList(previousCharacterNames);
        List<String> allNames = mergeNames(currentNames, previousNames);
        String leftName = firstText(findSideCharacter(text, allNames, "左"), findSideCharacter(previousText, allNames, "左"));
        String rightName = firstText(findSideCharacter(text, allNames, "右"), findSideCharacter(previousText, allNames, "右"));
        StringBuilder builder = new StringBuilder();

        if (!currentNames.isEmpty()) {
            builder.append("当前镜头在场角色：").append(currentNames.size()).append("人（")
                    .append(String.join("、", currentNames)).append("）。");
            builder.append("画内必须出现：").append(String.join("、", currentNames)).append("。")
                    .append("未列入画内角色的其他角色不得自动出现；除非本镜 actionDesc、transitionBeforeDesc、dialogue、voiceOver 或 promptText 明确写明画外、画外音、远景背景人群、离场或不出现，否则不要新增人物。");
            builder.append("禁止用“同伴/对方/两人/旁边的人/画外同伴/画外两人/她/他”替代角色姓名；")
                    .append("凡是对视、看向、回应、靠近、交接都必须点名角色名及画内/画外状态。");
            if (currentNames.size() >= 2) {
                builder.append("本镜是多人画面时，禁止只出现单个角色，禁止把任一画内角色裁出画外或用画外方向、眼神、对白代替。");
            }
        } else {
            builder.append("当前镜头必须保持分镜指定主体清楚可见。");
        }

        if (StringUtils.hasText(leftName) || StringUtils.hasText(rightName)) {
            builder.append("屏幕站位锁定：");
            if (StringUtils.hasText(leftName)) {
                builder.append(leftName).append("固定在画面左侧；");
            }
            if (StringUtils.hasText(rightName)) {
                builder.append(rightName).append("固定在画面右侧；");
            }
            builder.append("禁止左右互换，禁止随镜头推近或反打自动调换 screen-left/screen-right。");
        } else if (previousShot != null && sameSceneWithoutHardBreak(shot, previousShot)) {
            builder.append("若上一镜已建立屏幕左/右关系，必须沿用上一镜的 screen-left/screen-right；")
                    .append("未写明换轴、反打或重新调度时禁止随机交换站位。");
        }

        if (previousShot != null && sameSceneWithoutHardBreak(shot, previousShot) && !previousNames.isEmpty()) {
            builder.append("上一镜仍在场角色：").append(String.join("、", previousNames))
                    .append("；同场景/同剪辑组内，除非本镜 actionDesc 或 transitionBeforeDesc 明确写离场、退出画外、只露手/肩/背影或单人反应裁切，")
                    .append("否则这些角色必须继续在画面内或以可见局部存在，禁止上一镜仍在场角色无说明消失。");
        }

        if (isRelationshipActionText(text)) {
            builder.append("关系动作同框：靠近、看向、递给、接过、对话、并肩等动作必须让动作发起者和目标角色同时可见；")
                    .append("镜头推近的是二者关系，不是单独拍某个角色脸部。");
        }
        return builder.toString();
    }

    private String buildRelationshipCompositionRequirement(String text) {
        if (!isRelationshipActionText(text)) {
            return "";
        }
        List<String> targetNames = detectRelationshipTargetNames(text);
        String target = targetNames.isEmpty() ? "被靠近/看向/交接的目标角色" : String.join("、", targetNames);
        return "双角色同框：动作发起者和" + target
                + "必须同时出现在画面中，保持二者空间距离和朝向关系清楚；镜头推近的是两者关系，不是单独拍某一个角色脸部。"
                + " 禁止只出现单个角色，禁止把另一名角色放到画外，禁止只用眼神或画外方向代替目标角色。";
    }

    private String buildRelationshipReferenceRequirement(String referenceCharacterNames, String onscreenCharacterNames) {
        List<String> referenceNames = parseCharacterNameList(referenceCharacterNames);
        List<String> onscreenNames = parseCharacterNameList(onscreenCharacterNames);
        List<String> relationshipNames = referenceNames.stream()
                .filter(name -> !onscreenNames.contains(name))
                .distinct()
                .toList();
        if (relationshipNames.isEmpty()) {
            return "关系参考角色：无。";
        }
        return "关系参考角色：" + String.join("、", relationshipNames)
                + "。这些角色只作为身份、外观、声线或关系参考锚点；除非已写入“画内必须出现”，不得自动抢占主体或替代本镜画内角色。";
    }

    private List<String> parseCharacterNameList(String names) {
        if (!StringUtils.hasText(names)) {
            return List.of();
        }
        return Arrays.stream(names.trim().replace("[", "").replace("]", "").replace("\"", "")
                        .split("[,，、\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> mergeNames(List<String> first, List<String> second) {
        Set<String> names = new LinkedHashSet<>();
        if (first != null) {
            names.addAll(first);
        }
        if (second != null) {
            names.addAll(second);
        }
        return new ArrayList<>(names);
    }

    private String findSideCharacter(String text, List<String> characterNames, String side) {
        if (!StringUtils.hasText(text) || characterNames == null || characterNames.isEmpty()) {
            return "";
        }
        for (String name : characterNames) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String trimmed = name.trim();
            if (containsAny(text,
                    side + "侧=" + trimmed,
                    side + "=" + trimmed,
                    "画面" + side + "侧=" + trimmed,
                    trimmed + "在画面" + side + "侧",
                    trimmed + "位于画面" + side + "侧",
                    trimmed + "固定在画面" + side + "侧",
                    trimmed + "在" + side + "侧",
                    "画面" + side + "侧的" + trimmed,
                    side + "侧的" + trimmed)) {
                return trimmed;
            }
        }
        return "";
    }

    private boolean sameSceneWithoutHardBreak(AiVideoShotPo shot, AiVideoShotPo previousShot) {
        if (shot == null || previousShot == null || !Objects.equals(shot.getSceneId(), previousShot.getSceneId())) {
            return false;
        }
        String transition = firstText(shot.getTransitionBeforeType()).trim().toUpperCase(Locale.ROOT);
        return !List.of("SCENE_CUT", "TIME_JUMP", "MONTAGE").contains(transition);
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
                + firstText(shot.getDialogue()) + "\n"
                + firstText(shot.getTransitionBeforeDesc());
    }

    private String resolveEffectiveCharacterIds(Long projectId, AiVideoShotPo shot) {
        Set<String> ids = new LinkedHashSet<>(parseCharacterTokens(shot != null ? shot.getCharacterIds() : null));
        addMentionedRelationCharacterIds(projectId, collectShotText(shot), ids);
        return String.join(",", ids);
    }

    private void addMentionedRelationCharacterIds(Long projectId, String text, Set<String> ids) {
        if (projectId == null || characterMapper == null || ids == null || !isRelationshipActionText(text)) {
            return;
        }
        for (AiVideoCharacterPo character : selectProjectCharacters(projectId)) {
            if (character == null || character.getCharacterId() == null
                    || !StringUtils.hasText(character.getCharacterName())
                    || !textMentionsCharacter(text, character.getCharacterName())) {
                continue;
            }
            String idText = String.valueOf(character.getCharacterId());
            String name = character.getCharacterName().trim();
            if (isOffscreenCharacterMention(text, name)) {
                continue;
            }
            if (!ids.contains(idText) && !ids.contains(name)) {
                ids.add(idText);
            }
        }
    }

    private List<AiVideoCharacterPo> selectProjectCharacters(Long projectId) {
        if (projectId == null || characterMapper == null) {
            return List.of();
        }
        List<AiVideoCharacterPo> characters = characterMapper.selectList(new LambdaQueryWrapper<AiVideoCharacterPo>()
                .eq(AiVideoCharacterPo::getProjectId, projectId)
                .eq(AiVideoCharacterPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByAsc(AiVideoCharacterPo::getSortOrder));
        return characters == null ? List.of() : characters;
    }

    private boolean isRelationshipActionText(String text) {
        return containsAny(text, "靠近", "凑近", "走向", "看向", "望向", "旁边", "身边",
                "递给", "递向", "递出", "交给", "传给", "拿给", "接过", "接住", "收下",
                "对话", "同框", "两人", "三人", "多人", "一起", "并肩", "互动");
    }

    private boolean isOffscreenCharacterMention(String text, String characterName) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(characterName)) {
            return false;
        }
        for (String alias : characterNameAliases(characterName)) {
            int index = text.indexOf(alias);
            while (index >= 0) {
                String context = text.substring(Math.max(0, index - 16), Math.min(text.length(), index + alias.length() + 16));
                if (containsAny(context, "画外", "画外音", "旁白", "不出现", "不入画", "镜外", "离场", "离开",
                        "退出画面", "退出画外", "只闻其声", "声音传来", "脑海", "心里", "内心独白",
                        "单人反应", "只拍", "只露手", "只露肩", "只露背影", "特写裁切")) {
                    return true;
                }
                index = text.indexOf(alias, index + alias.length());
            }
        }
        return false;
    }

    private boolean textMentionsCharacter(String text, String characterName) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(characterName)) {
            return false;
        }
        for (String alias : characterNameAliases(characterName)) {
            if (text.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private List<String> characterNameAliases(String characterName) {
        if (!StringUtils.hasText(characterName)) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addCharacterAlias(aliases, characterName);
        addCharacterAlias(aliases, characterName.replaceAll("[（(][^（）()]*[）)]", ""));
        Matcher matcher = Pattern.compile("[（(]([^（）()]+)[）)]").matcher(characterName);
        while (matcher.find()) {
            addCharacterAlias(aliases, matcher.group(1));
        }
        return new ArrayList<>(aliases);
    }

    private void addCharacterAlias(Set<String> aliases, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (StringUtils.hasText(normalized)) {
            aliases.add(normalized);
        }
    }

    private List<String> detectRelationshipTargetNames(String text) {
        if (!StringUtils.hasText(text) || !isRelationshipActionText(text)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String cue : List.of("旁边的", "身边的", "靠近", "凑近", "靠向", "走向",
                "看向", "望向", "递给", "交给", "传给", "拿给", "递向", "面对")) {
            String name = readNameAfterCue(text, cue);
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private String readNameAfterCue(String text, String cue) {
        int index = text.indexOf(cue);
        if (index < 0) {
            return "";
        }
        String value = text.substring(index + cue.length()).trim();
        for (String prefix : List.of("旁边的", "身边的", "旁边", "身边", "画面中的", "画面里", "的", "向", "到")) {
            while (value.startsWith(prefix)) {
                value = value.substring(prefix.length()).trim();
            }
        }
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < value.length() && name.length() < 8; i++) {
            char ch = value.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN
                    || Character.isLetterOrDigit(ch) || ch == '_') {
                name.append(ch);
                continue;
            }
            break;
        }
        return name.toString();
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

    private String truncateForPrompt(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "无";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String truncateForError(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "无";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
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
            String referenceVideoUrl,
            String referenceAudioUrl,
            List<AiVideoMediaAssetPo> referenceMedias,
            boolean referenceImageAsFirstFrame,
            boolean referenceAudioSeedAllowed,
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

    @lombok.Data
    private static final class ShotScriptOptimizePayload {
        private Integer durationSec;
        private String shotType;
        private String cameraPosition;
        private String cameraMovement;
        private String transitionBeforeType;
        private String transitionBeforeDesc;
        private String actionDesc;
        private String dialogue;
        private String voiceOver;
        private String emotion;
        private String bgmCue;
        private String sfxCues;
        private String promptText;
        private String characterIds;
        private String referenceMediaIds;
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
