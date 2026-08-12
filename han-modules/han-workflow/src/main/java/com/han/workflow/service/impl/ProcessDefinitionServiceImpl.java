package com.han.workflow.service.impl;

import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.workflow.converter.ProcessDefinitionConverter;
import com.han.workflow.domain.dto.ProcessDefinitionDTO;
import com.han.workflow.domain.vo.ProcessDefinitionVO;
import com.han.workflow.security.WorkflowAccessChecker;
import com.han.workflow.service.IProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
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
public class ProcessDefinitionServiceImpl implements IProcessDefinitionService {

    private final RepositoryService repositoryService;
    private final ProcessDefinitionConverter processDefinitionConvert;
    private final WorkflowAccessChecker accessChecker;

    @Override
    public PageResult<ProcessDefinitionVO> listProcessDefinition(ProcessDefinitionDTO dto) {
        // 租户收敛待定：Flowable 7.2.0 的 ProcessDefinitionQuery 没有 or()，无法在一次查询里
        // 表达「本租户 OR 存量无租户」。强行只按租户过滤会让存量环境已部署的流程定义直接消失，
        // 因此这里暂不收敛，需要配合存量数据回填后再收口（部署侧已按租户打标）。
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
        DeploymentBuilder builder = repositoryService.createDeployment()
                .name(name)
                .category(category)
                .addInputStream(name + ".bpmn20.xml", inputStream);
        Deployment deployment = applyTenant(builder).deploy();
        log.info("流程部署成功: {}, deploymentId: {}, tenantId: {}", name, deployment.getId(), deployment.getTenantId());
    }

    @Override
    public void deployByXml(String name, String category, String bpmnXml) {
        DeploymentBuilder builder = repositoryService.createDeployment()
                .name(name)
                .category(category)
                .addString(name + ".bpmn20.xml", bpmnXml);
        Deployment deployment = applyTenant(builder).deploy();
        log.info("流程部署成功: {}, deploymentId: {}, tenantId: {}", name, deployment.getId(), deployment.getTenantId());
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

    /**
     * 部署时打上当前租户标记；不在租户上下文中时保持 Flowable 默认的无租户部署
     */
    private DeploymentBuilder applyTenant(DeploymentBuilder builder) {
        String tenantId = accessChecker.currentTenantId();
        return tenantId == null ? builder : builder.tenantId(tenantId);
    }

    private ProcessDefinitionVO toVO(ProcessDefinition definition) {
        ProcessDefinitionVO vo = processDefinitionConvert.toVO(definition);
        
        // 获取部署时间（需要从Deployment单独查询）
        Deployment deployment = repositoryService.createDeploymentQuery()
                .deploymentId(definition.getDeploymentId())
                .singleResult();
        if (deployment != null) {
            if (deployment.getDeploymentTime() != null) {
                vo.setDeploymentTime(deployment.getDeploymentTime()
                        .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            // BPMN <process> 未写 name 属性时回退部署时填写的流程名称
            if (vo.getProcessName() == null || vo.getProcessName().isBlank()) {
                vo.setProcessName(deployment.getName());
            }
            // definition.category 默认是 BPMN targetNamespace（http://flowable.org/processdef），
            // 展示分类以部署时选择的分类为准
            if (deployment.getCategory() != null && !deployment.getCategory().isBlank()) {
                vo.setCategory(deployment.getCategory());
            }
        }
        return vo;
    }
}
