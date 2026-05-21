package com.han.aivideo.controller.studio;

import com.han.aivideo.controller.base.BAivideoStudioController;
import com.han.aivideo.domain.dto.AivideoAssetConfirmDto;
import com.han.aivideo.domain.dto.AivideoAssetExtractDto;
import com.han.aivideo.domain.dto.AivideoContentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentConfirmDto;
import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.dto.AivideoTextGenerateDto;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.query.AivideoProjectQuery;
import com.han.aivideo.domain.vo.AivideoAssetSummaryVo;
import com.han.aivideo.domain.vo.AivideoProjectDetailVo;
import com.han.aivideo.service.IAivideoProjectService;
import com.han.aivideo.service.IAivideoTextService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("aivideoStudioController")
@RequestMapping("/aivideo/studio")
public class AivideoStudioController extends BAivideoStudioController {

    public AivideoStudioController(IAivideoProjectService projectService, IAivideoTextService textService) {
        super(projectService, textService);
    }

    @GetMapping("/project/list")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<AiVideoProjectPo>> list(AivideoProjectQuery query) {
        return listProjects(query);
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public R<AivideoProjectDetailVo> getInfo(@PathVariable Long projectId) {
        return getProject(projectId);
    }

    @RepeatSubmit
    @PostMapping("/project")
    @PreAuthorize("isAuthenticated()")
    public R<Long> add(@Valid @RequestBody AivideoProjectDto dto) {
        return createProject(dto);
    }

    @RepeatSubmit
    @PostMapping("/project/edit")
    @PreAuthorize("isAuthenticated()")
    public R<Void> edit(@Valid @RequestBody AivideoProjectDto dto) {
        return editProject(dto);
    }

    @RepeatSubmit
    @PostMapping("/document/save")
    @PreAuthorize("isAuthenticated()")
    public R<Long> save(@Valid @RequestBody AivideoDocumentSaveDto dto) {
        return saveDocument(dto);
    }

    @RepeatSubmit
    @PostMapping("/document/confirm")
    @PreAuthorize("isAuthenticated()")
    public R<Void> confirmSourceDocument(@Valid @RequestBody AivideoDocumentConfirmDto dto) {
        return confirmDocument(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/polish/generate")
    @PreAuthorize("isAuthenticated()")
    public R<AiVideoContentVersionPo> generatePolishText(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return generatePolish(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/polish/confirm")
    @PreAuthorize("isAuthenticated()")
    public R<Void> confirmPolishText(@Valid @RequestBody AivideoContentConfirmDto dto) {
        return confirmPolish(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/script/generate")
    @PreAuthorize("isAuthenticated()")
    public R<AiVideoContentVersionPo> generateScriptText(@Valid @RequestBody AivideoTextGenerateDto dto) {
        return generateScript(dto);
    }

    @RepeatSubmit
    @PostMapping("/text/script/confirm")
    @PreAuthorize("isAuthenticated()")
    public R<Void> confirmScriptText(@Valid @RequestBody AivideoContentConfirmDto dto) {
        return confirmScript(dto);
    }

    @RepeatSubmit
    @PostMapping("/assets/extract")
    @PreAuthorize("isAuthenticated()")
    public R<AivideoAssetSummaryVo> extractProjectAssets(@Valid @RequestBody AivideoAssetExtractDto dto) {
        return extractAssets(dto);
    }

    @GetMapping("/assets/summary/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public R<AivideoAssetSummaryVo> assets(@PathVariable Long projectId) {
        return getAssets(projectId);
    }

    @RepeatSubmit
    @PostMapping("/assets/confirm")
    @PreAuthorize("isAuthenticated()")
    public R<Void> confirmProjectAsset(@Valid @RequestBody AivideoAssetConfirmDto dto) {
        return confirmAsset(dto);
    }
}
