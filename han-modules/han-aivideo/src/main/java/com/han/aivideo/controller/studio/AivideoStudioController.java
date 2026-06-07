package com.han.aivideo.controller.studio;

import com.han.aivideo.controller.base.BAivideoStudioController;
import com.han.aivideo.domain.dto.AivideoAssetConfirmDto;
import com.han.aivideo.domain.dto.AivideoAssetExtractDto;
import com.han.aivideo.domain.dto.AivideoCharacterImageGenerateDto;
import com.han.aivideo.domain.dto.AivideoContentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.dto.AivideoMediaSelectDto;
import com.han.aivideo.domain.dto.AivideoProjectEditGenerateDto;
import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.dto.AivideoSceneImageGenerateDto;
import com.han.aivideo.domain.dto.AivideoShotSceneUpdateDto;
import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.dto.AivideoTextGenerateDto;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.query.AivideoProjectQuery;
import com.han.aivideo.domain.vo.AivideoAssetSummaryVo;
import com.han.aivideo.domain.vo.AivideoMediaAssetVo;
import com.han.aivideo.domain.vo.AivideoPromptPreviewVo;
import com.han.aivideo.domain.vo.AivideoProjectEditPreflightVo;
import com.han.aivideo.domain.vo.AivideoProjectDetailVo;
import com.han.aivideo.service.IAivideoProjectEditService;
import com.han.aivideo.service.IAivideoProjectService;
import com.han.aivideo.service.IAivideoSceneImageService;
import com.han.aivideo.service.IAivideoShotVideoService;
import com.han.aivideo.service.IAivideoTextService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController("aivideoStudioController")
@RequestMapping("/aivideo/studio")
public class AivideoStudioController extends BAivideoStudioController {

    public AivideoStudioController(IAivideoProjectService projectService, IAivideoTextService textService,
                                   IAivideoSceneImageService sceneImageService,
                                   IAivideoShotVideoService shotVideoService,
                                   IAivideoProjectEditService projectEditService) {
        super(projectService, textService, sceneImageService, shotVideoService, projectEditService);
    }

