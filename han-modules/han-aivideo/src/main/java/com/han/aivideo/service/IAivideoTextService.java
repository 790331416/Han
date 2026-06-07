package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoAssetConfirmDto;
import com.han.aivideo.domain.dto.AivideoAssetExtractDto;
import com.han.aivideo.domain.dto.AivideoContentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentConfirmDto;
import com.han.aivideo.domain.dto.AivideoShotSceneUpdateDto;
import com.han.aivideo.domain.dto.AivideoTextGenerateDto;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.vo.AivideoAssetSummaryVo;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI short-drama text workflow service.
 */
public interface IAivideoTextService {

    void confirmDocument(AivideoDocumentConfirmDto dto);

    AiVideoContentVersionPo generatePolish(AivideoTextGenerateDto dto);

    SseEmitter generatePolishStream(AivideoTextGenerateDto dto);

    AivideoPromptPreviewVo previewPolishPrompt(AivideoTextGenerateDto dto);

    void confirmPolish(AivideoContentConfirmDto dto);

    void cancelConfirmPolish(AivideoContentConfirmDto dto);

    AiVideoContentVersionPo generateScript(AivideoTextGenerateDto dto);

    SseEmitter generateScriptStream(AivideoTextGenerateDto dto);

    AivideoPromptPreviewVo previewScriptPrompt(AivideoTextGenerateDto dto);

    void confirmScript(AivideoContentConfirmDto dto);

    void cancelConfirmScript(AivideoContentConfirmDto dto);

    AivideoAssetSummaryVo extractAssets(AivideoAssetExtractDto dto);

    SseEmitter extractAssetsStream(AivideoAssetExtractDto dto);

    AivideoPromptPreviewVo previewAssetPrompt(AivideoAssetExtractDto dto);

    AivideoAssetSummaryVo selectAssetSummary(Long projectId);

    AiVideoGenerationTaskPo selectStudioTask(Long taskId);

    AiVideoGenerationTaskPo selectLatestAssetTask(Long projectId);

    void confirmAsset(AivideoAssetConfirmDto dto);

    void cancelConfirmAsset(AivideoAssetConfirmDto dto);

    void updateShotScene(AivideoShotSceneUpdateDto dto);
}
