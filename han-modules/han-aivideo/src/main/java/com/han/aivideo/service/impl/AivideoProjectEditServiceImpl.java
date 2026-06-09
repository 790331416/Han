package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.aivideo.domain.dto.AivideoProjectEditGenerateDto;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.vo.AivideoProjectEditClipVo;
import com.han.aivideo.domain.vo.AivideoProjectEditPreflightVo;
import com.han.aivideo.enums.AivideoTaskStatus;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import com.han.aivideo.service.AivideoDirectEditProvider;
import com.han.aivideo.service.IAivideoProjectEditService;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Project-level final video editing orchestration.
 */
@Service
public class AivideoProjectEditServiceImpl extends AivideoServiceSupport implements IAivideoProjectEditService {

    private static final String CONFIRM_APPROVED = "APPROVED";
    private static final String ASSET_SHOT_VIDEO = "SHOT_VIDEO";
    private static final String ASSET_PROJECT_EDIT_VIDEO = "PROJECT_EDIT_VIDEO";
    private static final String TASK_PROJECT_EDIT_VIDEO = "PROJECT_EDIT_VIDEO";
    private static final String BIZ_SHOT = "SHOT";
    private static final String BIZ_PROJECT = "PROJECT";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_SELECTED = "SELECTED";
    private static final DateTimeFormatter EDIT_OUTPUT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AiVideoProjectMapper projectMapper;
    private final AiVideoProjectSettingMapper settingMapper;
    private final AiVideoShotMapper shotMapper;
    private final AiVideoMediaAssetMapper mediaAssetMapper;
    private final AiVideoGenerationTaskMapper taskMapper;
    private final AivideoDirectEditProvider directEditProvider;
    private final String publicOrigin;

