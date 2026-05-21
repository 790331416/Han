package com.han.aivideo.controller.base;

import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.query.AivideoTaskQuery;
import com.han.aivideo.service.IAivideoAdminTaskService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;

public class BAivideoAdminTaskController {

    private final IAivideoAdminTaskService taskService;

    protected BAivideoAdminTaskController(IAivideoAdminTaskService taskService) {
        this.taskService = taskService;
    }

    protected R<PageResult<AiVideoGenerationTaskPo>> listTasks(AivideoTaskQuery query) {
        return R.ok(taskService.selectPage(query));
    }

    protected R<AiVideoGenerationTaskPo> getTask(Long taskId) {
        return R.ok(taskService.selectById(taskId));
    }
}
