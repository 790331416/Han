package com.han.aivideo.controller.studio;

import com.han.aivideo.controller.base.BAivideoStudioController;
import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.query.AivideoProjectQuery;
import com.han.aivideo.domain.vo.AivideoProjectDetailVo;
import com.han.aivideo.service.IAivideoProjectService;
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

    public AivideoStudioController(IAivideoProjectService projectService) {
        super(projectService);
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
}
