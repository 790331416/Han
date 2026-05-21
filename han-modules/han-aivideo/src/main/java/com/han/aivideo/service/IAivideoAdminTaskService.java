package com.han.aivideo.service;

import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.query.AivideoTaskQuery;
import com.han.common.core.domain.PageResult;

public interface IAivideoAdminTaskService {

    PageResult<AiVideoGenerationTaskPo> selectPage(AivideoTaskQuery query);

    AiVideoGenerationTaskPo selectById(Long taskId);
}
