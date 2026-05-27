package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Single shot video candidate workflow service.
 */
public interface IAivideoShotVideoService {

    AivideoPromptPreviewVo previewShotVideoPrompt(AivideoShotVideoGenerateDto dto);

    SseEmitter generateShotVideosStream(AivideoShotVideoGenerateDto dto);
}
