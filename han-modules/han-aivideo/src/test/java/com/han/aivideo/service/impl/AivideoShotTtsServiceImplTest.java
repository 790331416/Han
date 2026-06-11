package com.han.aivideo.service.impl;

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
import com.han.common.core.domain.R;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AivideoShotTtsServiceImplTest {

    @Test
    void generateShotTtsStoresSelectedShotTtsAudioAsset() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoCharacterMapper characterMapper = mock(AiVideoCharacterMapper.class);
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        AivideoTtsProvider ttsProvider = mock(AivideoTtsProvider.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setTenantId(9L);
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setShotId(11L);
        shot.setProjectId(1L);
        shot.setTenantId(9L);
        shot.setShotNo(3);
        shot.setDialogue("喵小萌：我们开始吧！");
        shot.setVoiceOver("画外旁白：她把预算清单放在桌上。");
        shot.setDelFlag(0);
        when(shotMapper.selectById(11L)).thenReturn(shot);

        when(ttsProvider.synthesize(any())).thenReturn(new AivideoTtsProvider.TtsAudio(
                "volc-v1-req", "audio/mpeg", "mp3", 1800,
                "fake-audio".getBytes(StandardCharsets.UTF_8)));
        when(fileServiceClient.upload(any())).thenReturn(R.ok(new FileDTO(301L, "shot-3-tts.mp3",
                "/file/public/aivideo/shot-3-tts.mp3")));

        AivideoShotTtsServiceImpl service = new AivideoShotTtsServiceImpl(
                projectMapper, shotMapper, mediaAssetMapper, characterMapper, fileServiceClient, ttsProvider);

        AivideoShotTtsGenerateDto dto = new AivideoShotTtsGenerateDto();
        dto.setProjectId(1L);
        dto.setShotId(11L);
        dto.setVoiceType("BV001_24k_streaming");

        AiVideoMediaAssetPo media = service.generateShotTts(dto);

        assertEquals("SHOT_TTS_AUDIO", media.getAssetType());
        assertEquals("SHOT", media.getBizType());
        assertEquals(11L, media.getBizId());
        assertEquals(301L, media.getFileId());
        assertEquals("/file/public/aivideo/shot-3-tts.mp3", media.getFileUrl());
        assertEquals("1", media.getSelected());
        assertTrue(media.getPromptText().contains("喵小萌"));
        assertTrue(media.getPromptText().contains("画外旁白"));
        assertTrue(media.getParamsJson().contains("\"voiceType\":\"BV001_24k_streaming\""));
        assertTrue(media.getParamsJson().contains("\"durationMs\":1800"));

        verify(mediaAssetMapper).update(any(), any());
        ArgumentCaptor<AiVideoMediaAssetPo> mediaCaptor = ArgumentCaptor.forClass(AiVideoMediaAssetPo.class);
        verify(mediaAssetMapper).insert(mediaCaptor.capture());
        assertEquals(media.getFileUrl(), mediaCaptor.getValue().getFileUrl());
    }

    @Test
    void generateShotTtsInheritsVoiceAndTimingFromCharacterAsset() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoCharacterMapper characterMapper = mock(AiVideoCharacterMapper.class);
        FileServiceClient fileServiceClient = mock(FileServiceClient.class);
        AivideoTtsProvider ttsProvider = mock(AivideoTtsProvider.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setTenantId(9L);
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setShotId(11L);
        shot.setProjectId(1L);
        shot.setTenantId(9L);
        shot.setShotNo(3);
        shot.setDurationSec(5);
        shot.setCharacterIds("21");
        shot.setDialogue("喵小萌：开始吧！");
        shot.setTtsStartMs(700);
        shot.setTtsEndMs(2700);
        shot.setDelFlag(0);
        when(shotMapper.selectById(11L)).thenReturn(shot);

        AiVideoCharacterPo character = new AiVideoCharacterPo();
        character.setCharacterId(21L);
        character.setProjectId(1L);
        character.setTenantId(9L);
        character.setCharacterName("喵小萌");
        character.setVoiceType("voice_miaomeng_q");
        character.setVoiceName("喵小萌Q版少女声");
        character.setVoiceSpeedRatio(new BigDecimal("0.96"));
        character.setVoiceVolumeRatio(new BigDecimal("1.05"));
        character.setVoicePitchRatio(new BigDecimal("1.08"));
        character.setDelFlag(0);
        when(characterMapper.selectList(any())).thenReturn(List.of(character));

        when(ttsProvider.synthesize(any())).thenReturn(new AivideoTtsProvider.TtsAudio(
                "volc-v1-req", "audio/mpeg", "mp3", 1600,
                "fake-audio".getBytes(StandardCharsets.UTF_8)));
        when(fileServiceClient.upload(any())).thenReturn(R.ok(new FileDTO(302L, "shot-3-tts.mp3",
                "/file/public/aivideo/shot-3-tts.mp3")));

        AivideoShotTtsServiceImpl service = new AivideoShotTtsServiceImpl(
                projectMapper, shotMapper, mediaAssetMapper, characterMapper, fileServiceClient, ttsProvider);

        AivideoShotTtsGenerateDto dto = new AivideoShotTtsGenerateDto();
        dto.setProjectId(1L);
        dto.setShotId(11L);

        AiVideoMediaAssetPo media = service.generateShotTts(dto);

        ArgumentCaptor<AivideoTtsProvider.TtsRequest> requestCaptor =
                ArgumentCaptor.forClass(AivideoTtsProvider.TtsRequest.class);
        verify(ttsProvider).synthesize(requestCaptor.capture());
        AivideoTtsProvider.TtsRequest request = requestCaptor.getValue();
        assertEquals("voice_miaomeng_q", request.voiceType());
        assertEquals(new BigDecimal("0.96"), request.speedRatio());
        assertEquals(new BigDecimal("1.05"), request.volumeRatio());
        assertEquals(new BigDecimal("1.08"), request.pitchRatio());

        assertTrue(media.getParamsJson().contains("\"speaker\":\"喵小萌\""));
        assertTrue(media.getParamsJson().contains("\"voiceType\":\"voice_miaomeng_q\""));
        assertTrue(media.getParamsJson().contains("\"voiceName\":\"喵小萌Q版少女声\""));
        assertTrue(media.getParamsJson().contains("\"ttsStartMs\":700"));
        assertTrue(media.getParamsJson().contains("\"ttsEndMs\":2700"));
        verify(shotMapper).updateById(any(AiVideoShotPo.class));
    }
}
