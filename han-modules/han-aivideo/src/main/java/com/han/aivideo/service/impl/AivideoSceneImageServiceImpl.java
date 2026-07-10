package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.aivideo.domain.dto.AivideoCharacterImageGenerateDto;
import com.han.aivideo.domain.dto.AivideoMediaRegisterDto;
import com.han.aivideo.domain.dto.AivideoMediaSelectDto;
import com.han.aivideo.domain.dto.AivideoSceneImageGenerateDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.aivideo.domain.po.AiVideoReviewRecordPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.domain.vo.AivideoMediaPreviewResource;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import com.han.aivideo.enums.AivideoTaskStatus;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoPropMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoReviewRecordMapper;
import com.han.aivideo.mapper.AiVideoSceneMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scene image candidate workflow implementation.
 */
@Service
@RequiredArgsConstructor
public class AivideoSceneImageServiceImpl extends AivideoServiceSupport implements IAivideoSceneImageService {

    private static final String ASSET_SCENE_IMAGE = "SCENE_IMAGE";
    private static final String ASSET_CHARACTER_IMAGE = "CHARACTER_IMAGE";
    private static final String ASSET_PROP_IMAGE = "PROP_IMAGE";
    private static final String ASSET_SHOT_VIDEO = "SHOT_VIDEO";
    private static final String ASSET_SHOT_TAIL_FRAME = "SHOT_TAIL_FRAME";
    private static final String ASSET_SHOT_AUDIO = "SHOT_AUDIO";
    private static final String ASSET_SHOT_TTS_AUDIO = "SHOT_TTS_AUDIO";
    private static final String ASSET_CHARACTER_VOICE_AUDIO = "CHARACTER_VOICE_AUDIO";
    private static final String ASSET_PROJECT_BGM_AUDIO = "PROJECT_BGM_AUDIO";
    private static final String ASSET_SHOT_SFX_AUDIO = "SHOT_SFX_AUDIO";
    private static final String ASSET_PROJECT_EDIT_VIDEO = "PROJECT_EDIT_VIDEO";
    private static final String BIZ_SCENE = "SCENE";
    private static final String BIZ_CHARACTER = "CHARACTER";
    private static final String BIZ_PROP = "PROP";
    private static final String BIZ_SHOT = "SHOT";
    private static final String BIZ_PROJECT = "PROJECT";
    private static final String TASK_SCENE_IMAGE = "SCENE_IMAGE";
    private static final String TASK_CHARACTER_IMAGE = "CHARACTER_IMAGE";
    private static final String TARGET_MEDIA = "MEDIA_ASSET";
    private static final String ACTION_SELECT = "SELECT";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_SELECTED = "SELECTED";
    private static final String MEDIA_ACCESS_PRIVATE = "PRIVATE";
    private static final String MEDIA_ACCESS_PUBLIC = "PUBLIC";
    private static final int DEFAULT_CANDIDATE_COUNT = 2;
    private static final int MAX_REFERENCE_IMAGE_COUNT = 9;
    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_VIDEO_BYTES = 300 * 1024 * 1024;
    private static final int MAX_REFERENCE_AUDIO_BYTES = 20 * 1024 * 1024;
    private static final int REFERENCE_AUDIO_SECONDS = 12;
    private static final String SCENE_IMAGE_SYSTEM_PROMPT = """
            你是 Seedance 视频生成专用场景参考图设计专家。
            核心规则：
            1. 只生成一张可作为视频首帧/环境锚点的单镜头场景参考图，不生成拼图、分栏、设定板、地图、示意图或多画面。
            2. 画面中默认严禁出现任何人影、角色、动物、脸、身体部位或角色名；除非明确要求主体入场，否则保持纯场景。
            3. 必须明确前景、中景、远景、地面/天空/墙面等空间关系，给后续角色留出可行动区域。
            4. 必须锁定时间、天气、光源方向、主色调、核心道具和背景结构，避免抽象背景或随机换地点。
            5. Prompt 必须以“不能出现其他人, 无人, 纯场景,”开头，并包含 no humans、empty scene、single shot reference。
            6. 不输出解释，只输出可直接用于图片模型的场景图提示词。
            """;
    private static final String CHARACTER_IMAGE_SYSTEM_PROMPT = """
            你是 Seedance 视频生成专用角色参考图设计专家。
            核心规则：
            1. 只生成一张单主体视频角色锚定图，不生成群像、不生成同款分身、不生成四方向/三视图/多视图/分栏设定表。
            2. 如果角色是动物或非人类，必须保持其物种本体，不要改成人类演员、真人脸或人类身体。
            3. 构图必须是单一镜头里的 3/4 正面或轻微侧正面自然站姿，全身完整可见，主体占画面高度 60%-75%。
            4. 必须完整露出头部/脸部、躯干、四肢/手脚/爪子、尾巴或标志性部位，边缘不得裁切。
            5. 背景使用纯白、浅灰或极简棚拍背景，不出现复杂场景、文字、水印、logo、漫画分镜框或说明标签。
            6. 必须突出 2-3 个稳定外观特征，供后续视频全程绑定；禁止夸张动作、强表情、换装、变身或剧情场景。
            7. 如果参考图与当前角色造型类型或视觉风格冲突，只继承身份、脸型/物种、体型、发型/毛色、服装和标志物，不继承错误风格。
            8. 不输出解释，只输出可直接用于图片模型的角色图提示词。
            """;

