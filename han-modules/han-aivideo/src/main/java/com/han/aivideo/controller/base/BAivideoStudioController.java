package com.han.aivideo.controller.base;

import com.han.aivideo.domain.dto.AivideoAssetConfirmDto;
import com.han.aivideo.domain.dto.AivideoAssetExtractDto;
import com.han.aivideo.domain.dto.AivideoCharacterImageGenerateDto;
import com.han.aivideo.domain.dto.AivideoContentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.dto.AivideoMediaSelectDto;
import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.dto.AivideoSceneImageGenerateDto;
import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.dto.AivideoTextGenerateDto;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.query.AivideoProjectQuery;
import com.han.aivideo.domain.vo.AivideoAssetSummaryVo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.domain.vo.AivideoMediaPreviewResource;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import com.han.aivideo.domain.vo.AivideoProjectDetailVo;
import com.han.aivideo.service.IAivideoProjectService;
import com.han.aivideo.service.IAivideoSceneImageService;
import com.han.aivideo.service.IAivideoShotVideoService;
import com.han.aivideo.service.IAivideoTextService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class BAivideoStudioController {

    private final IAivideoProjectService projectService;
    private final IAivideoTextService textService;
    private final IAivideoSceneImageService sceneImageService;
    private final IAivideoShotVideoService shotVideoService;

    protected BAivideoStudioController(IAivideoProjectService projectService, IAivideoTextService textService,
                                       IAivideoSceneImageService sceneImageService,
                                       IAivideoShotVideoService shotVideoService) {
        this.projectService = projectService;
        this.textService = textService;
        this.sceneImageService = sceneImageService;
        this.shotVideoService = shotVideoService;
    }

    protected R<PageResult<AiVideoProjectPo>> listProjects(AivideoProjectQuery query) {
        return R.ok(projectService.selectPage(query));
    }

    protected R<AivideoProjectDetailVo> getProject(Long projectId) {
        return R.ok(projectService.selectDetail(projectId));
    }

    protected R<Long> createProject(AivideoProjectDto dto) {
        return R.ok(projectService.createProject(dto));
    }

    protected R<Void> editProject(AivideoProjectDto dto) {
        projectService.updateProject(dto);
        return R.ok();
    }

    protected R<Long> saveDocument(AivideoDocumentSaveDto dto) {
        return R.ok(projectService.saveDocument(dto));
    }

    protected R<Void> confirmDocument(AivideoDocumentConfirmDto dto) {
        textService.confirmDocument(dto);
        return R.ok();
    }

    protected R<AiVideoContentVersionPo> generatePolish(AivideoTextGenerateDto dto) {
        return R.ok(textService.generatePolish(dto));
    }

    protected SseEmitter generatePolishStream(AivideoTextGenerateDto dto) {
        return textService.generatePolishStream(dto);
    }

    protected R<AivideoPromptPreviewVo> previewPolishPrompt(AivideoTextGenerateDto dto) {
        return R.ok(textService.previewPolishPrompt(dto));
    }

    protected R<Void> confirmPolish(AivideoContentConfirmDto dto) {
        textService.confirmPolish(dto);
        return R.ok();
    }

    protected R<AiVideoContentVersionPo> generateScript(AivideoTextGenerateDto dto) {
        return R.ok(textService.generateScript(dto));
    }

    protected SseEmitter generateScriptStream(AivideoTextGenerateDto dto) {
        return textService.generateScriptStream(dto);
    }

    protected R<AivideoPromptPreviewVo> previewScriptPrompt(AivideoTextGenerateDto dto) {
        return R.ok(textService.previewScriptPrompt(dto));
    }

    protected R<Void> confirmScript(AivideoContentConfirmDto dto) {
        textService.confirmScript(dto);
        return R.ok();
    }

    protected R<AivideoAssetSummaryVo> extractAssets(AivideoAssetExtractDto dto) {
        return R.ok(textService.extractAssets(dto));
    }

    protected SseEmitter extractAssetsStream(AivideoAssetExtractDto dto) {
        return textService.extractAssetsStream(dto);
    }

    protected R<AivideoPromptPreviewVo> previewAssetPrompt(AivideoAssetExtractDto dto) {
        return R.ok(textService.previewAssetPrompt(dto));
    }

    protected R<AivideoAssetSummaryVo> getAssets(Long projectId) {
        return R.ok(textService.selectAssetSummary(projectId));
    }

    protected R<Void> confirmAsset(AivideoAssetConfirmDto dto) {
        textService.confirmAsset(dto);
        return R.ok();
    }

    protected R<AivideoPromptPreviewVo> previewSceneImagePrompt(AivideoSceneImageGenerateDto dto) {
        return R.ok(sceneImageService.previewSceneImagePrompt(dto));
    }

    protected SseEmitter generateSceneImages(AivideoSceneImageGenerateDto dto) {
        return sceneImageService.generateSceneImagesStream(dto);
    }

    protected R<AivideoPromptPreviewVo> previewCharacterImagePrompt(AivideoCharacterImageGenerateDto dto) {
        return R.ok(sceneImageService.previewCharacterImagePrompt(dto));
    }

    protected SseEmitter generateCharacterImages(AivideoCharacterImageGenerateDto dto) {
        return sceneImageService.generateCharacterImagesStream(dto);
    }

    protected R<AivideoPromptPreviewVo> previewShotVideoPrompt(AivideoShotVideoGenerateDto dto) {
        return R.ok(shotVideoService.previewShotVideoPrompt(dto));
    }

    protected SseEmitter generateShotVideos(AivideoShotVideoGenerateDto dto) {
        return shotVideoService.generateShotVideosStream(dto);
    }

    protected R<java.util.List<AivideoMediaAssetVo>> listMedia(Long projectId, String assetType, String bizType, Long bizId) {
        return R.ok(sceneImageService.listMedia(projectId, assetType, bizType, bizId));
    }

    protected R<Void> selectMedia(AivideoMediaSelectDto dto) {
        sceneImageService.selectMedia(dto);
        return R.ok();
    }

    protected ResponseEntity<InputStreamResource> previewMedia(Long mediaId) {
        return mediaPreviewResponse(sceneImageService.previewMedia(mediaId));
    }

    protected ResponseEntity<InputStreamResource> previewPublicMedia(Long mediaId) {
        return mediaPreviewResponse(sceneImageService.previewPublicMedia(mediaId));
    }

    protected ResponseEntity<InputStreamResource> mediaPreviewResponse(AivideoMediaPreviewResource resource) {
        String encodedName = URLEncoder.encode(resource.fileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(resource.mediaType())
                .body(new InputStreamResource(resource.stream()));
    }
}
