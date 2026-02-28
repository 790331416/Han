package com.han.workflow.service;

import com.han.common.core.domain.PageResult;
import com.han.workflow.domain.dto.ProcessDefinitionDTO;
import com.han.workflow.domain.dto.ProcessStartDTO;
import com.han.workflow.domain.vo.ProcessDefinitionVO;
import com.han.workflow.domain.vo.ProcessInstanceVO;

import java.io.InputStream;
import java.util.List;

/**
 * 流程定义服务接口
 */
public interface ProcessDefinitionService {

    /**
     * 分页查询流程定义列表
     */
    PageResult<ProcessDefinitionVO> listProcessDefinition(ProcessDefinitionDTO dto);

    /**
     * 部署流程定义
     */
    void deploy(String name, String category, InputStream inputStream);

    /**
     * 部署流程定义(BPMN XML字符串)
     */
    void deployByXml(String name, String category, String bpmnXml);

    /**
     * 激活流程定义
     */
    void activate(String processDefinitionId);

    /**
     * 挂起流程定义
     */
    void suspend(String processDefinitionId);

    /**
     * 删除流程定义
     */
    void delete(String deploymentId, boolean cascade);

    /**
     * 获取流程定义XML
     */
    String getProcessDefinitionXml(String processDefinitionId);

    /**
     * 获取流程定义图片
     */
    InputStream getProcessDiagram(String processDefinitionId);
}
