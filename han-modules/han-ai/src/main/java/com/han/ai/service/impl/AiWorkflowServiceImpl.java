package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiWorkflowPo;
import com.han.ai.domain.query.AiWorkflowQuery;
import com.han.ai.mapper.AiWorkflowMapper;
import com.han.ai.service.IAiChatService;
import com.han.ai.service.IAiWorkflowService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * AI workflow service implementation.
 */
@Service
@RequiredArgsConstructor
public class AiWorkflowServiceImpl extends AiServiceSupport implements IAiWorkflowService {

    private final AiWorkflowMapper aiWorkflowMapper;
    private final IAiChatService aiChatService;

    @Override
    public PageResult<AiWorkflowPo> selectPage(AiWorkflowQuery query) {
        AiWorkflowQuery safeQuery = query != null ? query : new AiWorkflowQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiWorkflowPo> page = aiWorkflowMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiWorkflowPo selectById(Long workflowId) {
        return requireExisting(workflowId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiWorkflowPo workflow) {
        validateForSave(workflow, false);
        ensureWorkflowNameUnique(workflow.getWorkflowName(), null);
        normalize(workflow);
        fillCreateAudit(workflow);
        aiWorkflowMapper.insert(workflow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiWorkflowPo workflow) {
        if (workflow == null || workflow.getWorkflowId() == null) {
            throw new BusinessException("工作流ID不能为空");
        }
        AiWorkflowPo existing = requireExisting(workflow.getWorkflowId());
        copyEditableFields(workflow, existing);
        validateForSave(existing, true);
        ensureWorkflowNameUnique(existing.getWorkflowName(), existing.getWorkflowId());
        normalize(existing);
        fillUpdateAudit(existing);
        aiWorkflowMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long workflowId) {
        requireExisting(workflowId);
        aiWorkflowMapper.deleteById(workflowId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long workflowId) {
        AiWorkflowPo workflow = requireExisting(workflowId);
        workflow.setPublished("1");
        fillUpdateAudit(workflow);
        aiWorkflowMapper.updateById(workflow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long workflowId) {
        AiWorkflowPo workflow = requireExisting(workflowId);
        workflow.setPublished("0");
        fillUpdateAudit(workflow);
        aiWorkflowMapper.updateById(workflow);
    }

    @Override
    public String chat(Long workflowId, String message, Long conversationId) {
        return aiChatService.chatWithWorkflow(workflowId, message, conversationId);
    }

    private LambdaQueryWrapper<AiWorkflowPo> buildQueryWrapper(AiWorkflowQuery query) {
        LambdaQueryWrapper<AiWorkflowPo> wrapper = new LambdaQueryWrapper<AiWorkflowPo>()
                .like(StringUtils.hasText(query.getWorkflowName()), AiWorkflowPo::getWorkflowName, query.getWorkflowName())
                .eq(StringUtils.hasText(query.getWorkflowType()), AiWorkflowPo::getWorkflowType, query.getWorkflowType())
                .eq(StringUtils.hasText(query.getStatus()), AiWorkflowPo::getStatus, query.getStatus())
                .orderByDesc(AiWorkflowPo::getUpdateTime)
                .orderByDesc(AiWorkflowPo::getCreateTime);
        applyTenantScope(wrapper);
        return wrapper;
    }

    private void applyTenantScope(LambdaQueryWrapper<AiWorkflowPo> wrapper) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiWorkflowPo::getTenantId, tenantId);
        }
    }

    private AiWorkflowPo requireExisting(Long workflowId) {
        if (workflowId == null) {
            throw new BusinessException("工作流ID不能为空");
        }
        AiWorkflowPo workflow = aiWorkflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BusinessException("工作流不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(workflow.getTenantId())) {
            throw new BusinessException("无权访问该工作流");
        }
        return workflow;
    }

    private void validateForSave(AiWorkflowPo workflow, boolean update) {
        if (workflow == null) {
            throw new BusinessException("工作流信息不能为空");
        }
        if (update && workflow.getWorkflowId() == null) {
            throw new BusinessException("工作流ID不能为空");
        }
        if (!StringUtils.hasText(workflow.getWorkflowName())) {
            throw new BusinessException("工作流名称不能为空");
        }
        if (!StringUtils.hasText(workflow.getWorkflowType())) {
            throw new BusinessException("工作流类型不能为空");
        }
        if (workflow.getModelId() == null) {
            throw new BusinessException("AI模型不能为空");
        }
        if (!STATUS_ENABLED.equals(workflow.getStatus()) && !STATUS_DISABLED.equals(workflow.getStatus())) {
            throw new BusinessException("工作流状态不合法");
        }
    }

    private void ensureWorkflowNameUnique(String workflowName, Long excludeId) {
        LambdaQueryWrapper<AiWorkflowPo> wrapper = new LambdaQueryWrapper<AiWorkflowPo>()
                .eq(AiWorkflowPo::getWorkflowName, workflowName);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiWorkflowPo::getTenantId, tenantId);
        }
        if (excludeId != null) {
            wrapper.ne(AiWorkflowPo::getWorkflowId, excludeId);
        }
        if (aiWorkflowMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("工作流名称已存在");
        }
    }

    private void copyEditableFields(AiWorkflowPo source, AiWorkflowPo target) {
        target.setWorkflowName(source.getWorkflowName());
        target.setDescription(source.getDescription());
        target.setWorkflowType(source.getWorkflowType());
        target.setModelId(source.getModelId());
        target.setKnowledgeBaseIds(source.getKnowledgeBaseIds());
        target.setMcpServerIds(source.getMcpServerIds());
        target.setSystemPrompt(source.getSystemPrompt());
        target.setFlowConfig(source.getFlowConfig());
        target.setPrologue(source.getPrologue());
        target.setStatus(source.getStatus());
    }

    private void normalize(AiWorkflowPo workflow) {
        workflow.setWorkflowName(trimToNull(workflow.getWorkflowName()));
        workflow.setDescription(trimToNull(workflow.getDescription()));
        workflow.setWorkflowType(trimToNull(workflow.getWorkflowType()));
        workflow.setKnowledgeBaseIds(StringUtils.hasText(workflow.getKnowledgeBaseIds()) ? workflow.getKnowledgeBaseIds().trim() : "[]");
        workflow.setMcpServerIds(StringUtils.hasText(workflow.getMcpServerIds()) ? workflow.getMcpServerIds().trim() : "[]");
        workflow.setSystemPrompt(trimToEmpty(workflow.getSystemPrompt()));
        workflow.setFlowConfig(StringUtils.hasText(workflow.getFlowConfig()) ? workflow.getFlowConfig().trim() : "{}");
        workflow.setPrologue(trimToEmpty(workflow.getPrologue()));
        workflow.setPublished(StringUtils.hasText(workflow.getPublished()) ? workflow.getPublished().trim() : "0");
        workflow.setStatus(StringUtils.hasText(workflow.getStatus()) ? workflow.getStatus().trim() : STATUS_ENABLED);
    }

    private void fillCreateAudit(AiWorkflowPo workflow) {
        workflow.setTenantId(resolveTenantIdForWrite());
        workflow.setCreateBy(resolveOperator());
        workflow.setCreateTime(now());
        workflow.setUpdateBy(resolveOperator());
        workflow.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiWorkflowPo workflow) {
        workflow.setUpdateBy(resolveOperator());
        workflow.setUpdateTime(now());
    }
}