    private final AiVideoProjectMapper projectMapper;
    private final AiVideoProjectSettingMapper settingMapper;
    private final AiVideoSceneMapper sceneMapper;
    private final AiVideoCharacterMapper characterMapper;
    private final AiVideoPropMapper propMapper;
    private final AiVideoShotMapper shotMapper;
    private final AiVideoGenerationTaskMapper taskMapper;
    private final AiVideoMediaAssetMapper mediaAssetMapper;
    private final AiVideoReviewRecordMapper reviewRecordMapper;
    private final AiServiceClient aiServiceClient;
    private final FileServiceClient fileServiceClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${han.aivideo.media.internal-file-origin:http://gateway:8080}")
    private String internalFileOrigin;

    @Value("${han.aivideo.media.public-file-origin:}")
    private String publicFileOrigin;

    @Value("${han.aivideo.audio.ffmpeg-binary:ffmpeg}")
    private String ffmpegBinary;

    @Override
    public AivideoPromptPreviewVo previewSceneImagePrompt(AivideoSceneImageGenerateDto dto) {
        RequestContext context = buildSceneContext(dto, false);
        AivideoPromptPreviewVo vo = new AivideoPromptPreviewVo();
        vo.setPromptTemplateId(context.promptTemplateId());
        vo.setSystemPrompt(context.systemPrompt());
        vo.setUserPrompt(context.prompt());
        vo.setCustomPrompt(dto.getCustomPrompt());
        vo.setEffectivePrompt("系统提示词：\n" + context.systemPrompt() + "\n\n用户提示词：\n" + context.prompt());
        return vo;
    }

    @Override
    public SseEmitter generateSceneImagesStream(AivideoSceneImageGenerateDto dto) {
        SseEmitter emitter = new SseEmitter(300_000L);
        RequestContext context;
        AiVideoGenerationTaskPo task;
        try {
            context = buildSceneContext(dto, true);
            if (hasRunningImageTask(context.project().getProjectId(), context.taskType(), context.bizType(), context.bizId())) {
                throw new BusinessException("该场景已有图片生成任务执行中，请稍后刷新候选图");
            }
            task = createTask(context);
        } catch (Exception exception) {
            completeWithError(emitter, exception.getMessage());
            return emitter;
        }
        CompletableFuture.runAsync(() -> runImageStream(context, task, emitter));
        return emitter;
    }

    @Override
    public AivideoPromptPreviewVo previewCharacterImagePrompt(AivideoCharacterImageGenerateDto dto) {
        RequestContext context = buildCharacterContext(dto, false);
        AivideoPromptPreviewVo vo = new AivideoPromptPreviewVo();
        vo.setPromptTemplateId(context.promptTemplateId());
        vo.setSystemPrompt(context.systemPrompt());
        vo.setUserPrompt(context.prompt());
        vo.setCustomPrompt(dto.getCustomPrompt());
        vo.setEffectivePrompt("系统提示词：\n" + context.systemPrompt() + "\n\n用户提示词：\n" + context.prompt());
        return vo;
    }

    @Override
    public SseEmitter generateCharacterImagesStream(AivideoCharacterImageGenerateDto dto) {
        SseEmitter emitter = new SseEmitter(300_000L);
        RequestContext context;
        AiVideoGenerationTaskPo task;
        try {
            context = buildCharacterContext(dto, true);
            if (hasRunningImageTask(context.project().getProjectId(), context.taskType(), context.bizType(), context.bizId())) {
                throw new BusinessException("该角色已有形象图生成任务执行中，请稍后刷新候选图");
            }
            task = createTask(context);
        } catch (Exception exception) {
            completeWithError(emitter, exception.getMessage());
            return emitter;
        }
        CompletableFuture.runAsync(() -> runImageStream(context, task, emitter));
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
    public AivideoMediaAssetVo registerMedia(AivideoMediaRegisterDto dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        String assetType = normalizeAssetType(dto.getAssetType());
        MediaBizScope scope = normalizeAndValidateMediaScope(project, assetType, dto.getBizType(), dto.getBizId());
        String fileUrl = firstText(dto.getFileUrl());
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException("媒体URL不能为空");
        }

        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        media.setProjectId(project.getProjectId());
        media.setTenantId(project.getTenantId());
        media.setAssetType(assetType);
        media.setBizType(scope.bizType());
        media.setBizId(scope.bizId());
        media.setFileId(dto.getFileId());
        media.setFileUrl(normalizeRegisteredFileUrl(fileUrl));
        media.setThumbnailFileId(dto.getThumbnailFileId());
        media.setPromptText(firstText(dto.getPromptText(), "manual media asset"));
        media.setParamsJson(firstText(dto.getParamsJson(), "{}"));
        media.setCandidateNo(nextCandidateNo(project.getProjectId(), assetType, scope.bizType(), scope.bizId()));
        media.setSelected(NO);
        media.setAssetStatus(STATUS_READY);
        media.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(media);
        mediaAssetMapper.insert(media);

        if (!Boolean.FALSE.equals(dto.getSelected())) {
            AivideoMediaSelectDto selectDto = new AivideoMediaSelectDto();
            selectDto.setProjectId(project.getProjectId());
            selectDto.setMediaId(media.getMediaId());
            selectDto.setBizType(scope.bizType());
            selectDto.setBizId(scope.bizId());
            selectDto.setComment(dto.getComment());
            selectMedia(selectDto);
            AiVideoMediaAssetPo selected = mediaAssetMapper.selectById(media.getMediaId());
            if (selected != null) {
                media = selected;
            }
        }
        return toVo(media);
    }

    @Override
    public AivideoMediaPreviewResource previewMedia(Long mediaId) {
        AiVideoMediaAssetPo media = requireMedia(mediaId);
        assertMediaTenantAllowed(media);
        return openMediaPreview(media);
    }

    @Override
    public AivideoMediaPreviewResource previewPublicMedia(Long mediaId) {
        AiVideoMediaAssetPo media = requireMedia(mediaId);
        AiVideoProjectSettingPo globalSetting = selectGlobalSetting(media.getTenantId());
        String accessPolicy = firstText(globalSetting != null ? globalSetting.getMediaAccessPolicy() : null,
                MEDIA_ACCESS_PRIVATE);
        if (!MEDIA_ACCESS_PUBLIC.equalsIgnoreCase(accessPolicy)) {
            throw new BusinessException("媒体资源未公开");
        }
        if (!isPublicReferenceImage(media) || !isSelectedMedia(media)) {
            throw new BusinessException("媒体资源不是已确认的公开参考图");
        }
        return openMediaPreview(media);
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
        boolean sceneImage = ASSET_SCENE_IMAGE.equals(media.getAssetType()) && BIZ_SCENE.equals(media.getBizType());
        boolean characterImage = ASSET_CHARACTER_IMAGE.equals(media.getAssetType()) && BIZ_CHARACTER.equals(media.getBizType());
        boolean propImage = ASSET_PROP_IMAGE.equals(media.getAssetType()) && BIZ_PROP.equals(media.getBizType());
        boolean shotVideo = ASSET_SHOT_VIDEO.equals(media.getAssetType()) && BIZ_SHOT.equals(media.getBizType());
        if (!isSelectableMediaAsset(media)) {
            throw new BusinessException("当前媒体资产不是支持的候选媒体");
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

        Long before;
        if (sceneImage) {
            AiVideoScenePo scene = requireScene(project.getProjectId(), media.getBizId());
            before = scene.getLockedMediaId();
            scene.setLockedMediaId(media.getMediaId());
            fillUpdateAudit(scene);
            sceneMapper.updateById(scene);
        } else if (characterImage) {
            AiVideoCharacterPo character = requireCharacter(project.getProjectId(), media.getBizId());
            before = character.getLockedMediaId();
            character.setLockedMediaId(media.getMediaId());
            fillUpdateAudit(character);
            characterMapper.updateById(character);
        } else if (propImage) {
            AiVideoPropPo prop = requireProp(project.getProjectId(), media.getBizId());
            before = prop.getLockedMediaId();
            prop.setLockedMediaId(media.getMediaId());
            fillUpdateAudit(prop);
            propMapper.updateById(prop);
        } else if (shotVideo) {
            AiVideoShotPo shot = requireShot(project.getProjectId(), media.getBizId());
            before = shot.getVideoMediaId();
            AiVideoMediaAssetPo tailFrame = saveShotTailFrameIfPossible(project, shot, media);
            saveShotReferenceAudioIfPossible(project, shot, media);
            shot.setVideoMediaId(media.getMediaId());
            if (tailFrame != null) {
                shot.setTailFrameMediaId(tailFrame.getMediaId());
            }
            shot.setGenerationStatus(STATUS_SELECTED);
            fillUpdateAudit(shot);
            shotMapper.updateById(shot);
            markVideoTaskSuccessIfCandidateReady(project, media);
        } else {
            before = null;
        }

        insertReview(project, TARGET_MEDIA, media.getMediaId(), ACTION_SELECT,
                before == null ? "" : String.valueOf(before), String.valueOf(media.getMediaId()), dto.getComment(), null);
    }

    private String normalizeAssetType(String assetType) {
        String normalized = StringUtils.hasText(assetType) ? assetType.trim().toUpperCase() : "";
        if (!isKnownMediaAssetType(normalized)) {
            throw new BusinessException("Unsupported AIVideo media assetType: " + assetType);
        }
        return normalized;
    }

    private MediaBizScope normalizeAndValidateMediaScope(AiVideoProjectPo project, String assetType,
                                                         String requestedBizType, Long requestedBizId) {
        return switch (assetType) {
            case ASSET_SCENE_IMAGE -> {
                Long bizId = requireBizId(assetType, requestedBizId);
                requireScene(project.getProjectId(), bizId);
                yield new MediaBizScope(BIZ_SCENE, bizId);
            }
            case ASSET_CHARACTER_IMAGE, ASSET_CHARACTER_VOICE_AUDIO -> {
                Long bizId = requireBizId(assetType, requestedBizId);
                requireCharacter(project.getProjectId(), bizId);
                yield new MediaBizScope(BIZ_CHARACTER, bizId);
            }
            case ASSET_PROP_IMAGE -> {
                Long bizId = requireBizId(assetType, requestedBizId);
                requireProp(project.getProjectId(), bizId);
                yield new MediaBizScope(BIZ_PROP, bizId);
            }
            case ASSET_SHOT_VIDEO, ASSET_SHOT_TAIL_FRAME, ASSET_SHOT_AUDIO, ASSET_SHOT_TTS_AUDIO, ASSET_SHOT_SFX_AUDIO -> {
                Long bizId = requireBizId(assetType, requestedBizId);
                requireShot(project.getProjectId(), bizId);
                yield new MediaBizScope(BIZ_SHOT, bizId);
            }
            case ASSET_PROJECT_BGM_AUDIO, ASSET_PROJECT_EDIT_VIDEO -> {
                Long bizId = requestedBizId == null ? project.getProjectId() : requestedBizId;
                if (!Objects.equals(project.getProjectId(), bizId)) {
                    throw new BusinessException(assetType + " must bind to current projectId");
                }
                yield new MediaBizScope(BIZ_PROJECT, bizId);
            }
            default -> throw new BusinessException("Unsupported AIVideo media assetType: " + assetType);
        };
    }

    private Long requireBizId(String assetType, Long bizId) {
        if (bizId == null) {
            throw new BusinessException(assetType + " bizId cannot be null");
        }
        return bizId;
    }

    private String normalizeRegisteredFileUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return "";
        }
        String trimmed = fileUrl.trim();
        if (trimmed.startsWith("http")) {
            return trimmed;
        }
        return toFilePublicPath(trimmed);
    }

