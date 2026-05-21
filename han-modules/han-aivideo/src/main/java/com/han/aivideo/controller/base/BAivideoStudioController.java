package com.han.aivideo.controller.base;

import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.query.AivideoProjectQuery;
import com.han.aivideo.domain.vo.AivideoProjectDetailVo;
import com.han.aivideo.service.IAivideoProjectService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;

public class BAivideoStudioController {

    private final IAivideoProjectService projectService;

    protected BAivideoStudioController(IAivideoProjectService projectService) {
        this.projectService = projectService;
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
}
