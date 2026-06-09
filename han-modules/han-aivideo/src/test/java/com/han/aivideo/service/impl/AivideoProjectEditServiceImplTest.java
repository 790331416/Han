package com.han.aivideo.service.impl;

import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.vo.AivideoProjectEditPreflightVo;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import com.han.aivideo.service.AivideoDirectEditProvider;
import com.han.common.core.exception.BusinessException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AivideoProjectEditServiceImplTest {

    @Test
    void preflightRequiresEveryApprovedShotToHaveSelectedVideo() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("喵小萌阳光账本");
        project.setTenantId(9L);
        project.setDefaultRatio("9:16");
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoShotPo shot1 = approvedShot(11L, 1, 5, 101L);
        AiVideoShotPo shot2 = approvedShot(12L, 2, 6, null);
        when(shotMapper.selectList(any())).thenReturn(List.of(shot1, shot2));

        AiVideoMediaAssetPo video1 = selectedVideo(101L, 11L, "/file/public/aivideo/shot-1.mp4");
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of(video1));

        AivideoProjectEditServiceImpl service = new AivideoProjectEditServiceImpl(
                projectMapper, null, shotMapper, mediaAssetMapper, taskMapper, null, "https://han.scavengers.cn");

        AivideoProjectEditPreflightVo preflight = service.previewProjectEdit(1L);

        assertFalse(preflight.getReady());
        assertEquals(1, preflight.getClipCount());
        assertEquals(1, preflight.getMissingShotCount());
        assertEquals(5, preflight.getTotalDurationSec());
        assertTrue(preflight.getErrors().get(0).contains("第2镜"));
        assertEquals(101L, preflight.getClips().get(0).getVideoMediaId());
    }

    @Test
    void editParamUsesSelectedVideosInShotOrderWithContinuousTargetTime() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("剪辑测试");
        project.setTenantId(9L);
        project.setDefaultRatio("9:16");
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        when(shotMapper.selectList(any())).thenReturn(List.of(
                approvedShot(12L, 2, 6, 102L),
                approvedShot(11L, 1, 5, 101L)
        ));
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of(
                selectedVideo(101L, 11L, "/file/public/aivideo/shot-1.mp4"),
                selectedVideo(102L, 12L, "/file/public/aivideo/shot-2.mp4")
        ));

        AivideoProjectEditServiceImpl service = new AivideoProjectEditServiceImpl(
                projectMapper, null, shotMapper, mediaAssetMapper, taskMapper, null, "https://han.scavengers.cn");

        String editParam = service.buildDirectEditParamForTest(1L, "剪辑测试成片", true);

        assertTrue(editParam.contains("\"Width\":720"));
        assertTrue(editParam.contains("\"Height\":1280"));
        assertTrue(editParam.contains("\"Source\":\"https://han.scavengers.cn/file/public/aivideo/shot-1.mp4\""));
        assertTrue(editParam.contains("\"Source\":\"https://han.scavengers.cn/file/public/aivideo/shot-2.mp4\""));
        assertTrue(editParam.contains("\"TargetTime\":[0,5000]"));
        assertTrue(editParam.contains("\"TargetTime\":[5000,11000]"));
        assertTrue(editParam.contains("\"DisableAudio\":false"));
    }

    @Test
    void editParamUsesProjectSettingResolutionForLandscapeCanvas() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoProjectSettingMapper settingMapper = mock(AiVideoProjectSettingMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("edit-test");
        project.setTenantId(9L);
        project.setDefaultRatio("9:16");
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoProjectSettingPo projectSetting = new AiVideoProjectSettingPo();
        projectSetting.setProjectId(1L);
        projectSetting.setDefaultRatio("16:9");
        projectSetting.setDefaultResolution("720p");
        when(settingMapper.selectOne(any())).thenReturn(projectSetting);

        when(shotMapper.selectList(any())).thenReturn(List.of(approvedShot(11L, 1, 5, 101L)));
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of(
                selectedVideo(101L, 11L, "/file/public/aivideo/shot-1.mp4")
        ));

        AivideoProjectEditServiceImpl service = new AivideoProjectEditServiceImpl(
                projectMapper, settingMapper, shotMapper, mediaAssetMapper, taskMapper, null, "https://han.scavengers.cn");

        String editParam = service.buildDirectEditParamForTest(1L, "edit-test-final", true);

        assertTrue(editParam.contains("\"Width\":1280"));
        assertTrue(editParam.contains("\"Height\":720"));
    }

    @Test
    void editParamRejectsRelativeSelectedVideoWhenPublicOriginMissing() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("剪辑测试");
        project.setTenantId(9L);
        project.setDefaultRatio("9:16");
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        when(shotMapper.selectList(any())).thenReturn(List.of(approvedShot(11L, 1, 5, 101L)));
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of(
                selectedVideo(101L, 11L, "/file/public/static-rustfs/shot-1.mp4")
        ));

        AivideoProjectEditServiceImpl service = new AivideoProjectEditServiceImpl(
                projectMapper, null, shotMapper, mediaAssetMapper, taskMapper, null, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.buildDirectEditParamForTest(1L, "剪辑测试成片", true));

        assertTrue(exception.getMessage().contains("公网可访问地址"));
        assertTrue(exception.getMessage().contains("第1镜"));
    }

    @Test
    void editParamKeepsAbsoluteSelectedVideoWithoutPublicOrigin() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("剪辑测试");
        project.setTenantId(9L);
        project.setDefaultRatio("9:16");
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        when(shotMapper.selectList(any())).thenReturn(List.of(approvedShot(11L, 1, 5, 101L)));
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of(
                selectedVideo(101L, 11L, "https://media.scavengers.cn/file/public/static-rustfs/shot-1.mp4")
        ));

        AivideoProjectEditServiceImpl service = new AivideoProjectEditServiceImpl(
                projectMapper, null, shotMapper, mediaAssetMapper, taskMapper, null, null);

        String editParam = service.buildDirectEditParamForTest(1L, "剪辑测试成片", true);

        assertTrue(editParam.contains("\"Source\":\"https://media.scavengers.cn/file/public/static-rustfs/shot-1.mp4\""));
    }

    @Test
    void successfulPollStoresPlayableEditVideoUrlWhenVodReturnsOne() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);
        AivideoDirectEditProvider provider = mock(AivideoDirectEditProvider.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("剪辑测试");
        project.setTenantId(9L);
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoGenerationTaskPo task = new AiVideoGenerationTaskPo();
        task.setTaskId(31L);
        task.setProjectId(1L);
        task.setTenantId(9L);
        task.setTaskType("PROJECT_EDIT_VIDEO");
        task.setBizType("PROJECT");
        task.setBizId(1L);
        task.setProviderTaskId("req-31");
        task.setDelFlag(0);
        when(taskMapper.selectById(31L)).thenReturn(task);
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of());
        when(provider.progress("req-31")).thenReturn(100);
        when(provider.result("req-31")).thenReturn(new AivideoDirectEditProvider.EditResult("req-31", "success", "", "vid-31"));
        when(provider.playUrl("vid-31")).thenReturn("https://vod.example.com/final.mp4");

        AivideoProjectEditServiceImpl service = new AivideoProjectEditServiceImpl(
                projectMapper, null, shotMapper, mediaAssetMapper, taskMapper, provider, "https://han.scavengers.cn");

        service.pollProjectEditTask(1L, 31L);

        ArgumentCaptor<AiVideoMediaAssetPo> mediaCaptor = ArgumentCaptor.forClass(AiVideoMediaAssetPo.class);
        verify(mediaAssetMapper).insert(mediaCaptor.capture());
        AiVideoMediaAssetPo inserted = mediaCaptor.getValue();
        assertEquals("PROJECT_EDIT_VIDEO", inserted.getAssetType());
        assertEquals("https://vod.example.com/final.mp4", inserted.getFileUrl());
        assertTrue(inserted.getParamsJson().contains("\"outputVid\":\"vid-31\""));
    }

    @Test
    void successfulPollKeepsVodMarkerWhenPlayDomainIsMissing() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);
        AivideoDirectEditProvider provider = mock(AivideoDirectEditProvider.class);

        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("edit-test");
        project.setTenantId(9L);
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoGenerationTaskPo task = new AiVideoGenerationTaskPo();
        task.setTaskId(32L);
        task.setProjectId(1L);
        task.setTenantId(9L);
        task.setTaskType("PROJECT_EDIT_VIDEO");
        task.setBizType("PROJECT");
        task.setBizId(1L);
        task.setProviderTaskId("req-32");
        task.setDelFlag(0);
        when(taskMapper.selectById(32L)).thenReturn(task);
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of());
        when(provider.progress("req-32")).thenReturn(100);
        when(provider.result("req-32")).thenReturn(new AivideoDirectEditProvider.EditResult("req-32", "success", "", "vid-32"));
        when(provider.playUrl("vid-32")).thenThrow(new BusinessException("ResourceNotFound.NoAvailableDomain: No valid domain is configured"));

        AivideoProjectEditServiceImpl service = new AivideoProjectEditServiceImpl(
                projectMapper, null, shotMapper, mediaAssetMapper, taskMapper, provider, "https://han.scavengers.cn");

        AiVideoGenerationTaskPo polled = service.pollProjectEditTask(1L, 32L);

        assertEquals("SUCCESS", polled.getTaskStatus());
        ArgumentCaptor<AiVideoMediaAssetPo> mediaCaptor = ArgumentCaptor.forClass(AiVideoMediaAssetPo.class);
        verify(mediaAssetMapper).insert(mediaCaptor.capture());
        AiVideoMediaAssetPo inserted = mediaCaptor.getValue();
        assertEquals("vod://vid-32", inserted.getFileUrl());
        assertTrue(inserted.getParamsJson().contains("\"outputVid\":\"vid-32\""));
        assertTrue(inserted.getParamsJson().contains("playUrlError"));
        assertTrue(inserted.getParamsJson().contains("NoAvailableDomain"));
    }

    private static AiVideoShotPo approvedShot(Long shotId, int shotNo, int durationSec, Long videoMediaId) {
        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setProjectId(1L);
        shot.setTenantId(9L);
        shot.setShotId(shotId);
        shot.setEpisodeNo(1);
        shot.setShotNo(shotNo);
        shot.setDurationSec(durationSec);
        shot.setVideoMediaId(videoMediaId);
        shot.setTransitionBeforeType(shotNo == 1 ? "OPENING" : "INSERT");
        shot.setTransitionBeforeDesc(shotNo == 1 ? "开场" : "同场景插入镜头");
        shot.setTransitionEffect("hard_cut");
        shot.setStitchGroupNo(1);
        shot.setActionDesc("第" + shotNo + "镜动作");
        shot.setConfirmStatus("APPROVED");
        shot.setDelFlag(0);
        return shot;
    }

    private static AiVideoMediaAssetPo selectedVideo(Long mediaId, Long shotId, String fileUrl) {
        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        media.setProjectId(1L);
        media.setTenantId(9L);
        media.setMediaId(mediaId);
        media.setAssetType("SHOT_VIDEO");
        media.setBizType("SHOT");
        media.setBizId(shotId);
        media.setFileUrl(fileUrl);
        media.setSelected("1");
        media.setAssetStatus("SELECTED");
        media.setDelFlag(0);
        return media;
    }
}
