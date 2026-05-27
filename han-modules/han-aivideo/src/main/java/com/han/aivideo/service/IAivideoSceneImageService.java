package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoMediaSelectDto;
import com.han.aivideo.domain.dto.AivideoSceneImageGenerateDto;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.domain.vo.AivideoMediaPreviewResource;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Scene image candidate workflow service.
 */
public interface IAivideoSceneImageService {

    AivideoPromptPreviewVo previewSceneImagePrompt(AivideoSceneImageGenerateDto dto);

    SseEmitter generateSceneImagesStream(AivideoSceneImageGenerateDto dto);

    List<AivideoMediaAssetVo> listMedia(Long projectId, String assetType, String bizType, Long bizId);

    AivideoMediaPreviewResource previewMedia(Long mediaId);

    AivideoMediaPreviewResource previewPublicMedia(Long mediaId);

    void selectMedia(AivideoMediaSelectDto dto);
}
