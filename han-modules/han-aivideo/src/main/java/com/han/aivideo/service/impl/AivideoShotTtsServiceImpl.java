package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.han.api.file.FileServiceClient;
import com.han.api.file.domain.FileDTO;
import com.han.aivideo.domain.dto.AivideoShotTtsGenerateDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final AiVideoCharacterMapper characterMapper;
    private final FileServiceClient fileServiceClient;
    private final AivideoTtsProvider ttsProvider;

    @Autowired
    public AivideoShotTtsServiceImpl(AiVideoProjectMapper projectMapper,
                                     AiVideoShotMapper shotMapper,
                                     AiVideoMediaAssetMapper mediaAssetMapper,
                                     AiVideoCharacterMapper characterMapper,
                                     FileServiceClient fileServiceClient,
                                     AivideoTtsProvider ttsProvider) {
        this.projectMapper = projectMapper;
        this.shotMapper = shotMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.characterMapper = characterMapper;
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
        String speaker = firstText(dto.getSpeaker(), inferSpeakerName(speechText), shot.getTtsSpeaker());
        AiVideoCharacterPo speakerCharacter = resolveSpeakerCharacter(project, shot, speaker);
        if (!StringUtils.hasText(speaker) && speakerCharacter != null) {
            speaker = firstText(speakerCharacter.getCharacterName());
        }
        String voiceType = firstText(dto.getVoiceType(), shot.getTtsVoiceType(),
                speakerCharacter != null ? speakerCharacter.getVoiceType() : null,
                DEFAULT_VOICE_TYPE);
        BigDecimal speedRatio = firstDecimal(dto.getSpeedRatio(),
                speakerCharacter != null ? speakerCharacter.getVoiceSpeedRatio() : null);
        BigDecimal volumeRatio = firstDecimal(dto.getVolumeRatio(),
                speakerCharacter != null ? speakerCharacter.getVoiceVolumeRatio() : null);
        BigDecimal pitchRatio = firstDecimal(dto.getPitchRatio(),
                speakerCharacter != null ? speakerCharacter.getVoicePitchRatio() : null);
        String requestId = "aivideo-tts-" + shot.getShotId() + "-" + UUID.randomUUID();

        AivideoTtsProvider.TtsAudio audio = ttsProvider.synthesize(new AivideoTtsProvider.TtsRequest(
                speechText,
                voiceType,
                speedRatio,
                volumeRatio,
                pitchRatio,
                requestId));
        if (audio == null || audio.bytes() == null || audio.bytes().length == 0) {
            throw new BusinessException("语音合成未返回有效音频");
        }

        int shotDurationMs = Math.max(1000, firstInteger(shot.getDurationSec(), 5) * 1000);
        int ttsStartMs = clamp(firstInteger(dto.getTtsStartMs(), firstInteger(shot.getTtsStartMs(), 0)),
                0, shotDurationMs - 1);
        int generatedDurationMs = firstInteger(audio.durationMs(), shotDurationMs - ttsStartMs);
        int defaultEndMs = Math.min(shotDurationMs, ttsStartMs + Math.max(1, generatedDurationMs));
        int ttsEndMs = clamp(firstInteger(dto.getTtsEndMs(), firstInteger(shot.getTtsEndMs(), defaultEndMs)),
                ttsStartMs + 1, shotDurationMs);

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
        updateShotTtsProfile(shot, speaker, voiceType, ttsStartMs, ttsEndMs);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("provider", "VOLC_TTS");
        params.put("providerRequestId", firstText(audio.providerRequestId(), requestId));
        params.put("speaker", firstText(speaker));
        params.put("voiceType", voiceType);
        params.put("voiceName", speakerCharacter != null ? firstText(speakerCharacter.getVoiceName()) : "");
        params.put("voiceMode", speakerCharacter != null ? firstText(speakerCharacter.getVoiceMode()) : "");
        params.put("ttsStartMs", ttsStartMs);
        params.put("ttsEndMs", ttsEndMs);
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

    private AiVideoCharacterPo resolveSpeakerCharacter(AiVideoProjectPo project, AiVideoShotPo shot, String speaker) {
        if (characterMapper == null) {
            return null;
        }
        List<AiVideoCharacterPo> characters = characterMapper.selectList(new LambdaQueryWrapper<AiVideoCharacterPo>()
                .eq(AiVideoCharacterPo::getProjectId, project.getProjectId())
                .eq(AiVideoCharacterPo::getDelFlag, DEL_FLAG_NORMAL));
        if (characters == null || characters.isEmpty()) {
            return null;
        }
        String normalizedSpeaker = trimToNull(speaker);
        if (StringUtils.hasText(normalizedSpeaker)) {
            for (AiVideoCharacterPo character : characters) {
                if (normalizedSpeaker.equals(firstText(character.getCharacterName()))) {
                    return character;
                }
            }
        }
        List<Long> characterIds = parseCharacterIds(shot.getCharacterIds());
        if (!characterIds.isEmpty()) {
            for (AiVideoCharacterPo character : characters) {
                if (characterIds.contains(character.getCharacterId())) {
                    return character;
                }
            }
        }
        return characters.size() == 1 ? characters.get(0) : null;
    }

    private List<Long> parseCharacterIds(String value) {
        List<Long> ids = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return ids;
        }
        for (String item : value.split("[,，;；\\s]+")) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(item.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private String inferSpeakerName(String speechText) {
        String text = trimToNull(speechText);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String firstLine = text.split("\\R", 2)[0].trim();
        int zh = firstLine.indexOf('：');
        int en = firstLine.indexOf(':');
        int index;
        if (zh >= 0 && en >= 0) {
            index = Math.min(zh, en);
        } else {
            index = Math.max(zh, en);
        }
        if (index <= 0 || index > 32) {
            return "";
        }
        return firstLine.substring(0, index).trim();
    }

    private void updateShotTtsProfile(AiVideoShotPo shot, String speaker, String voiceType,
                                      int ttsStartMs, int ttsEndMs) {
        shot.setTtsSpeaker(firstText(speaker));
        shot.setTtsVoiceType(firstText(voiceType));
        shot.setTtsStartMs(ttsStartMs);
        shot.setTtsEndMs(ttsEndMs);
        shot.setUpdateBy(resolveOperator());
        shot.setUpdateTime(now());
        shotMapper.updateById(shot);
    }

    private BigDecimal firstDecimal(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int firstInteger(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