    @GetMapping("/project/list")
    @PreAuthorize("@ss.isLogin()")
    public R<PageResult<AiVideoProjectPo>> list(AivideoProjectQuery query) {
        return listProjects(query);
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoProjectDetailVo> getInfo(@PathVariable Long projectId) {
        return getProject(projectId);
    }

    @RepeatSubmit
    @PostMapping("/project")
    @PreAuthorize("@ss.isLogin()")
    public R<Long> add(@Valid @RequestBody AivideoProjectDto dto) {
        return createProject(dto);
    }

    @RepeatSubmit
    @PostMapping("/project/edit")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> edit(@Valid @RequestBody AivideoProjectDto dto) {
        return editProject(dto);
    }

    @RepeatSubmit
    @PostMapping("/document/save")
    @PreAuthorize("@ss.isLogin()")
    public R<Long> save(@Valid @RequestBody AivideoDocumentSaveDto dto) {
        return saveDocument(dto);
    }

    @RepeatSubmit
    @PostMapping("/document/confirm")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> confirmSourceDocument(@Valid @RequestBody AivideoDocumentConfirmDto dto) {
        return confirmDocument(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/polish/generate")
    @PreAuthorize("@ss.isLogin()")
    public R<AiVideoContentVersionPo> generatePolishText(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return generatePolish(dto);
    }

    @PostMapping(value = "/text/polish/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.isLogin()")
    public SseEmitter generatePolishTextStream(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return generatePolishStream(dto);
    }

    @PostMapping("/text/polish/prompt-preview")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoPromptPreviewVo> previewPolishTextPrompt(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return previewPolishPrompt(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/polish/confirm")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> confirmPolishText(@Valid @RequestBody AivideoContentConfirmDto dto) {
        return confirmPolish(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/polish/confirm/cancel")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> cancelConfirmPolishText(@Valid @RequestBody AivideoContentConfirmDto dto) {
        return cancelConfirmPolish(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/script/generate")
    @PreAuthorize("@ss.isLogin()")
    public R<AiVideoContentVersionPo> generateScriptText(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return generateScript(dto);
    }

    @PostMapping(value = "/text/script/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.isLogin()")
    public SseEmitter generateScriptTextStream(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return generateScriptStream(dto);
    }

    @PostMapping("/text/script/prompt-preview")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoPromptPreviewVo> previewScriptTextPrompt(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return previewScriptPrompt(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/script/confirm")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> confirmScriptText(@Valid @RequestBody AivideoContentConfirmDto dto) {
        return confirmScript(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/script/confirm/cancel")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> cancelConfirmScriptText(@Valid @RequestBody AivideoContentConfirmDto dto) {
        return cancelConfirmScript(dto);
    }

    @RepeatSubmit
    @PostMapping("/assets/extract")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoAssetSummaryVo> extractProjectAssets(@Valid @RequestBody AivideoAssetExtractDto dto) {
        return extractAssets(dto);
    }

    @PostMapping(value = "/assets/extract/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.isLogin()")
    public SseEmitter extractProjectAssetsStream(@Valid @RequestBody AivideoAssetExtractDto dto) {
        return extractAssetsStream(dto);
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("@ss.isLogin()")
    public R<AiVideoGenerationTaskPo> getStudioTaskInfo(@PathVariable Long taskId) {
        return getStudioTask(taskId);
    }

    @GetMapping("/task/assets/latest")
    @PreAuthorize("@ss.isLogin()")
    public R<AiVideoGenerationTaskPo> getLatestProjectAssetTask(@RequestParam Long projectId) {
        return getLatestAssetTask(projectId);
    }

    @PostMapping("/assets/prompt-preview")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoPromptPreviewVo> previewProjectAssetPrompt(@Valid @RequestBody AivideoAssetExtractDto dto) {
        return previewAssetPrompt(dto);
    }

    @GetMapping("/assets/summary/{projectId}")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoAssetSummaryVo> assets(@PathVariable Long projectId) {
        return getAssets(projectId);
    }

    @PostMapping("/assets/confirm")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> confirmProjectAsset(@Valid @RequestBody AivideoAssetConfirmDto dto) {
        return confirmAsset(dto);
    }

    @PostMapping("/assets/confirm/cancel")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> cancelConfirmProjectAsset(@Valid @RequestBody AivideoAssetConfirmDto dto) {
        return cancelConfirmAsset(dto);
    }

    @PostMapping("/assets/shot/scene")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> updateProjectShotScene(@Valid @RequestBody AivideoShotSceneUpdateDto dto) {
        return updateShotScene(dto);
    }

    @PostMapping("/media/scene/prompt-preview")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoPromptPreviewVo> previewSceneImage(@Valid @RequestBody AivideoSceneImageGenerateDto dto) {
        return previewSceneImagePrompt(dto);
    }

    @PostMapping(value = "/media/scene/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.isLogin()")
    public SseEmitter generateSceneImageStream(@Valid @RequestBody AivideoSceneImageGenerateDto dto) {
        return generateSceneImages(dto);
    }

    @PostMapping("/media/character/prompt-preview")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoPromptPreviewVo> previewCharacterImage(@Valid @RequestBody AivideoCharacterImageGenerateDto dto) {
        return previewCharacterImagePrompt(dto);
    }

    @PostMapping(value = "/media/character/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.isLogin()")
    public SseEmitter generateCharacterImageStream(@Valid @RequestBody AivideoCharacterImageGenerateDto dto) {
        return generateCharacterImages(dto);
    }

    @PostMapping("/media/shot/video/prompt-preview")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoPromptPreviewVo> previewShotVideo(@Valid @RequestBody AivideoShotVideoGenerateDto dto) {
        return previewShotVideoPrompt(dto);
    }

    @PostMapping(value = "/media/shot/video/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.isLogin()")
    public SseEmitter generateShotVideoStream(@Valid @RequestBody AivideoShotVideoGenerateDto dto) {
        return generateShotVideos(dto);
    }

    @GetMapping("/media/shot/video/tasks")
    @PreAuthorize("@ss.isLogin()")
    public R<List<AiVideoGenerationTaskPo>> listShotVideoTaskHistory(@RequestParam Long projectId,
                                                                     @RequestParam Long shotId) {
        return listShotVideoTasks(projectId, shotId);
    }

    @GetMapping("/edit/preflight")
    @PreAuthorize("@ss.isLogin()")
    public R<AivideoProjectEditPreflightVo> previewEdit(@RequestParam Long projectId) {
        return previewProjectEdit(projectId);
    }

    @PostMapping("/edit/generate")
    @PreAuthorize("@ss.isLogin()")
    public R<AiVideoGenerationTaskPo> generateEdit(@Valid @RequestBody AivideoProjectEditGenerateDto dto) {
        return generateProjectEdit(dto);
    }

    @GetMapping("/edit/task/{taskId}/poll")
    @PreAuthorize("@ss.isLogin()")
    public R<AiVideoGenerationTaskPo> pollEditTask(@PathVariable Long taskId, @RequestParam Long projectId) {
        return pollProjectEdit(projectId, taskId);
    }

    @GetMapping("/edit/tasks")
    @PreAuthorize("@ss.isLogin()")
    public R<List<AiVideoGenerationTaskPo>> listEditTaskHistory(@RequestParam Long projectId) {
        return listProjectEditTasks(projectId);
    }

    @GetMapping("/media/list")
    @PreAuthorize("@ss.isLogin()")
    public R<List<AivideoMediaAssetVo>> mediaList(@RequestParam Long projectId,
                                                  @RequestParam(required = false) String assetType,
                                                  @RequestParam(required = false) String bizType,
                                                  @RequestParam(required = false) Long bizId) {
        return listMedia(projectId, assetType, bizType, bizId);
    }

    @PostMapping("/media/select")
    @PreAuthorize("@ss.isLogin()")
    public R<Void> selectProjectMedia(@Valid @RequestBody AivideoMediaSelectDto dto) {
        return selectMedia(dto);
    }

    @GetMapping("/media/{mediaId}/preview")
    @PreAuthorize("@ss.isLogin()")
    public ResponseEntity<InputStreamResource> previewProjectMedia(@PathVariable Long mediaId) {
        return previewMedia(mediaId);
    }
}
