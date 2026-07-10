package com.han.aivideo.service.impl;

import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoSourceDocumentPo;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoSourceDocumentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AivideoProjectServiceImplTest {

    @Test
    void saveDocumentUpdatesExistingUnconfirmedDocumentWhenDocumentIdProvided() {
        AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        AiVideoSourceDocumentMapper documentMapper = mock(AiVideoSourceDocumentMapper.class);
        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectId(1L);
        project.setProjectName("短剧项目");
        project.setCurrentStage("DOCUMENT_SAVED");
        project.setDelFlag(0);
        when(projectMapper.selectById(1L)).thenReturn(project);

        AiVideoSourceDocumentPo document = new AiVideoSourceDocumentPo();
        document.setDocumentId(2L);
        document.setProjectId(1L);
        document.setConfirmed("0");
        document.setDelFlag(0);
        when(documentMapper.selectById(2L)).thenReturn(document);

        AivideoProjectServiceImpl service = new AivideoProjectServiceImpl(
                projectMapper, null, documentMapper, null, null, null, null, null);
        AivideoDocumentSaveDto dto = new AivideoDocumentSaveDto();
        dto.setProjectId(1L);
        dto.setDocumentId(2L);
        dto.setSourceType("MARKDOWN");
        dto.setFileName("rework.md");
        dto.setRawText("重新调整后的原文");

        Long documentId = service.saveDocument(dto);

        ArgumentCaptor<AiVideoSourceDocumentPo> documentCaptor =
                ArgumentCaptor.forClass(AiVideoSourceDocumentPo.class);
        verify(documentMapper).updateById(documentCaptor.capture());
        verify(documentMapper, never()).insert(org.mockito.ArgumentMatchers.<AiVideoSourceDocumentPo>any());
        assertEquals(2L, documentId);
        assertEquals("重新调整后的原文", documentCaptor.getValue().getRawText());
        assertEquals("MARKDOWN", documentCaptor.getValue().getSourceType());
        assertEquals("rework.md", documentCaptor.getValue().getFileName());
        assertEquals(8L, documentCaptor.getValue().getCharCount());
        assertEquals("PENDING", documentCaptor.getValue().getParseStatus());
        assertEquals("0", documentCaptor.getValue().getConfirmed());
    }

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
