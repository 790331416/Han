package com.han.workflow.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.RequiresPermission;
import com.han.workflow.domain.dto.ProcessStartDTO;
import com.han.workflow.domain.dto.TaskQueryDTO;
import com.han.workflow.domain.vo.ProcessInstanceVO;
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
@RequestMapping("/workflow/instance")
@RequiredArgsConstructor
public class ProcessInstanceController {

    private final IProcessInstanceService processInstanceService;

    @RequiresPermission("workflow:instance:list")
    @GetMapping("/list")
    public R<PageResult<ProcessInstanceVO>> list(TaskQueryDTO dto) {
        return R.ok(processInstanceService.listMyStartedProcess(dto));
    }

    @RequiresPermission("workflow:instance:start")
    @PostMapping("/start")
    public R<ProcessInstanceVO> start(@Valid @RequestBody ProcessStartDTO dto) {
        return R.ok(processInstanceService.startProcess(dto));
    }

    @RequiresPermission("workflow:instance:stop")
    @PostMapping("/stop/{processInstanceId}")
    public R<Void> stop(@PathVariable String processInstanceId,
                        @RequestBody(required = false) Map<String, Object> payload) {
        processInstanceService.cancelProcess(processInstanceId, readReason(payload));
        return R.ok();
    }

    @RequiresPermission("workflow:instance:suspend")
    @PostMapping("/suspend/{processInstanceId}")
    public R<Void> suspend(@PathVariable String processInstanceId) {
        processInstanceService.suspendProcess(processInstanceId);
        return R.ok();
    }

    @RequiresPermission("workflow:instance:suspend")
    @PostMapping("/activate/{processInstanceId}")
    public R<Void> activate(@PathVariable String processInstanceId) {
        processInstanceService.activateProcess(processInstanceId);
        return R.ok();
    }

    @RequiresPermission("workflow:instance:remove")
    @PostMapping("/delete/{processInstanceId}")
    public R<Void> delete(@PathVariable String processInstanceId,
                          @RequestBody(required = false) Map<String, Object> payload) {
        processInstanceService.deleteProcess(processInstanceId, readReason(payload));
        return R.ok();
    }

    private String readReason(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get("reason");
        return value == null ? null : String.valueOf(value);
    }
}
