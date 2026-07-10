package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.han.aivideo.domain.dto.AivideoMediaRegisterDto;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.common.core.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void previewMediaRejectsUncontrolledExternalUrl() {
        TestContext ctx = new TestContext();
        AiVideoMediaAssetPo media = media(904L, "SHOT_SFX_AUDIO", "http://127.0.0.1:8080/internal");
        when(ctx.mediaAssetMapper.selectById(904L)).thenReturn(media);

        BusinessException exception = assertThrows(BusinessException.class, () -> ctx.service.previewMedia(904L));

        assertEquals("媒体资源地址不是受控文件路径", exception.getMessage());
    }

    @Test
    void canonicalPublicFileUrlResolvesToControlledPath() throws Exception {
        TestContext ctx = new TestContext();
        var method = AivideoSceneImageServiceImpl.class.getDeclaredMethod("toFilePublicPath", String.class);
        method.setAccessible(true);

        Object path = method.invoke(ctx.service,
                "https://media.example.com/file/public/static-rustfs/scene%20one.png");

        assertEquals("/file/public/static-rustfs/scene%20one.png", path);
    }

    @Test
    void previewMediaRejectsPublicPathTraversal() {
        TestContext ctx = new TestContext();
        AiVideoMediaAssetPo media = media(905L, "SCENE_IMAGE",
                "/file/public/static-rustfs/../actuator-health");
        when(ctx.mediaAssetMapper.selectById(905L)).thenReturn(media);

        BusinessException exception = assertThrows(BusinessException.class, () -> ctx.service.previewMedia(905L));

        assertEquals("媒体资源地址不是受控文件路径", exception.getMessage());
    }

    @Test
    void previewPublicMediaRejectsAudioEvenWhenTenantPolicyIsPublic() {
        TestContext ctx = new TestContext();
        AiVideoMediaAssetPo media = media(906L, "SHOT_SFX_AUDIO",
                "/file/public/static-rustfs/shot-sfx.mp3");
        media.setSelected("1");
        when(ctx.mediaAssetMapper.selectById(906L)).thenReturn(media);
        when(ctx.settingMapper.selectOne(any())).thenReturn(publicSetting());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ctx.service.previewPublicMedia(906L));

        assertEquals("媒体资源不是已确认的公开参考图", exception.getMessage());
    }

    @Test
    void previewPublicMediaRejectsUnselectedImage() {
        TestContext ctx = new TestContext();
        AiVideoMediaAssetPo media = media(907L, "SCENE_IMAGE",
                "/file/public/static-rustfs/scene-candidate.png");
        media.setSelected("0");
        media.setAssetStatus("READY");
        when(ctx.mediaAssetMapper.selectById(907L)).thenReturn(media);
        when(ctx.settingMapper.selectOne(any())).thenReturn(publicSetting());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ctx.service.previewPublicMedia(907L));

        assertEquals("媒体资源不是已确认的公开参考图", exception.getMessage());
    }

    private static AiVideoMediaAssetPo media(Long mediaId, String assetType, String fileUrl) {
        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        media.setMediaId(mediaId);
        media.setTenantId(9L);
        media.setAssetType(assetType);
        media.setFileUrl(fileUrl);
        media.setDelFlag(0);
        return media;
    }

    private static AiVideoProjectSettingPo publicSetting() {
        AiVideoProjectSettingPo setting = new AiVideoProjectSettingPo();
        setting.setTenantId(9L);
        setting.setMediaAccessPolicy("PUBLIC");
        return setting;
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
