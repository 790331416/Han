package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.han.aivideo.domain.dto.AivideoMediaRegisterDto;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoPropMapper;
import com.han.aivideo.mapper.AiVideoReviewRecordMapper;
import com.han.aivideo.mapper.AiVideoSceneMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AivideoSceneImageServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiVideoMediaAssetPo.class);
    }

    @Test
    void registerProjectBgmAudioDefaultsToProjectScopeAndSelectsIt() {
        TestContext ctx = new TestContext();
        AiVideoProjectPo project = project(1L);
        when(ctx.projectMapper.selectById(1L)).thenReturn(project);
        when(ctx.mediaAssetMapper.selectList(any())).thenReturn(List.of());
        AtomicReference<AiVideoMediaAssetPo> inserted = captureInsertedMedia(ctx.mediaAssetMapper, 901L);
        when(ctx.mediaAssetMapper.selectById(901L)).thenAnswer(invocation -> inserted.get());

        AivideoMediaRegisterDto dto = new AivideoMediaRegisterDto();
        dto.setProjectId(1L);
        dto.setAssetType("PROJECT_BGM_AUDIO");
        dto.setFileUrl("/file/public/aivideo/bgm.mp3");
        dto.setPromptText("quiet classroom bgm");

        AivideoMediaAssetVo vo = ctx.service.registerMedia(dto);

        assertEquals(901L, vo.getMediaId());
        assertEquals("PROJECT_BGM_AUDIO", vo.getAssetType());
        assertEquals("PROJECT", inserted.get().getBizType());
        assertEquals(1L, inserted.get().getBizId());
        assertEquals("1", inserted.get().getSelected());
        assertEquals("SELECTED", inserted.get().getAssetStatus());
        verify(ctx.mediaAssetMapper).update(any(), any());
        verify(ctx.mediaAssetMapper).updateById(inserted.get());
    }

    @Test
    void registerPropImageLocksThePropAsset() {
        TestContext ctx = new TestContext();
        AiVideoProjectPo project = project(1L);
        AiVideoPropPo prop = new AiVideoPropPo();
        prop.setProjectId(1L);
        prop.setPropId(22L);
        prop.setDelFlag(0);
        when(ctx.projectMapper.selectById(1L)).thenReturn(project);
        when(ctx.propMapper.selectById(22L)).thenReturn(prop);
        when(ctx.mediaAssetMapper.selectList(any())).thenReturn(List.of());
        AtomicReference<AiVideoMediaAssetPo> inserted = captureInsertedMedia(ctx.mediaAssetMapper, 902L);
        when(ctx.mediaAssetMapper.selectById(902L)).thenAnswer(invocation -> inserted.get());

        AivideoMediaRegisterDto dto = new AivideoMediaRegisterDto();
        dto.setProjectId(1L);
        dto.setAssetType("PROP_IMAGE");
        dto.setBizId(22L);
        dto.setFileUrl("/file/public/aivideo/blue-box.png");

        ctx.service.registerMedia(dto);

        ArgumentCaptor<AiVideoPropPo> propCaptor = ArgumentCaptor.forClass(AiVideoPropPo.class);
        verify(ctx.propMapper).updateById(propCaptor.capture());
        assertEquals(902L, propCaptor.getValue().getLockedMediaId());
        assertEquals("PROP", inserted.get().getBizType());
        assertEquals(22L, inserted.get().getBizId());
    }

    @Test
    void registerShotSfxAudioSelectsWithoutReplacingShotVideo() {
        TestContext ctx = new TestContext();
        AiVideoProjectPo project = project(1L);
        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setProjectId(1L);
        shot.setShotId(33L);
        shot.setVideoMediaId(777L);
        shot.setDelFlag(0);
        when(ctx.projectMapper.selectById(1L)).thenReturn(project);
        when(ctx.shotMapper.selectById(33L)).thenReturn(shot);
        when(ctx.mediaAssetMapper.selectList(any())).thenReturn(List.of());
        AtomicReference<AiVideoMediaAssetPo> inserted = captureInsertedMedia(ctx.mediaAssetMapper, 903L);
        when(ctx.mediaAssetMapper.selectById(903L)).thenAnswer(invocation -> inserted.get());

        AivideoMediaRegisterDto dto = new AivideoMediaRegisterDto();
        dto.setProjectId(1L);
        dto.setAssetType("SHOT_SFX_AUDIO");
        dto.setBizId(33L);
        dto.setFileUrl("https://media.example.com/sfx.mp3");

        ctx.service.registerMedia(dto);

        verify(ctx.shotMapper, never()).updateById(any(AiVideoShotPo.class));
        assertEquals("SHOT", inserted.get().getBizType());
        assertEquals(33L, inserted.get().getBizId());
        assertEquals("https://media.example.com/sfx.mp3", inserted.get().getFileUrl());
    }

    private static AtomicReference<AiVideoMediaAssetPo> captureInsertedMedia(AiVideoMediaAssetMapper mapper,
                                                                             Long mediaId) {
        AtomicReference<AiVideoMediaAssetPo> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            AiVideoMediaAssetPo media = invocation.getArgument(0);
            media.setMediaId(mediaId);
            inserted.set(media);
            return 1;
        }).when(mapper).insert(any(AiVideoMediaAssetPo.class));
        return inserted;
    }

    private static AiVideoProjectPo project(Long projectId) {
        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(projectId);
        project.setTenantId(9L);
        project.setDelFlag(0);
        return project;
    }

    private static class TestContext {
        private final AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        private final AiVideoProjectSettingMapper settingMapper = mock(AiVideoProjectSettingMapper.class);
        private final AiVideoSceneMapper sceneMapper = mock(AiVideoSceneMapper.class);
        private final AiVideoCharacterMapper characterMapper = mock(AiVideoCharacterMapper.class);
        private final AiVideoPropMapper propMapper = mock(AiVideoPropMapper.class);
        private final AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        private final AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);
        private final AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        private final AiVideoReviewRecordMapper reviewRecordMapper = mock(AiVideoReviewRecordMapper.class);
        private final AivideoSceneImageServiceImpl service = new AivideoSceneImageServiceImpl(
                projectMapper,
                settingMapper,
                sceneMapper,
                characterMapper,
                propMapper,
                shotMapper,
                taskMapper,
                mediaAssetMapper,
                reviewRecordMapper,
                null,
                null,
                mock(TransactionTemplate.class));
    }
}
