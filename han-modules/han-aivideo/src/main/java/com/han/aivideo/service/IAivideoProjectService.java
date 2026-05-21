package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoDocumentSaveDto;
import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.query.AivideoProjectQuery;
import com.han.aivideo.domain.vo.AivideoProjectDetailVo;
import com.han.common.core.domain.PageResult;

public interface IAivideoProjectService {

    PageResult<AiVideoProjectPo> selectPage(AivideoProjectQuery query);

    AivideoProjectDetailVo selectDetail(Long projectId);

    Long createProject(AivideoProjectDto dto);

    void updateProject(AivideoProjectDto dto);

    Long saveDocument(AivideoDocumentSaveDto dto);
}
