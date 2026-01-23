package com.xuman.workflow.controller;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.core.domain.R;
import com.xuman.common.security.annotation.RequiresPermission;
import com.xuman.workflow.domain.dto.ProcessDefinitionDTO;
import com.xuman.workflow.domain.vo.ProcessDefinitionVO;
import com.xuman.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 流程定义控制器
 */
@RestController
@RequestMapping("/workflow/definition")
@RequiredArgsConstructor
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    /**
     * 查询流程定义列表
     */
    @RequiresPermission("workflow:definition:list")
    @GetMapping("/list")
    public R<PageResult<ProcessDefinitionVO>> list(ProcessDefinitionDTO dto) {
        return R.ok(processDefinitionService.listProcessDefinition(dto));
    }

    /**
     * 部署流程定义
     */
    @RequiresPermission("workflow:definition:deploy")
    @PostMapping("/deploy")
    public R<Void> deploy(@RequestParam("file") MultipartFile file,
                          @RequestParam("name") String name,
                          @RequestParam(value = "category", required = false) String category) throws IOException {
        processDefinitionService.deploy(name, category, file.getInputStream());
        return R.ok();
    }

    /**
     * 部署流程定义(XML字符串)
     */
    @RequiresPermission("workflow:definition:deploy")
    @PostMapping("/deployXml")
    public R<Void> deployXml(@RequestParam("name") String name,
                             @RequestParam(value = "category", required = false) String category,
                             @RequestBody String bpmnXml) {
        processDefinitionService.deployByXml(name, category, bpmnXml);
        return R.ok();
    }

    /**
     * 激活流程定义
     */
    @RequiresPermission("workflow:definition:edit")
    @PostMapping("/activate/{processDefinitionId}")
    public R<Void> activate(@PathVariable String processDefinitionId) {
        processDefinitionService.activate(processDefinitionId);
        return R.ok();
    }

    /**
     * 挂起流程定义
     */
    @RequiresPermission("workflow:definition:edit")
    @PostMapping("/suspend/{processDefinitionId}")
    public R<Void> suspend(@PathVariable String processDefinitionId) {
        processDefinitionService.suspend(processDefinitionId);
        return R.ok();
    }

    /**
     * 删除流程定义
     */
    @RequiresPermission("workflow:definition:remove")
    @PostMapping("/delete/{deploymentId}")
    public R<Void> delete(@PathVariable String deploymentId,
                          @RequestParam(value = "cascade", defaultValue = "false") boolean cascade) {
        processDefinitionService.delete(deploymentId, cascade);
        return R.ok();
    }

    /**
     * 获取流程定义XML
     */
    @RequiresPermission("workflow:definition:query")
    @GetMapping("/xml/{processDefinitionId}")
    public R<String> getXml(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.getProcessDefinitionXml(processDefinitionId));
    }

    /**
     * 获取流程定义图片
     */
    @RequiresPermission("workflow:definition:query")
    @GetMapping(value = "/diagram/{processDefinitionId}", produces = MediaType.IMAGE_PNG_VALUE)
    public void getDiagram(@PathVariable String processDefinitionId, HttpServletResponse response) throws IOException {
        try (InputStream is = processDefinitionService.getProcessDiagram(processDefinitionId);
             OutputStream os = response.getOutputStream()) {
            response.setContentType(MediaType.IMAGE_PNG_VALUE);
            is.transferTo(os);
        }
    }
}
