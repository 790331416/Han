package com.han.workflow.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.RequiresPermission;
import com.han.workflow.domain.dto.TaskCompleteDTO;
import com.han.workflow.domain.dto.TaskQueryDTO;
import com.han.workflow.domain.vo.TaskVO;
import com.han.workflow.service.IProcessInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/workflow/task")
@RequiredArgsConstructor
public class TaskController {

    private final IProcessInstanceService processInstanceService;

    @RequiresPermission("workflow:task:todo")
    @GetMapping("/todo")
    public R<PageResult<TaskVO>> todo(TaskQueryDTO dto) {
        return R.ok(processInstanceService.listMyTodoTasks(dto));
    }

    @RequiresPermission("workflow:task:done")
    @GetMapping("/done")
    public R<PageResult<TaskVO>> done(TaskQueryDTO dto) {
        return R.ok(processInstanceService.listMyDoneTasks(dto));
    }

    @RequiresPermission("workflow:task:todo")
    @PostMapping("/complete")
    public R<Void> complete(@Valid @RequestBody TaskCompleteDTO dto) {
        processInstanceService.completeTask(dto);
        return R.ok();
    }

    @RequiresPermission("workflow:task:todo")
    @PostMapping("/transfer")
    public R<Void> transfer(@RequestBody Map<String, Object> payload) {
        processInstanceService.transferTask(readString(payload, "taskId"), readString(payload, "userId"), readString(payload, "reason"));
        return R.ok();
    }

    @RequiresPermission("workflow:task:todo")
    @PostMapping("/delegate")
    public R<Void> delegate(@RequestBody Map<String, Object> payload) {
        processInstanceService.delegateTask(readString(payload, "taskId"), readString(payload, "userId"), readString(payload, "reason"));
        return R.ok();
    }

    @RequiresPermission("workflow:task:todo")
    @PostMapping("/revoke/{taskId}")
    public R<Void> revoke(@PathVariable String taskId) {
        processInstanceService.unclaimTask(taskId);
        return R.ok();
    }

    private String readString(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
