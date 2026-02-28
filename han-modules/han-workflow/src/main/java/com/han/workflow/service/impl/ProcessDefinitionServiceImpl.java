package com.han.workflow.service.impl;

import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.workflow.convert.ProcessDefinitionConvert;
import com.han.workflow.domain.dto.ProcessDefinitionDTO;
import com.han.workflow.domain.vo.ProcessDefinitionVO;
import com.han.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

/**
 * 流程定义服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionServiceImpl implements ProcessDefinitionService {

    private final RepositoryService repositoryService;
    private final ProcessDefinitionConvert processDefinitionConvert;

    @Override
    public PageResult<ProcessDefinitionVO> listProcessDefinition(ProcessDefinitionDTO dto) {
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc();

        if (dto.getName() != null && !dto.getName().isEmpty()) {
            query.processDefinitionNameLike("%" + dto.getName() + "%");
        }
        if (dto.getCategory() != null && !dto.getCategory().isEmpty()) {
            query.processDefinitionCategory(dto.getCategory());
        }
        if (dto.getKey() != null && !dto.getKey().isEmpty()) {
            query.processDefinitionKeyLike("%" + dto.getKey() + "%");
        }
        if (dto.getSuspended() != null) {
            if (dto.getSuspended()) {
                query.suspended();
            } else {
                query.active();
            }
        }

        long total = query.count();
        int offset = (dto.getPageNum() - 1) * dto.getPageSize();
        List<ProcessDefinition> list = query.listPage(offset, dto.getPageSize());

        List<ProcessDefinitionVO> records = list.stream().map(this::toVO).toList();

        return new PageResult<>(records, total, dto.getPageNum(), dto.getPageSize());
    }

    @Override
    public void deploy(String name, String category, InputStream inputStream) {
        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .category(category)
                .addInputStream(name + ".bpmn20.xml", inputStream)
                .deploy();
        log.info("流程部署成功: {}, deploymentId: {}", name, deployment.getId());
    }

    @Override
    public void deployByXml(String name, String category, String bpmnXml) {
        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .category(category)
                .addString(name + ".bpmn20.xml", bpmnXml)
                .deploy();
        log.info("流程部署成功: {}, deploymentId: {}", name, deployment.getId());
    }

    @Override
    public void activate(String processDefinitionId) {
        repositoryService.activateProcessDefinitionById(processDefinitionId, true, null);
        log.info("流程定义激活: {}", processDefinitionId);
    }

    @Override
    public void suspend(String processDefinitionId) {
        repositoryService.suspendProcessDefinitionById(processDefinitionId, true, null);
        log.info("流程定义挂起: {}", processDefinitionId);
    }

    @Override
    public void delete(String deploymentId, boolean cascade) {
        repositoryService.deleteDeployment(deploymentId, cascade);
        log.info("流程部署删除: {}, cascade: {}", deploymentId, cascade);
    }

    @Override
    public String getProcessDefinitionXml(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        try (InputStream is = repositoryService.getResourceAsStream(
                definition.getDeploymentId(), definition.getResourceName())) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("获取流程定义XML失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream getProcessDiagram(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        return repositoryService.getResourceAsStream(
                definition.getDeploymentId(), definition.getDiagramResourceName());
    }

    private ProcessDefinitionVO toVO(ProcessDefinition definition) {
        ProcessDefinitionVO vo = processDefinitionConvert.toVO(definition);
        
        // 获取部署时间（需要从Deployment单独查询）
        Deployment deployment = repositoryService.createDeploymentQuery()
                .deploymentId(definition.getDeploymentId())
                .singleResult();
        if (deployment != null && deployment.getDeploymentTime() != null) {
            vo.setDeploymentTime(deployment.getDeploymentTime()
                    .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return vo;
    }
}