    private int nextCandidateNo(Long projectId, String assetType, String bizType, Long bizId) {
        return mediaAssetMapper.selectList(new LambdaQueryWrapper<AiVideoMediaAssetPo>()
                        .eq(AiVideoMediaAssetPo::getProjectId, projectId)
                        .eq(AiVideoMediaAssetPo::getAssetType, assetType)
                        .eq(AiVideoMediaAssetPo::getBizType, bizType)
                        .eq(AiVideoMediaAssetPo::getBizId, bizId)
                        .eq(AiVideoMediaAssetPo::getDelFlag, DEL_FLAG_NORMAL))
                .stream()
                .map(AiVideoMediaAssetPo::getCandidateNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private boolean isSelectableMediaAsset(AiVideoMediaAssetPo media) {
        if (media == null || !isKnownMediaAssetType(media.getAssetType())) {
            return false;
        }
        return switch (media.getAssetType()) {
            case ASSET_SCENE_IMAGE -> BIZ_SCENE.equals(media.getBizType());
            case ASSET_CHARACTER_IMAGE, ASSET_CHARACTER_VOICE_AUDIO -> BIZ_CHARACTER.equals(media.getBizType());
            case ASSET_PROP_IMAGE -> BIZ_PROP.equals(media.getBizType());
            case ASSET_SHOT_VIDEO, ASSET_SHOT_AUDIO, ASSET_SHOT_TTS_AUDIO, ASSET_SHOT_SFX_AUDIO -> BIZ_SHOT.equals(media.getBizType());
            case ASSET_PROJECT_BGM_AUDIO, ASSET_PROJECT_EDIT_VIDEO -> BIZ_PROJECT.equals(media.getBizType());
            default -> false;
        };
    }

    private boolean isKnownMediaAssetType(String assetType) {
        return switch (firstText(assetType)) {
            case ASSET_SCENE_IMAGE, ASSET_CHARACTER_IMAGE, ASSET_PROP_IMAGE,
                    ASSET_SHOT_VIDEO, ASSET_SHOT_TAIL_FRAME, ASSET_SHOT_AUDIO, ASSET_SHOT_TTS_AUDIO,
                    ASSET_CHARACTER_VOICE_AUDIO, ASSET_PROJECT_BGM_AUDIO, ASSET_SHOT_SFX_AUDIO,
                    ASSET_PROJECT_EDIT_VIDEO -> true;
            default -> false;
        };
    }

    private boolean isPublicReferenceImage(AiVideoMediaAssetPo media) {
        return switch (firstText(media != null ? media.getAssetType() : null)) {
            case ASSET_SCENE_IMAGE, ASSET_CHARACTER_IMAGE, ASSET_PROP_IMAGE, ASSET_SHOT_TAIL_FRAME -> true;
            default -> false;
        };
    }

    private boolean isSelectedMedia(AiVideoMediaAssetPo media) {
        return media != null && (YES.equals(media.getSelected())
                || STATUS_SELECTED.equalsIgnoreCase(firstText(media.getAssetStatus())));
    }

    private void runImageStream(RequestContext context, AiVideoGenerationTaskPo task, SseEmitter emitter) {
        try {
            sendSse(emitter, "meta", Map.of(
                    "event", "task",
                    "taskId", task.getTaskId(),
                    "modelId", context.modelId(),
                    "candidateCount", context.candidateCount()
            ));
            List<AivideoMediaAssetVo> assets = new ArrayList<>();
            AiImageGenerateResponse lastResponse = null;
            int requested = context.candidateCount();
            int attempts = 0;
            int maxAttempts = Math.max(requested * 2, requested);
            while (assets.size() < requested && attempts < maxAttempts) {
                attempts++;
                int nextIndex = assets.size() + 1;
                sendSse(emitter, "meta", Map.of(
                        "event", "generating",
                        "current", nextIndex,
                        "total", requested,
                        "attempt", attempts
                ));
                AiImageGenerateResponse response = invokeImageGeneration(context, 1);
                lastResponse = response;
                List<AiImageCandidate> candidates = response.getCandidates();
                if (candidates == null || candidates.isEmpty()) {
                    continue;
                }
                for (AiImageCandidate candidate : candidates) {
                    if (assets.size() >= requested) {
                        break;
                    }
                    int candidateNo = assets.size() + 1;
                    AivideoMediaAssetVo asset = saveCandidate(context, task, response, candidate, candidateNo);
                    assets.add(asset);
                    markTaskProgress(task, Math.min(95, 20 + (assets.size() * 70 / requested)));
                    sendSse(emitter, "meta", Map.of("event", "candidate", "asset", asset));
                }
            }
            if (assets.isEmpty()) {
                throw new BusinessException("图片模型未返回候选图");
            }
            if (assets.size() < requested) {
                throw new BusinessException("图片模型返回候选图数量不足，已生成 " + assets.size() + " 张，目标 " + requested + " 张，请稍后重试");
            }
            markTaskSuccess(task, lastResponse != null ? lastResponse.getModelId() : context.modelId());
            sendSse(emitter, "meta", Map.of("event", "done", "taskId", task.getTaskId(), "assets", assets));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception exception) {
            markTaskFailed(task, exception.getMessage());
            completeWithError(emitter, exception.getMessage());
        }
    }

    private RequestContext buildSceneContext(AivideoSceneImageGenerateDto dto, boolean requireImageModel) {
        if (dto == null || dto.getProjectId() == null || dto.getSceneId() == null) {
            throw new BusinessException("项目ID和场景ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoScenePo scene = requireScene(project.getProjectId(), dto.getSceneId());
        AiVideoProjectSettingPo projectSetting = selectProjectSetting(project.getProjectId());
        AiVideoProjectSettingPo globalSetting = selectGlobalSetting(project.getTenantId());
        AiVideoProjectSettingPo setting = projectSetting != null ? projectSetting : globalSetting;
        Long modelId = firstLong(dto.getModelId(),
                projectSetting != null ? projectSetting.getImageModelId() : null,
                globalSetting != null ? globalSetting.getImageModelId() : null);
        if (requireImageModel && modelId == null) {
            throw new BusinessException("图片模型未配置，请先在 AI 模型页新增 model_type=IMAGE 的火山模型，并在 AI短剧基础配置中绑定图片模型ID");
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
        Long promptTemplateId = firstLong(
                projectSetting != null ? projectSetting.getSceneImagePromptTemplateId() : null,
                globalSetting != null ? globalSetting.getSceneImagePromptTemplateId() : null);
        String style = firstText(dto.getDefaultStyle(), strategyText(projectSetting, globalSetting, PARAM_DEFAULT_STYLE,
                firstText(project.getDefaultStyle(), DEFAULT_STYLE)));
        List<String> referenceImageUrls = resolveReferenceImageUrls(project.getProjectId(), ASSET_SCENE_IMAGE,
                BIZ_SCENE, dto.getReferenceMediaIds(), dto.getReferenceImageUrl(), dto.getReferenceImageUrls());
        Map<String, String> variables = buildSceneVariables(project, scene, ratio, resolution, style);
        variables.put("referenceImageUrl", referenceImageUrls.isEmpty() ? "未填写" : referenceImageUrls.get(0));
        variables.put("referenceImageUrls", formatReferenceImageUrls(referenceImageUrls));
        variables.put("referenceImageCount", String.valueOf(referenceImageUrls.size()));
        String fallbackPrompt = buildSceneImagePrompt(project, scene, ratio, resolution, style);
        String prompt = renderPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        variables.put("candidateCount", String.valueOf(candidateCount));
        variables.put("size", firstText(dto.getSize(), ""));
        return new RequestContext(project, scene, null, setting, modelId, promptTemplateId, candidateCount,
                ratio, resolution, dto.getSize(), dto.getCustomPrompt(), prompt, variables, referenceImageUrls,
                SCENE_IMAGE_SYSTEM_PROMPT, ASSET_SCENE_IMAGE, BIZ_SCENE, scene.getSceneId(),
                TASK_SCENE_IMAGE, "场景图生成失败", "人物, 人影, human, person, face, body, crowd, extra people");
    }

    private RequestContext buildCharacterContext(AivideoCharacterImageGenerateDto dto, boolean requireImageModel) {
        if (dto == null || dto.getProjectId() == null || dto.getCharacterId() == null) {
            throw new BusinessException("项目ID和角色ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoCharacterPo character = requireCharacter(project.getProjectId(), dto.getCharacterId());
        AiVideoProjectSettingPo projectSetting = selectProjectSetting(project.getProjectId());
        AiVideoProjectSettingPo globalSetting = selectGlobalSetting(project.getTenantId());
        AiVideoProjectSettingPo setting = projectSetting != null ? projectSetting : globalSetting;
        Long modelId = firstLong(dto.getModelId(),
                projectSetting != null ? projectSetting.getImageModelId() : null,
                globalSetting != null ? globalSetting.getImageModelId() : null);
        if (requireImageModel && modelId == null) {
            throw new BusinessException("图片模型未配置，请先在 AI 模型页新增 model_type=IMAGE 的火山模型，并在 AI短剧基础配置中绑定图片模型ID");
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
        Long promptTemplateId = firstLong(
                projectSetting != null ? projectSetting.getCharacterImagePromptTemplateId() : null,
                globalSetting != null ? globalSetting.getCharacterImagePromptTemplateId() : null);
        String style = firstText(dto.getDefaultStyle(), strategyText(projectSetting, globalSetting, PARAM_DEFAULT_STYLE,
                firstText(project.getDefaultStyle(), DEFAULT_STYLE)));
        String characterDesignType = firstText(dto.getCharacterDesignType(), strategyText(projectSetting, globalSetting,
                PARAM_CHARACTER_DESIGN_TYPE, DEFAULT_CHARACTER_DESIGN_TYPE));
        List<String> referenceImageUrls = resolveReferenceImageUrls(project.getProjectId(), ASSET_CHARACTER_IMAGE,
                BIZ_CHARACTER, dto.getReferenceMediaIds(), dto.getReferenceImageUrl(), dto.getReferenceImageUrls());
        Map<String, String> variables = buildCharacterVariables(project, character, ratio, resolution, style, characterDesignType);
        variables.put("referenceImageUrl", referenceImageUrls.isEmpty() ? "未填写" : referenceImageUrls.get(0));
        variables.put("referenceImageUrls", formatReferenceImageUrls(referenceImageUrls));
        variables.put("referenceImageCount", String.valueOf(referenceImageUrls.size()));
        String fallbackPrompt = buildCharacterImagePrompt(project, character, ratio, resolution, style, characterDesignType,
                referenceImageUrls.isEmpty() ? null : referenceImageUrls.get(0));
        String prompt = renderPrompt(project, promptTemplateId, dto.getCustomPrompt(), fallbackPrompt, variables);
        variables.put("candidateCount", String.valueOf(candidateCount));
        variables.put("size", firstText(dto.getSize(), ""));
        return new RequestContext(project, null, character, setting, modelId, promptTemplateId, candidateCount,
                ratio, resolution, dto.getSize(), dto.getCustomPrompt(), prompt, variables, referenceImageUrls,
                CHARACTER_IMAGE_SYSTEM_PROMPT, ASSET_CHARACTER_IMAGE, BIZ_CHARACTER, character.getCharacterId(),
                TASK_CHARACTER_IMAGE, "角色形象图生成失败",
                "multiple characters, crowd, extra people, watermark, logo, text, signature, headshot only, portrait only, close-up only, half body, cropped body, missing legs, missing paws, missing feet, missing tail, inconsistent layout, inconsistent scale, inconsistent markings, different animal, different breed, human body if animal role");
    }

    private List<String> resolveReferenceImageUrls(Long projectId, String expectedAssetType, String expectedBizType,
                                                   List<Long> referenceMediaIds, String referenceImageUrl,
                                                   List<String> referenceImageUrls) {
        List<String> references = new ArrayList<>();
        if (referenceMediaIds != null) {
            for (Long mediaId : referenceMediaIds) {
                addMediaReference(references, projectId, expectedAssetType, expectedBizType, mediaId);
            }
        }
        addDirectReference(references, referenceImageUrl);
        if (referenceImageUrls != null) {
            for (String url : referenceImageUrls) {
                addDirectReference(references, url);
            }
        }
        return references;
    }

    private void addMediaReference(List<String> references, Long projectId, String expectedAssetType,
                                   String expectedBizType, Long mediaId) {
        if (mediaId == null) {
            return;
        }
        if (references.size() >= MAX_REFERENCE_IMAGE_COUNT) {
            throw new BusinessException("参考图最多支持 " + MAX_REFERENCE_IMAGE_COUNT + " 张");
        }
        AiVideoMediaAssetPo media = requireMedia(mediaId);
        if (!Objects.equals(projectId, media.getProjectId())) {
            throw new BusinessException("参考图不属于当前项目");
        }
        if (!expectedAssetType.equals(media.getAssetType()) || !expectedBizType.equals(media.getBizType())) {
            throw new BusinessException("参考图类型不匹配，请选择已确认的" + ("SCENE".equals(expectedBizType) ? "场景图" : "角色图"));
        }
        if (!YES.equals(media.getSelected())) {
            throw new BusinessException("只能选择已确认的参考图");
        }
        addReference(references, buildProviderFileUrl(media.getFileUrl()));
    }

    private void addDirectReference(List<String> references, String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        if (references.size() >= MAX_REFERENCE_IMAGE_COUNT) {
            throw new BusinessException("参考图最多支持 " + MAX_REFERENCE_IMAGE_COUNT + " 张");
        }
        addReference(references, buildProviderFileUrl(url));
    }

    private void addReference(List<String> references, String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        String trimmed = url.trim();
        if (!references.contains(trimmed)) {
            references.add(trimmed);
        }
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
            throw new BusinessException("参考图外部访问地址未配置，请配置 han.aivideo.media.public-file-origin");
        }
        if (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return origin + publicPath;
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

    private AiImageGenerateResponse invokeImageGeneration(RequestContext context, int candidateCount) {
        AiImageGenerateRequest request = new AiImageGenerateRequest();
        request.setTenantId(context.project().getTenantId());
        request.setModelId(context.modelId());
        request.setUserPrompt(context.prompt());
        request.setCandidateCount(candidateCount);
        request.setRatio(context.ratio());
        request.setResolution(context.resolution());
        request.setSize(context.size());
        request.setReferenceImageUrls(context.referenceImageUrls());
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
        String filenamePrefix = ASSET_CHARACTER_IMAGE.equals(context.assetType()) ? "aivideo-character-" : "aivideo-scene-";
        String filename = filenamePrefix + context.bizId() + "-" + task.getTaskId()
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
        media.setAssetType(context.assetType());
        media.setBizType(context.bizType());
        media.setBizId(context.bizId());
        media.setFileId(file.getId());
        media.setFileUrl(toFilePublicPath(file.getUrl()));
        media.setPromptText(response.getPrompt());
        media.setNegativePrompt(context.negativePrompt());
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

    private AiVideoMediaAssetPo saveShotTailFrameIfPossible(AiVideoProjectPo project, AiVideoShotPo shot,
                                                            AiVideoMediaAssetPo sourceVideo) {
        String lastFrameUrl = resolveProviderLastFrameUrl(sourceVideo);
        if (!StringUtils.hasText(lastFrameUrl)) {
            return null;
        }
        ImageBytes imageBytes;
        try {
            imageBytes = downloadImage(lastFrameUrl);
        } catch (RuntimeException exception) {
            return null;
        }
        String extension = extensionFromMime(imageBytes.mimeType());
        String filename = "aivideo-shot-tail-frame-" + shot.getShotId() + "-" + sourceVideo.getMediaId()
                + "." + extension;
        Resource resource = new NamedByteArrayResource(imageBytes.bytes(), filename);
        R<FileDTO> uploadResult;
        try {
            uploadResult = fileServiceClient.upload(resource);
        } catch (RuntimeException exception) {
            return null;
        }
        if (uploadResult == null || uploadResult.isFail()) {
            return null;
        }
        FileDTO file = uploadResult.getData();
        if (file == null || file.getId() == null || !StringUtils.hasText(file.getUrl())) {
            return null;
        }

        mediaAssetMapper.update(null, new LambdaUpdateWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, project.getProjectId())
                .eq(AiVideoMediaAssetPo::getAssetType, ASSET_SHOT_TAIL_FRAME)
                .eq(AiVideoMediaAssetPo::getBizType, BIZ_SHOT)
                .eq(AiVideoMediaAssetPo::getBizId, shot.getShotId())
                .set(AiVideoMediaAssetPo::getSelected, NO)
                .set(AiVideoMediaAssetPo::getAssetStatus, STATUS_READY)
                .set(AiVideoMediaAssetPo::getUpdateBy, resolveOperator())
                .set(AiVideoMediaAssetPo::getUpdateTime, now()));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("sourceVideoMediaId", String.valueOf(sourceVideo.getMediaId()));
        params.put("sourceTaskId", sourceVideo.getTaskId() == null ? "" : String.valueOf(sourceVideo.getTaskId()));
        params.put("providerLastFrameUrl", lastFrameUrl);

        AiVideoMediaAssetPo tailFrame = new AiVideoMediaAssetPo();
        tailFrame.setProjectId(project.getProjectId());
        tailFrame.setTenantId(project.getTenantId());
        tailFrame.setAssetType(ASSET_SHOT_TAIL_FRAME);
        tailFrame.setBizType(BIZ_SHOT);
        tailFrame.setBizId(shot.getShotId());
        tailFrame.setFileId(file.getId());
        tailFrame.setFileUrl(toFilePublicPath(file.getUrl()));
        tailFrame.setPromptText("分镜尾帧参考图，来源视频 #" + sourceVideo.getMediaId());
        tailFrame.setModelId(sourceVideo.getModelId());
        tailFrame.setTaskId(sourceVideo.getTaskId());
        tailFrame.setParamsJson(XuJsonUtil.toJsonString(params));
        tailFrame.setCandidateNo(sourceVideo.getCandidateNo());
        tailFrame.setSelected(YES);
        tailFrame.setAssetStatus(STATUS_SELECTED);
        tailFrame.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(tailFrame);
        mediaAssetMapper.insert(tailFrame);
        return tailFrame;
    }

    private AiVideoMediaAssetPo saveShotReferenceAudioIfPossible(AiVideoProjectPo project, AiVideoShotPo shot,
                                                                 AiVideoMediaAssetPo sourceVideo) {
        clearSelectedShotAudio(project, shot);
        AudioBytes audioBytes = extractReferenceAudio(sourceVideo);
        if (audioBytes == null || audioBytes.bytes().length == 0) {
            return null;
        }
        String filename = "aivideo-shot-reference-audio-" + shot.getShotId() + "-" + sourceVideo.getMediaId()
                + ".mp3";
        Resource resource = new NamedByteArrayResource(audioBytes.bytes(), filename);
        R<FileDTO> uploadResult;
        try {
            uploadResult = fileServiceClient.upload(resource);
        } catch (RuntimeException exception) {
            return null;
        }
        if (uploadResult == null || uploadResult.isFail()) {
            return null;
        }
        FileDTO file = uploadResult.getData();
        if (file == null || file.getId() == null || !StringUtils.hasText(file.getUrl())) {
            return null;
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("sourceVideoMediaId", String.valueOf(sourceVideo.getMediaId()));
        params.put("sourceTaskId", sourceVideo.getTaskId() == null ? "" : String.valueOf(sourceVideo.getTaskId()));
        params.put("durationSec", String.valueOf(REFERENCE_AUDIO_SECONDS));
        params.put("mimeType", audioBytes.mimeType());

        AiVideoMediaAssetPo audio = new AiVideoMediaAssetPo();
        audio.setProjectId(project.getProjectId());
        audio.setTenantId(project.getTenantId());
        audio.setAssetType(ASSET_SHOT_AUDIO);
        audio.setBizType(BIZ_SHOT);
        audio.setBizId(shot.getShotId());
        audio.setFileId(file.getId());
        audio.setFileUrl(toFilePublicPath(file.getUrl()));
        audio.setPromptText("分镜参考音频，来源视频 #" + sourceVideo.getMediaId());
        audio.setModelId(sourceVideo.getModelId());
        audio.setTaskId(sourceVideo.getTaskId());
        audio.setParamsJson(XuJsonUtil.toJsonString(params));
        audio.setCandidateNo(sourceVideo.getCandidateNo());
        audio.setSelected(YES);
        audio.setAssetStatus(STATUS_SELECTED);
        audio.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(audio);
        mediaAssetMapper.insert(audio);
        return audio;
    }

    private void clearSelectedShotAudio(AiVideoProjectPo project, AiVideoShotPo shot) {
        if (project == null || shot == null || shot.getShotId() == null) {
            return;
        }
        mediaAssetMapper.update(null, new LambdaUpdateWrapper<AiVideoMediaAssetPo>()
                .eq(AiVideoMediaAssetPo::getProjectId, project.getProjectId())
                .eq(AiVideoMediaAssetPo::getAssetType, ASSET_SHOT_AUDIO)
                .eq(AiVideoMediaAssetPo::getBizType, BIZ_SHOT)
                .eq(AiVideoMediaAssetPo::getBizId, shot.getShotId())
                .set(AiVideoMediaAssetPo::getSelected, NO)
                .set(AiVideoMediaAssetPo::getAssetStatus, STATUS_READY)
                .set(AiVideoMediaAssetPo::getUpdateBy, resolveOperator())
                .set(AiVideoMediaAssetPo::getUpdateTime, now()));
    }

    private AudioBytes extractReferenceAudio(AiVideoMediaAssetPo sourceVideo) {
        if (sourceVideo == null || !StringUtils.hasText(sourceVideo.getFileUrl())) {
            return null;
        }
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("aivideo-shot-video-", ".mp4");
            output = Files.createTempFile("aivideo-shot-audio-", ".mp3");
            downloadMediaToFile(buildMediaDownloadUrl(sourceVideo), input, MAX_VIDEO_BYTES);
            Process process = new ProcessBuilder(
                    firstText(ffmpegBinary, "ffmpeg"),
                    "-y",
                    "-i", input.toString(),
                    "-map", "0:a:0?",
                    "-vn",
                    "-t", String.valueOf(REFERENCE_AUDIO_SECONDS),
                    "-ac", "1",
                    "-ar", "44100",
                    "-b:a", "128k",
                    output.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0 || !Files.exists(output) || Files.size(output) <= 0) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(output);
            if (bytes.length > MAX_REFERENCE_AUDIO_BYTES) {
                return null;
            }
            return new AudioBytes(bytes, "audio/mpeg");
        } catch (IOException | RuntimeException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            deleteTempFile(input);
            deleteTempFile(output);
        }
    }

    private String buildMediaDownloadUrl(AiVideoMediaAssetPo media) {
        String fileUrl = firstText(media != null ? media.getFileUrl() : null);
        if (fileUrl.startsWith("http")) {
            return fileUrl;
        }
        return buildInternalFileUrl(toFilePublicPath(fileUrl));
    }

    private void downloadMediaToFile(String mediaUrl, Path target, int maxBytes) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(mediaUrl).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(300_000);
            connection.setRequestProperty("User-Agent", "Han-AIVideo/1.0");
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("download failed: " + statusCode);
            }
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IOException("media file too large");
                    }
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary extraction files are best-effort cleanup.
        }
    }

    private void markVideoTaskSuccessIfCandidateReady(AiVideoProjectPo project, AiVideoMediaAssetPo media) {
        if (media == null || media.getTaskId() == null) {
            return;
        }
        AiVideoGenerationTaskPo task = taskMapper.selectById(media.getTaskId());
        if (task == null || !Objects.equals(project.getProjectId(), task.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(task.getDelFlag())) {
            return;
        }
        task.setTaskStatus(AivideoTaskStatus.SUCCESS.name());
        task.setProgress(100);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        if (task.getFinishedTime() == null) {
            task.setFinishedTime(now());
        }
        fillUpdateAudit(task);
        taskMapper.updateById(task);
    }

    @SuppressWarnings("unchecked")
    private String resolveProviderLastFrameUrl(AiVideoMediaAssetPo sourceVideo) {
        if (sourceVideo == null || !StringUtils.hasText(sourceVideo.getParamsJson())) {
            return "";
        }
        try {
            Map<String, Object> params = XuJsonUtil.parseObject(sourceVideo.getParamsJson(), Map.class);
            Object value = params.get("providerLastFrameUrl");
            return value == null ? "" : String.valueOf(value).trim();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private AiVideoMediaAssetPo requireMedia(Long mediaId) {
        if (mediaId == null) {
            throw new BusinessException("媒体ID不能为空");
        }
        AiVideoMediaAssetPo media = mediaAssetMapper.selectById(mediaId);
        if (media == null || !Integer.valueOf(DEL_FLAG_NORMAL).equals(media.getDelFlag())) {
            throw new BusinessException("媒体资源不存在");
        }
        if (!isKnownMediaAssetType(media.getAssetType())) {
            throw new BusinessException("当前媒体不是短剧资源");
        }
        if (!StringUtils.hasText(media.getFileUrl())) {
            throw new BusinessException("媒体资源未归档");
        }
        return media;
    }

    private void assertMediaTenantAllowed(AiVideoMediaAssetPo media) {
        Long tenantId = currentTenantId();
        if (tenantId != null && !Objects.equals(tenantId, media.getTenantId())) {
            throw new BusinessException("无权访问该媒体资源");
        }
    }

    private AivideoMediaPreviewResource openMediaPreview(AiVideoMediaAssetPo media) {
        String fileUrl = firstText(media.getFileUrl());
        String publicPath = toFilePublicPath(fileUrl);
        String previewUrl = buildInternalFileUrl(publicPath);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(previewUrl).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "Han-AIVideo/1.0");
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException("读取媒体资源失败(" + statusCode + ")");
            }
            return new AivideoMediaPreviewResource(
                    fileNameFromPath(publicPath),
                    safeMediaType(firstText(connection.getContentType(), "image/jpeg")),
                    new DisconnectingInputStream(connection.getInputStream(), connection)
            );
        } catch (IOException exception) {
            if (connection != null) {
                connection.disconnect();
            }
            throw new BusinessException("读取媒体资源失败: " + exception.getMessage());
        } catch (RuntimeException exception) {
            if (connection != null) {
                connection.disconnect();
            }
            throw exception;
        }
    }

    private String buildInternalFileUrl(String publicPath) {
        String origin = firstText(internalFileOrigin, "http://gateway:8080").trim();
        if (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return origin + publicPath;
    }

    private String toFilePublicPath(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException("媒体资源地址为空");
        }
        try {
            URI uri = URI.create(fileUrl.trim());
            String scheme = uri.getScheme();
            if (scheme != null && !"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("unsupported scheme");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("query and fragment are not allowed");
            }

            String rawPath = uri.getRawPath();
            String decodedPath = uri.getPath();
            if (!StringUtils.hasText(rawPath) || !StringUtils.hasText(decodedPath)
                    || !decodedPath.startsWith("/file/public/")
                    || rawPath.matches("(?i).*(%2f|%5c).*")
                    || decodedPath.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("invalid public file path");
            }

            String[] segments = decodedPath.substring("/file/public/".length()).split("/", -1);
            if (segments.length != 2
                    || !segments[0].matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                    || segments[0].equals(".") || segments[0].equals("..")
                    || !StringUtils.hasText(segments[1])
                    || segments[1].equals(".") || segments[1].equals("..")) {
                throw new IllegalArgumentException("invalid public file locator or name");
            }
            return rawPath;
        } catch (IllegalArgumentException ignored) {
            throw new BusinessException("媒体资源地址不是受控文件路径");
        }
    }

    private String fileNameFromPath(String publicPath) {
        int slashIndex = publicPath.lastIndexOf('/');
        return slashIndex >= 0 ? publicPath.substring(slashIndex + 1) : "aivideo-media";
    }

    private MediaType safeMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException exception) {
            return MediaType.IMAGE_JPEG;
        }
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
                                                    String ratio, String resolution, String style) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("projectName", safeValue(project.getProjectName()));
        variables.put("targetPlatform", safeValue(project.getTargetPlatform()));
        variables.put("style", safeValue(style));
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

    private String buildSceneImagePrompt(AiVideoProjectPo project, AiVideoScenePo scene, String ratio, String resolution,
                                         String style) {
        return """
                不能出现其他人, 无人, 纯场景, no humans, empty scene, single shot reference。
                Seedance 视频场景参考图/首帧环境锚点，单一镜头画面，极高画质，高辨识度，画幅：%s，清晰度目标：%s。
                严禁出现任何角色、人名、人影、人物剪影、动物、脸、身体部位、crowd、person、human。
            禁止拼图、分栏、设定板、地图、俯视平面图、漫画格、文字、水印、logo 或说明标签。
            如本次传入参考图，必须按图片1、图片2……的顺序使用：图片1为最重要场景锚点，优先继承其空间结构、镜头位置、核心道具和建筑关系；后续参考图只补充天气、时间、光线或细节，不得随机换地点。
            场景描述必须完整涵盖环境类型、具体时间、天气光线、空间氛围、视觉主要特征和核心道具。
            画面必须可作为后续分镜视频背景：前景/中景/远景清楚，地面或可行动区域明确，主光源方向和色调稳定，避免过度抽象。

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
                safeValue(style), safeValue(scene.getSceneName()), safeValue(scene.getSceneType()),
                safeValue(scene.getTimeDesc()), safeValue(scene.getWeather()), safeValue(scene.getAtmosphere()),
                safeValue(scene.getVisualFeatures()), safeValue(scene.getColorTone()), safeValue(scene.getProps()),
                safeValue(scene.getPromptText()));
    }

    private Map<String, String> buildCharacterVariables(AiVideoProjectPo project, AiVideoCharacterPo character,
                                                        String ratio, String resolution, String style,
                                                        String characterDesignType) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("projectName", safeValue(project.getProjectName()));
        variables.put("targetPlatform", safeValue(project.getTargetPlatform()));
        variables.put("style", safeValue(style));
        variables.put(PARAM_CHARACTER_DESIGN_TYPE, safeValue(characterDesignType));
        variables.put("characterDesignInstruction", characterDesignInstruction(characterDesignType, style));
        variables.put("ratio", safeValue(ratio));
        variables.put("resolution", safeValue(resolution));
        variables.put("characterName", safeValue(character.getCharacterName()));
        variables.put("gender", safeValue(character.getGender()));
        variables.put("ageDesc", safeValue(character.getAgeDesc()));
        variables.put("identityDesc", safeValue(character.getIdentityDesc()));
        variables.put("personalityTags", safeValue(character.getPersonalityTags()));
        variables.put("storyRole", safeValue(character.getStoryRole()));
        variables.put("relationshipDesc", safeValue(character.getRelationshipDesc()));
        variables.put("appearance", safeValue(character.getAppearance()));
        variables.put("hairStyle", safeValue(character.getHairStyle()));
        variables.put("costume", safeValue(character.getCostume()));
        variables.put("colorStyle", safeValue(character.getColorStyle()));
        variables.put("negativeTraits", safeValue(character.getNegativeTraits()));
        variables.put("characterPromptText", sanitizeCharacterImagePromptText(character.getPromptText()));
        return variables;
    }

    private String buildCharacterImagePrompt(AiVideoProjectPo project, AiVideoCharacterPo character,
                                             String ratio, String resolution, String style,
                                             String characterDesignType, String referenceImageUrl) {
        return """
                Seedance 视频生成专用单主体角色锚定图，单一角色，纯净背景，角色一致性参考图，画幅：%s，清晰度目标：%s。
                如果该角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须保持其物种本体，不要改成人类演员、真人脸或人类身体。
                造型硬规则：%s
                构图硬规则：只输出一只/一个主体，3/4 正面或轻微侧正面自然站姿，主体居中，全身完整可见，主体占画面高度 60%-75%。
                视频参考硬规则：禁止四方向、三视图、多视图、转面表、分栏、拼图、同款分身、多个角度并排；避免被视频模型误识别成多个主体。
                全身硬规则：必须完整露出头部/脸部、躯干、四肢/爪子/脚、尾巴或标志性部位；禁止只画头部、禁止半身、禁止身体裁切。
            一致性硬规则：突出 2-3 个稳定外观特征，保持同一体型、年龄阶段、物种/品种、毛色/发型、服饰/身体特征、斑纹、光照和比例。
            旧词屏蔽规则：如果历史提示词里出现头像、半身、三视图、四方向、正侧背等旧版版式，只提取身份和外观特征；最终只允许单主体视频角色锚定图。
            如本次传入参考图，必须按图片1、图片2……的顺序使用：图片1为最重要角色身份锚点，优先继承物种/脸型/体型/毛色/服装/标志性细节；后续参考图只补充局部细节，不得生成多个主体或改变物种。
            风格冲突处理：如果参考图风格与当前视觉风格或角色造型类型冲突，只继承身份和稳定外观锚点，不继承错误风格、错误比例或错误材质。
            必须体现身份定位、性格气质、外观轮廓、毛发/发型、服饰/身体特征、颜色风格、标志性细节。
                只出现该角色本体，不出现其他角色、复杂剧情动作、文字、水印、logo、复杂环境；背景使用纯白、浅灰或极简棚拍背景。

                项目：%s
                风格：%s
                角色造型类型：%s
                角色名称：%s
                性别/物种：%s
                年龄/阶段：%s
                身份定位：%s
                剧情定位：%s
                性格标签：%s
                关系描述：%s
                形象描述：%s
                毛发/发型：%s
                服饰/身体特征：%s
                色彩风格：%s
                负面特征：%s
                净化后的角色外观提示词：%s
                参考图 URL：%s
                """.formatted(
                safeValue(ratio), safeValue(resolution), characterDesignInstruction(characterDesignType, style),
                safeValue(project.getProjectName()),
                safeValue(style), safeValue(characterDesignType), safeValue(character.getCharacterName()), safeValue(character.getGender()),
                safeValue(character.getAgeDesc()), safeValue(character.getIdentityDesc()), safeValue(character.getStoryRole()),
                safeValue(character.getPersonalityTags()), safeValue(character.getRelationshipDesc()), safeValue(character.getAppearance()),
                safeValue(character.getHairStyle()), safeValue(character.getCostume()), safeValue(character.getColorStyle()),
                safeValue(character.getNegativeTraits()), sanitizeCharacterImagePromptText(character.getPromptText()), safeValue(referenceImageUrl));
    }

    private int normalizeCandidateCount(Integer requested, AiVideoProjectSettingPo projectSetting,
                                        AiVideoProjectSettingPo globalSetting) {
        int value = requested != null && requested > 0
                ? requested
                : firstInteger(
                projectSetting != null ? projectSetting.getImageCandidateCount() : null,
                globalSetting != null ? globalSetting.getImageCandidateCount() : null,
                DEFAULT_CANDIDATE_COUNT);
        return Math.max(1, Math.min(4, value));
    }

    private boolean hasRunningImageTask(Long projectId, String taskType, String bizType, Long bizId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(AiVideoGenerationTaskPo::getProjectId, projectId)
                .eq(AiVideoGenerationTaskPo::getTaskType, taskType)
                .eq(AiVideoGenerationTaskPo::getBizType, bizType)
                .eq(AiVideoGenerationTaskPo::getBizId, bizId)
                .in(AiVideoGenerationTaskPo::getTaskStatus, AivideoTaskStatus.PENDING.name(), AivideoTaskStatus.RUNNING.name())
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)) > 0;
    }

    private AiVideoGenerationTaskPo createTask(RequestContext context) {
        AiVideoGenerationTaskPo task = new AiVideoGenerationTaskPo();
        task.setProjectId(context.project().getProjectId());
        task.setTenantId(context.project().getTenantId());
        task.setTaskType(context.taskType());
        task.setBizType(context.bizType());
        task.setBizId(context.bizId());
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
        task.setErrorMessage(message == null ? "图片生成失败" : message);
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

    private AiVideoCharacterPo requireCharacter(Long projectId, Long characterId) {
        AiVideoCharacterPo character = characterMapper.selectById(characterId);
        if (character == null || !Objects.equals(projectId, character.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(character.getDelFlag())) {
            throw new BusinessException("角色资产不存在");
        }
        return character;
    }

    private AiVideoPropPo requireProp(Long projectId, Long propId) {
        AiVideoPropPo prop = propMapper.selectById(propId);
        if (prop == null || !Objects.equals(projectId, prop.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(prop.getDelFlag())) {
            throw new BusinessException("道具资产不存在");
        }
        return prop;
    }

    private AiVideoShotPo requireShot(Long projectId, Long shotId) {
        AiVideoShotPo shot = shotMapper.selectById(shotId);
        if (shot == null || !Objects.equals(projectId, shot.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(shot.getDelFlag())) {
            throw new BusinessException("分镜资产不存在");
        }
        return shot;
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
        LambdaQueryWrapper<AiVideoProjectSettingPo> wrapper = new LambdaQueryWrapper<AiVideoProjectSettingPo>()
                .isNull(AiVideoProjectSettingPo::getProjectId)
                .and(q -> q.eq(AiVideoProjectSettingPo::getTenantId, 0L)
                        .or().isNull(AiVideoProjectSettingPo::getTenantId))
                .orderByDesc(AiVideoProjectSettingPo::getUpdateTime)
                .orderByDesc(AiVideoProjectSettingPo::getSettingId)
                .last("limit 1");
        return settingMapper.selectOne(wrapper);
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
            sendSse(emitter, "error", StringUtils.hasText(message) ? message : "图片生成失败");
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

    private void fillUpdateAudit(AiVideoCharacterPo character) {
        character.setUpdateBy(resolveOperator());
        character.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoPropPo prop) {
        prop.setUpdateBy(resolveOperator());
        prop.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiVideoShotPo shot) {
        shot.setUpdateBy(resolveOperator());
        shot.setUpdateTime(now());
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
            return DEFAULT_CANDIDATE_COUNT;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return DEFAULT_CANDIDATE_COUNT;
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未填写";
    }

    private record MediaBizScope(String bizType, Long bizId) {
    }

    private record RequestContext(
            AiVideoProjectPo project,
            AiVideoScenePo scene,
            AiVideoCharacterPo character,
            AiVideoProjectSettingPo setting,
            Long modelId,
            Long promptTemplateId,
            int candidateCount,
            String ratio,
            String resolution,
            String size,
            String customPrompt,
            String prompt,
            Map<String, String> variables,
            List<String> referenceImageUrls,
            String systemPrompt,
            String assetType,
            String bizType,
            Long bizId,
            String taskType,
            String failureMessage,
            String negativePrompt
    ) {
    }

    private record ImageBytes(byte[] bytes, String mimeType) {
    }

    private record AudioBytes(byte[] bytes, String mimeType) {
    }

    private static final class DisconnectingInputStream extends FilterInputStream {
        private final HttpURLConnection connection;

        private DisconnectingInputStream(InputStream source, HttpURLConnection connection) {
            super(source);
            this.connection = connection;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
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