    public AivideoProjectEditServiceImpl(AiVideoProjectMapper projectMapper,
                                         AiVideoProjectSettingMapper settingMapper,
                                         AiVideoShotMapper shotMapper,
                                         AiVideoMediaAssetMapper mediaAssetMapper,
                                         AiVideoGenerationTaskMapper taskMapper,
                                         AivideoDirectEditProvider directEditProvider,
                                         @Value("${han.aivideo.media.public-file-origin:${aivideo.public-origin:${HAN_AIVIDEO_MEDIA_PUBLIC_FILE_ORIGIN:${AIVIDEO_PUBLIC_ORIGIN:}}}}") String publicOrigin) {
        this.projectMapper = projectMapper;
        this.settingMapper = settingMapper;
        this.shotMapper = shotMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.taskMapper = taskMapper;
        this.directEditProvider = directEditProvider;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public AivideoProjectEditPreflightVo previewProjectEdit(Long projectId) {
        AiVideoProjectPo project = requireProject(projectId);
        return buildPreflight(project);
    }

    @Override
    public AiVideoGenerationTaskPo submitProjectEdit(AivideoProjectEditGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AivideoProjectEditPreflightVo preflight = buildPreflight(project);
        if (!Boolean.TRUE.equals(preflight.getReady())) {
            throw new BusinessException("剪辑预检未通过：" + String.join("；", preflight.getErrors()));
        }
        AivideoDirectEditProvider provider = requireProvider();
        String videoName = firstText(dto.getVideoName(), project.getProjectName() + " 成片");
        boolean includeAudio = dto.getIncludeAudio() == null || Boolean.TRUE.equals(dto.getIncludeAudio());
        int priority = dto.getPriority() == null ? 0 : dto.getPriority();
        String outputFileName = buildEditOutputFileName(project, nextEditOutputSuffix());
        String editParam = buildDirectEditParam(project, preflight, videoName, includeAudio, provider.uploader(), outputFileName);

        AiVideoGenerationTaskPo task = createEditTask(project, editParam, videoName, priority, preflight);
        try {
            AivideoDirectEditProvider.SubmitResult result =
                    provider.submit(editParam, priority, "projectId=" + project.getProjectId() + ";taskId=" + task.getTaskId());
            if (result == null || !StringUtils.hasText(result.reqId())) {
                throw new BusinessException("火山剪辑任务未返回 ReqId");
            }
            task.setProviderTaskId(result.reqId());
            task.setProgress(1);
            task.setTaskStatus(AivideoTaskStatus.RUNNING.name());
            fillUpdateAudit(task);
            taskMapper.updateById(task);
            return task;
        } catch (RuntimeException exception) {
            markTaskFailed(task, exception.getMessage());
            throw exception;
        }
    }

    @Override
    public AiVideoGenerationTaskPo pollProjectEditTask(Long projectId, Long taskId) {
        AiVideoProjectPo project = requireProject(projectId);
        AiVideoGenerationTaskPo task = requireEditTask(project, taskId);
        if (!StringUtils.hasText(task.getProviderTaskId())) {
            return task;
        }
        AivideoDirectEditProvider provider = requireProvider();
        try {
            int progress = provider.progress(task.getProviderTaskId());
            task.setProgress(Math.max(1, Math.min(100, progress)));
            AivideoDirectEditProvider.EditResult result = provider.result(task.getProviderTaskId());
            if (result != null && isEditSuccess(result.status())) {
                task.setProgress(100);
                task.setTaskStatus(AivideoTaskStatus.SUCCESS.name());
                task.setFinishedTime(now());
                upsertEditedVideoAsset(project, task, result);
            } else if (result != null && isEditFailed(result.status())) {
                task.setTaskStatus(AivideoTaskStatus.FAILED.name());
                task.setErrorMessage(firstText(result.message(), "火山剪辑任务失败"));
                task.setFinishedTime(now());
            } else {
                task.setTaskStatus(AivideoTaskStatus.RUNNING.name());
            }
            fillUpdateAudit(task);
            taskMapper.updateById(task);
            return task;
        } catch (RuntimeException exception) {
            markTaskFailed(task, exception.getMessage());
            throw exception;
        }
    }

    @Override
    public List<AiVideoGenerationTaskPo> listProjectEditTasks(Long projectId) {
        requireProject(projectId);
        return taskMapper.selectList(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, projectId)
                .eq(AiVideoGenerationTaskPo::getTaskType, TASK_PROJECT_EDIT_VIDEO)
                .eq(AiVideoGenerationTaskPo::getBizType, BIZ_PROJECT)
                .eq(AiVideoGenerationTaskPo::getBizId, projectId)
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoGenerationTaskPo::getUpdateTime)
                .orderByDesc(AiVideoGenerationTaskPo::getTaskId));
    }

    String buildDirectEditParamForTest(Long projectId, String videoName, boolean includeAudio) {
        AiVideoProjectPo project = requireProject(projectId);
        AivideoProjectEditPreflightVo preflight = buildPreflight(project);
        if (!Boolean.TRUE.equals(preflight.getReady())) {
            throw new BusinessException("剪辑预检未通过：" + String.join("；", preflight.getErrors()));
        }
        return buildDirectEditParam(project, preflight, videoName, includeAudio, "aivideo-edit",
                buildEditOutputFileName(project, "test-output"));
    }

    private AivideoProjectEditPreflightVo buildPreflight(AiVideoProjectPo project) {
        List<AiVideoShotPo> shots = selectApprovedShots(project.getProjectId());
        List<AiVideoMediaAssetPo> selectedVideos = selectSelectedShotVideos(project.getProjectId());
        Map<Long, AiVideoMediaAssetPo> selectedByMediaId = selectedVideos.stream()
                .filter(media -> media.getMediaId() != null)
                .collect(Collectors.toMap(AiVideoMediaAssetPo::getMediaId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, AiVideoMediaAssetPo> selectedByShotId = selectedVideos.stream()
                .filter(media -> media.getBizId() != null)
                .collect(Collectors.toMap(AiVideoMediaAssetPo::getBizId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        AivideoProjectEditPreflightVo preflight = new AivideoProjectEditPreflightVo();
        if (shots.isEmpty()) {
            preflight.getErrors().add("暂无已确认分镜，不能生成剪辑成片");
            preflight.setMissingShotCount(0);
            return preflight;
        }

        int cursorMs = 0;
        int missing = 0;
        Integer previousGroup = null;
        for (AiVideoShotPo shot : shots) {
            AiVideoMediaAssetPo media = selectedByShotId.get(shot.getShotId());
            if (media == null && shot.getVideoMediaId() != null) {
                media = selectedByMediaId.get(shot.getVideoMediaId());
            }
            if (media == null || !StringUtils.hasText(media.getFileUrl())) {
                missing++;
                preflight.getErrors().add("第" + firstInteger(shot.getShotNo(), 0) + "镜缺少已选视频，需先生成并选择候选视频");
                continue;
            }
            int durationSec = Math.max(1, firstInteger(shot.getDurationSec(), 5));
            AivideoProjectEditClipVo clip = new AivideoProjectEditClipVo();
            clip.setShotId(shot.getShotId());
            clip.setEpisodeNo(firstInteger(shot.getEpisodeNo(), 1));
            clip.setShotNo(firstInteger(shot.getShotNo(), 0));
            clip.setDurationSec(durationSec);
            clip.setStitchGroupNo(shot.getStitchGroupNo());
            clip.setTransitionBeforeType(firstText(shot.getTransitionBeforeType()));
            clip.setTransitionBeforeDesc(firstText(shot.getTransitionBeforeDesc()));
            clip.setTransitionEffect(firstText(shot.getTransitionEffect(), "hard_cut"));
            clip.setActionDesc(firstText(shot.getActionDesc(), shot.getPromptText()));
            clip.setVideoMediaId(media.getMediaId());
            String videoUrl = toPublicUrl(media.getFileUrl());
            if (!isSupportedDirectEditSourceUrl(videoUrl)) {
                preflight.getErrors().add("第" + firstInteger(shot.getShotNo(), 0)
                        + "镜已选视频不是公网可访问地址，请配置 han.aivideo.media.public-file-origin 或重新选择公网视频");
                continue;
            }
            clip.setVideoUrl(videoUrl);
            clip.setTimelineStartMs(cursorMs);
            cursorMs += durationSec * 1000;
            clip.setTimelineEndMs(cursorMs);
            if (previousGroup != null && !Objects.equals(previousGroup, shot.getStitchGroupNo())) {
                preflight.getWarnings().add("第" + clip.getShotNo() + "镜前发生剪辑组切换，可在后期组间加转场：" + clip.getTransitionBeforeType());
            }
            previousGroup = shot.getStitchGroupNo();
            preflight.getClips().add(clip);
        }
        preflight.setClipCount(preflight.getClips().size());
        preflight.setMissingShotCount(missing);
        preflight.setTotalDurationSec(cursorMs / 1000);
        preflight.setReady(preflight.getErrors().isEmpty() && !preflight.getClips().isEmpty());
        return preflight;
    }

    private List<AiVideoShotPo> selectApprovedShots(Long projectId) {
        List<AiVideoShotPo> shots = shotMapper.selectList(new LambdaQueryWrapper<AiVideoShotPo>()
                .eq(AiVideoShotPo::getProjectId, projectId)
                .eq(AiVideoShotPo::getConfirmStatus, CONFIRM_APPROVED)
                .eq(AiVideoShotPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByAsc(AiVideoShotPo::getEpisodeNo)
                .orderByAsc(AiVideoShotPo::getShotNo)
                .orderByAsc(AiVideoShotPo::getSortOrder));
        if (shots == null) {
            return new ArrayList<>();
        }
        List<AiVideoShotPo> sortedShots = new ArrayList<>(shots);
        sortedShots.sort(Comparator
                .comparing((AiVideoShotPo shot) -> firstInteger(shot.getEpisodeNo(), 1))
                .thenComparing(shot -> firstInteger(shot.getShotNo(), 0))
                .thenComparing(shot -> firstInteger(shot.getSortOrder(), 0)));
        return sortedShots;
    }

    private List<AiVideoMediaAssetPo> selectSelectedShotVideos(Long projectId) {
        List<AiVideoMediaAssetPo> medias = mediaAssetMapper.selectList(new LambdaQueryWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, projectId)
                .eq(AiVideoMediaAssetPo::getAssetType, ASSET_SHOT_VIDEO)
                .eq(AiVideoMediaAssetPo::getBizType, BIZ_SHOT)
                .eq(AiVideoMediaAssetPo::getSelected, YES)
                .eq(AiVideoMediaAssetPo::getDelFlag, DEL_FLAG_NORMAL));
        return medias == null ? new ArrayList<>() : medias;
    }

    private String buildDirectEditParam(AiVideoProjectPo project, AivideoProjectEditPreflightVo preflight,
                                        String videoName, boolean includeAudio, String uploader, String outputFileName) {
        AiVideoProjectSettingPo projectSetting = selectProjectSetting(project.getProjectId());
        AiVideoProjectSettingPo globalSetting = selectGlobalSetting(project.getTenantId());
        String ratio = firstText(
                projectSetting != null ? projectSetting.getDefaultRatio() : null,
                globalSetting != null ? globalSetting.getDefaultRatio() : null,
                project.getDefaultRatio(),
                "9:16");
        String resolution = firstText(
                projectSetting != null ? projectSetting.getDefaultResolution() : null,
                globalSetting != null ? globalSetting.getDefaultResolution() : null,
                "720p");
        int[] canvas = resolveCanvas(ratio, resolution);
        List<Map<String, Object>> videoTrack = new ArrayList<>();
        for (AivideoProjectEditClipVo clip : preflight.getClips()) {
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("ID", "shot_" + clip.getShotNo());
            element.put("Source", clip.getVideoUrl());
            element.put("Type", "video");
            element.put("TargetTime", List.of(clip.getTimelineStartMs(), clip.getTimelineEndMs()));
            videoTrack.add(element);
        }
        Map<String, Object> editParam = new LinkedHashMap<>();
        editParam.put("Canvas", Map.of("Width", canvas[0], "Height", canvas[1]));
        editParam.put("Output", Map.of(
                "Alpha", false,
                "DisableAudio", !includeAudio,
                "DisableVideo", false,
                "Fps", 30,
                "Codec", Map.of(
                        "VideoCodec", "h264",
                        "AudioCodec", "aac",
                        "AudioBitrate", 128,
                        "Crf", 23,
                        "Preset", "slow"
                )
        ));
        editParam.put("Track", List.of(videoTrack));
        editParam.put("Upload", Map.of(
                "SpaceName", uploader,
                "VideoName", sanitizeVideoName(videoName),
                "FileName", outputFileName
        ));
        editParam.put("Uploader", uploader);
        return XuJsonUtil.toJsonString(editParam);
    }

    private String buildEditOutputFileName(AiVideoProjectPo project, String suffix) {
        String safeSuffix = firstText(suffix, "final")
                .replaceAll("[^A-Za-z0-9_-]", "-")
                .replaceAll("-{2,}", "-");
        return "aivideo/project-" + project.getProjectId() + "/final/" + safeSuffix + ".mp4";
    }

    private String nextEditOutputSuffix() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "edit-" + LocalDateTime.now().format(EDIT_OUTPUT_TIME_FORMATTER) + "-" + uuid;
    }

    private AiVideoGenerationTaskPo createEditTask(AiVideoProjectPo project, String editParam, String videoName,
                                                   int priority, AivideoProjectEditPreflightVo preflight) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("videoName", sanitizeVideoName(videoName));
        params.put("priority", priority);
        params.put("clipCount", preflight.getClipCount());
        params.put("totalDurationSec", preflight.getTotalDurationSec());
        params.put("editParam", editParam);

        AiVideoGenerationTaskPo task = new AiVideoGenerationTaskPo();
        task.setProjectId(project.getProjectId());
        task.setTenantId(project.getTenantId());
        task.setTaskType(TASK_PROJECT_EDIT_VIDEO);
        task.setBizType(BIZ_PROJECT);
        task.setBizId(project.getProjectId());
        task.setParamsJson(XuJsonUtil.toJsonString(params));
        task.setPromptText(editParam);
        task.setTaskStatus(AivideoTaskStatus.RUNNING.name());
        task.setProgress(1);
        task.setStartedTime(now());
        task.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(task);
        taskMapper.insert(task);
        return task;
    }

    private void upsertEditedVideoAsset(AiVideoProjectPo project, AiVideoGenerationTaskPo task,
                                        AivideoDirectEditProvider.EditResult result) {
        List<AiVideoMediaAssetPo> existing = mediaAssetMapper.selectList(new LambdaQueryWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, project.getProjectId())
                .eq(AiVideoMediaAssetPo::getAssetType, ASSET_PROJECT_EDIT_VIDEO)
                .eq(AiVideoMediaAssetPo::getBizType, BIZ_PROJECT)
                .eq(AiVideoMediaAssetPo::getBizId, project.getProjectId())
                .eq(AiVideoMediaAssetPo::getTaskId, task.getTaskId())
                .eq(AiVideoMediaAssetPo::getDelFlag, DEL_FLAG_NORMAL));
        if (existing != null && !existing.isEmpty()) {
            return;
        }
        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        String outputVid = firstText(result.outputVid());
        String playUrl = "";
        String playUrlError = "";
        if (StringUtils.hasText(outputVid) && directEditProvider != null) {
            try {
                playUrl = firstText(directEditProvider.playUrl(outputVid));
            } catch (RuntimeException exception) {
                playUrlError = firstText(exception.getMessage());
            }
        }
        media.setProjectId(project.getProjectId());
        media.setTenantId(project.getTenantId());
        media.setAssetType(ASSET_PROJECT_EDIT_VIDEO);
        media.setBizType(BIZ_PROJECT);
        media.setBizId(project.getProjectId());
        media.setFileUrl(firstText(playUrl, StringUtils.hasText(outputVid) ? "vod://" + outputVid : ""));
        media.setTaskId(task.getTaskId());
        media.setPromptText(task.getPromptText());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("providerTaskId", firstText(result.reqId(), task.getProviderTaskId()));
        params.put("providerStatus", firstText(result.status()));
        params.put("providerMessage", firstText(result.message()));
        params.put("outputVid", outputVid);
        params.put("playUrl", playUrl);
        params.put("playUrlError", playUrlError);
        media.setParamsJson(XuJsonUtil.toJsonString(params));
        media.setCandidateNo(1);
        media.setSelected(YES);
        media.setAssetStatus(STATUS_SELECTED);
        media.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(media);
        mediaAssetMapper.insert(media);
    }

    private AiVideoProjectPo requireProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = projectMapper.selectById(projectId);
        if (project == null || !Integer.valueOf(DEL_FLAG_NORMAL).equals(project.getDelFlag())) {
            throw new BusinessException("项目不存在或已删除");
        }
        return project;
    }

    private AiVideoGenerationTaskPo requireEditTask(AiVideoProjectPo project, Long taskId) {
        if (taskId == null) {
            throw new BusinessException("剪辑任务ID不能为空");
        }
        AiVideoGenerationTaskPo task = taskMapper.selectById(taskId);
        if (task == null
                || !Objects.equals(project.getProjectId(), task.getProjectId())
                || !TASK_PROJECT_EDIT_VIDEO.equals(task.getTaskType())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(task.getDelFlag())) {
            throw new BusinessException("剪辑任务不存在或不属于当前项目");
        }
        return task;
    }

    private AivideoDirectEditProvider requireProvider() {
        if (directEditProvider == null) {
            throw new BusinessException("火山剪辑 API 未配置，请先配置 VOD AK/SK 和上传空间");
        }
        return directEditProvider;
    }

    private String toPublicUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return "";
        }
        String value = fileUrl.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        String origin = firstText(publicOrigin).trim();
        if (!StringUtils.hasText(origin)) {
            return value;
        }
        if (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return value.startsWith("/") ? origin + value : origin + "/" + value;
    }

    private boolean isSupportedDirectEditSourceUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private AiVideoProjectSettingPo selectProjectSetting(Long projectId) {
        if (settingMapper == null || projectId == null) {
            return null;
        }
        return settingMapper.selectOne(new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .eq(AiVideoProjectSettingPo::getProjectId, projectId)
                .last("limit 1"));
    }

    private AiVideoProjectSettingPo selectGlobalSetting(Long tenantId) {
        if (settingMapper == null) {
            return null;
        }
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

    private int[] resolveCanvas(String ratio, String resolution) {
        String value = firstText(ratio, "9:16").trim();
        int shortEdge = resolveShortEdge(resolution);
        int longEdge = normalizeEven(shortEdge * 16.0 / 9.0);
        if ("16:9".equals(value)) {
            return new int[]{longEdge, shortEdge};
        }
        if ("1:1".equals(value)) {
            return new int[]{shortEdge, shortEdge};
        }
        return new int[]{shortEdge, longEdge};
    }

    private int resolveShortEdge(String resolution) {
        String value = firstText(resolution, "720p").toLowerCase(Locale.ROOT).trim();
        if (value.contains("1080")) {
            return 1080;
        }
        if (value.contains("480")) {
            return 480;
        }
        if (value.contains("360")) {
            return 360;
        }
        return 720;
    }

    private int normalizeEven(double value) {
        int rounded = (int) Math.round(value);
        return rounded % 2 == 0 ? rounded : rounded + 1;
    }

    private String sanitizeVideoName(String value) {
        String name = firstText(value, "AI短剧成片").trim();
        name = name.replaceAll("[^\\p{IsHan}.()（）\\w\\s:-]", "");
        return StringUtils.hasText(name) ? name : "AI短剧成片";
    }

    private boolean isEditSuccess(String status) {
        return "success".equalsIgnoreCase(firstText(status));
    }

    private boolean isEditFailed(String status) {
        String value = firstText(status).toLowerCase(Locale.ROOT);
        return "failed_run".equals(value) || "user_canceled".equals(value);
    }

    private int firstInteger(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void fillCreateAudit(AiVideoGenerationTaskPo task) {
        String operator = resolveOperator();
        LocalDateTime current = now();
        task.setCreateBy(operator);
        task.setCreateTime(current);
        task.setUpdateBy(operator);
        task.setUpdateTime(current);
    }

    private void fillUpdateAudit(AiVideoGenerationTaskPo task) {
        task.setUpdateBy(resolveOperator());
        task.setUpdateTime(now());
    }

    private void fillCreateAudit(AiVideoMediaAssetPo media) {
        String operator = resolveOperator();
        LocalDateTime current = now();
        media.setCreateBy(operator);
        media.setCreateTime(current);
        media.setUpdateBy(operator);
        media.setUpdateTime(current);
    }

    private void markTaskFailed(AiVideoGenerationTaskPo task, String message) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        task.setTaskStatus(AivideoTaskStatus.FAILED.name());
        task.setProgress(task.getProgress() == null ? 0 : task.getProgress());
        task.setErrorMessage(message);
        task.setFinishedTime(LocalDateTime.now());
        fillUpdateAudit(task);
        taskMapper.updateById(task);
    }
}
