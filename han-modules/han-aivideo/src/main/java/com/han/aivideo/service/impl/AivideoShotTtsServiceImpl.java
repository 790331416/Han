package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.han.api.file.FileServiceClient;
import com.han.api.file.domain.FileDTO;
import com.han.aivideo.domain.dto.AivideoShotTtsGenerateDto;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import com.han.aivideo.service.AivideoTtsProvider;
import com.han.aivideo.service.IAivideoShotTtsService;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates post-production speech audio assets for storyboard shots.
 */
@Service
public class AivideoShotTtsServiceImpl extends AivideoServiceSupport implements IAivideoShotTtsService {

    private static final String CONFIRM_APPROVED = "APPROVED";
    private static final String ASSET_SHOT_TTS_AUDIO = "SHOT_TTS_AUDIO";
    private static final String BIZ_SHOT = "SHOT";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_SELECTED = "SELECTED";
    private static final String DEFAULT_VOICE_TYPE = "BV001_24k_streaming";

    private final AiVideoProjectMapper projectMapper;
    private final AiVideoShotMapper shotMapper;
    private final AiVideoMediaAssetMapper mediaAssetMapper;
    private final FileServiceClient fileServiceClient;
    private final AivideoTtsProvider ttsProvider;

    public AivideoShotTtsServiceImpl(AiVideoProjectMapper projectMapper,
                                     AiVideoShotMapper shotMapper,
                                     AiVideoMediaAssetMapper mediaAssetMapper,
                                     FileServiceClient fileServiceClient,
                                     AivideoTtsProvider ttsProvider) {
        this.projectMapper = projectMapper;
        this.shotMapper = shotMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.fileServiceClient = fileServiceClient;
        this.ttsProvider = ttsProvider;
    }

    @Override
    public AiVideoMediaAssetPo generateShotTts(AivideoShotTtsGenerateDto dto) {
        if (dto == null || dto.getProjectId() == null || dto.getShotId() == null) {
            throw new BusinessException("项目ID和分镜ID不能为空");
        }
        AiVideoProjectPo project = requireProject(dto.getProjectId());
        AiVideoShotPo shot = requireShot(project, dto.getShotId());
        String speechText = resolveSpeechText(dto, shot);
        String voiceType = firstText(dto.getVoiceType(), DEFAULT_VOICE_TYPE);
        String requestId = "aivideo-tts-" + shot.getShotId() + "-" + UUID.randomUUID();

        AivideoTtsProvider.TtsAudio audio = ttsProvider.synthesize(new AivideoTtsProvider.TtsRequest(
                speechText,
                voiceType,
                dto.getSpeedRatio(),
                dto.getVolumeRatio(),
                dto.getPitchRatio(),
                requestId));
        if (audio == null || audio.bytes() == null || audio.bytes().length == 0) {
            throw new BusinessException("语音合成未返回有效音频");
        }

        String extension = firstText(audio.extension(), "mp3");
        String filename = "aivideo-shot-tts-" + shot.getShotId() + "-" + System.currentTimeMillis() + "." + extension;
        Resource resource = new NamedByteArrayResource(audio.bytes(), filename);
        R<FileDTO> uploadResult = fileServiceClient.upload(resource);
        if (uploadResult == null || uploadResult.isFail() || uploadResult.getData() == null) {
            throw new BusinessException("语音合成音频上传失败");
        }
        FileDTO file = uploadResult.getData();
        if (file.getId() == null || !StringUtils.hasText(file.getUrl())) {
            throw new BusinessException("语音合成音频上传结果缺少文件地址");
        }

        clearSelectedShotTts(project, shot);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("provider", "VOLC_TTS");
        params.put("providerRequestId", firstText(audio.providerRequestId(), requestId));
        params.put("voiceType", voiceType);
        params.put("durationMs", audio.durationMs());
        params.put("mimeType", firstText(audio.mimeType(), "audio/mpeg"));
        params.put("source", "POST_TTS");

        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        media.setProjectId(project.getProjectId());
        media.setTenantId(project.getTenantId());
        media.setAssetType(ASSET_SHOT_TTS_AUDIO);
        media.setBizType(BIZ_SHOT);
        media.setBizId(shot.getShotId());
        media.setFileId(file.getId());
        media.setFileUrl(toFilePublicPath(file.getUrl()));
        media.setPromptText(speechText);
        media.setParamsJson(XuJsonUtil.toJsonString(params));
        media.setCandidateNo(1);
        media.setSelected(YES);
        media.setAssetStatus(STATUS_SELECTED);
        media.setDelFlag(DEL_FLAG_NORMAL);
        fillCreateAudit(media);
        mediaAssetMapper.insert(media);
        return media;
    }

    private AiVideoProjectPo requireProject(Long projectId) {
        AiVideoProjectPo project = projectMapper.selectById(projectId);
        if (project == null || !Integer.valueOf(DEL_FLAG_NORMAL).equals(project.getDelFlag())) {
            throw new BusinessException("项目不存在或已删除");
        }
        return project;
    }

    private AiVideoShotPo requireShot(AiVideoProjectPo project, Long shotId) {
        AiVideoShotPo shot = shotMapper.selectById(shotId);
        if (shot == null
                || !Objects.equals(project.getProjectId(), shot.getProjectId())
                || !Integer.valueOf(DEL_FLAG_NORMAL).equals(shot.getDelFlag())) {
            throw new BusinessException("分镜不存在或不属于当前项目");
        }
        return shot;
    }

    private String resolveSpeechText(AivideoShotTtsGenerateDto dto, AiVideoShotPo shot) {
        String custom = trimToNull(dto.getText());
        if (StringUtils.hasText(custom)) {
            return custom;
        }
        StringBuilder builder = new StringBuilder();
        appendSpeech(builder, shot.getDialogue());
        if (isAudibleVoiceOver(shot.getVoiceOver())) {
            appendSpeech(builder, shot.getVoiceOver());
        }
        String text = builder.toString().trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("当前分镜没有可合成的对白或旁白");
        }
        return text;
    }

    private boolean isAudibleVoiceOver(String value) {
        String text = trimToNull(value);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return !(text.contains("心声")
                || text.contains("内心")
                || text.contains("心理")
                || text.contains("脑海里")
                || text.contains("心里"));
    }

    private void appendSpeech(StringBuilder builder, String value) {
        String text = trimToNull(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(text);
    }

    private void clearSelectedShotTts(AiVideoProjectPo project, AiVideoShotPo shot) {
        mediaAssetMapper.update(null, new UpdateWrapper<AiVideoMediaAssetPo>()
                .eq("project_id", project.getProjectId())
                .eq("asset_type", ASSET_SHOT_TTS_AUDIO)
                .eq("biz_type", BIZ_SHOT)
                .eq("biz_id", shot.getShotId())
                .set("selected", NO)
                .set("asset_status", STATUS_READY)
                .set("update_by", resolveOperator())
                .set("update_time", now()));
    }

    private void fillCreateAudit(AiVideoMediaAssetPo media) {
        String operator = resolveOperator();
        LocalDateTime current = now();
        media.setCreateBy(operator);
        media.setCreateTime(current);
        media.setUpdateBy(operator);
        media.setUpdateTime(current);
    }

    private String toFilePublicPath(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException("文件地址为空");
        }
        String value = fileUrl.trim();
        if (value.startsWith("/file/public/")) {
            return value;
        }
        int index = value.indexOf("/file/public/");
        if (index >= 0) {
            return value.substring(index);
        }
        return value;
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
