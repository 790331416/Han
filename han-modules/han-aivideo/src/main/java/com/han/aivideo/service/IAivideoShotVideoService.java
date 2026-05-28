package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Single shot video candidate workflow service.
 */
public interface IAivideoShotVideoService {

    AivideoPromptPreviewVo previewShotVideoPrompt(AivideoShotVideoGenerateDto dto);

    SseEmitter generateShotVideosStream(AivideoShotVideoGenerateDto dto);

    List<AiVideoGenerationTaskPo> listShotVideoTasks(Long projectId, Long shotId);
}
