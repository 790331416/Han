package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoProjectEditGenerateDto;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.vo.AivideoProjectEditPreflightVo;

import java.util.List;

/**
 * Project-level selected-shot video editing service.
 */
public interface IAivideoProjectEditService {

    AivideoProjectEditPreflightVo previewProjectEdit(Long projectId);

    AiVideoGenerationTaskPo submitProjectEdit(AivideoProjectEditGenerateDto dto);

    AiVideoGenerationTaskPo pollProjectEditTask(Long projectId, Long taskId);

    List<AiVideoGenerationTaskPo> listProjectEditTasks(Long projectId);
}
