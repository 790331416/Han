package com.han.aivideo.controller.admin;

import com.han.aivideo.controller.base.BAivideoAdminTaskController;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.query.AivideoTaskQuery;
import com.han.aivideo.service.IAivideoAdminTaskService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminAuth
@RestController("aivideoAdminTaskController")
@RequestMapping("/aivideo/admin/task")
public class AAivideoTaskController extends BAivideoAdminTaskController {

    public AAivideoTaskController(IAivideoAdminTaskService taskService) {
        super(taskService);
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:aivideo:task:list')")
    public R<PageResult<AiVideoGenerationTaskPo>> list(AivideoTaskQuery query) {
        return listTasks(query);
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@ss.hasAuthority('ai:aivideo:task:query')")
    public R<AiVideoGenerationTaskPo> getInfo(@PathVariable Long taskId) {
        return getTask(taskId);
    }
}
