package com.han.aivideo.service.impl;

import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AivideoProjectServiceImplTest {

    @Test
    void updateProjectPersistsImageAndVideoCandidateCounts() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoProjectSettingMapper settingMapper = mock(AiVideoProjectSettingMapper.class);
        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("喵小萌阳光账本");
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoProjectSettingPo setting = new AiVideoProjectSettingPo();
        setting.setSettingId(9L);
        setting.setProjectId(1L);
        setting.setDefaultRatio("9:16");
        setting.setDefaultShotDuration(5);
        setting.setImageCandidateCount(2);
        setting.setVideoCandidateCount(1);
        setting.setPreviewMode("1");
        setting.setParamsJson("{}");
        when(settingMapper.selectOne(any())).thenReturn(setting);

        AivideoProjectServiceImpl service = new AivideoProjectServiceImpl(
                projectMapper, settingMapper, null, null, null, null, null, null);
        AivideoProjectDto dto = new AivideoProjectDto();
        dto.setProjectId(1L);
        dto.setProjectName("喵小萌阳光账本");
        dto.setDefaultRatio("9:16");
        dto.setDefaultShotDuration(5);
        dto.setCandidateImageCount(1);
        dto.setVideoCandidateCount(2);

        service.updateProject(dto);

        ArgumentCaptor<AiVideoProjectSettingPo> settingCaptor =
                ArgumentCaptor.forClass(AiVideoProjectSettingPo.class);
        verify(settingMapper).updateById(settingCaptor.capture());
        assertEquals(1, settingCaptor.getValue().getImageCandidateCount());
        assertEquals(2, settingCaptor.getValue().getVideoCandidateCount());
    }
}
