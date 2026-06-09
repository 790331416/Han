package com.han.aivideo.service.impl;

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
import com.han.common.core.domain.R;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

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
                projectMapper, shotMapper, mediaAssetMapper, fileServiceClient, ttsProvider);

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
}
