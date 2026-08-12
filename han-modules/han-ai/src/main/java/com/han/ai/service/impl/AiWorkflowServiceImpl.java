package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiWorkflowPo;
import com.han.ai.domain.query.AiWorkflowQuery;
import com.han.ai.domain.vo.AiFlowDebugVo;
import com.han.ai.domain.vo.AiFlowNodeTraceVo;
import com.han.ai.mapper.AiWorkflowMapper;
import com.han.ai.service.IAiChatService;
import com.han.ai.service.IAiWorkflowService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI workflow service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkflowServiceImpl extends AiServiceSupport implements IAiWorkflowService {

    /** 调试流式 SSE 超时：覆盖编排单流 5 分钟上限 + 收尾余量 */
    private static final long DEBUG_STREAM_SSE_TIMEOUT = 330_000L;

    private final AiWorkflowMapper aiWorkflowMapper;
    private final IAiChatService aiChatService;
    private final AiFlowEngine aiFlowEngine;

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

    @Override
    public AiFlowDebugVo debug(Long workflowId, String message, Map<String, String> params) {
        AiWorkflowPo workflow = requireDebuggableWorkflow(workflowId, message);
        AiFlowEngine.FlowResult result = aiFlowEngine.execute(workflow.getFlowConfig(), message.trim(),
                List.of(), params, null);
        AiFlowDebugVo vo = new AiFlowDebugVo();
        vo.setSuccess(result.success());
        vo.setReply(result.success() ? result.finalText() : "编排执行失败：" + result.errorMessage());
        vo.setNodeTraces(result.traces());
        return vo;
    }

    @Override
    public SseEmitter debugStream(Long workflowId, String message, Map<String, String> params) {
        AiWorkflowPo workflow = requireDebuggableWorkflow(workflowId, message);
        String debugMessage = message.trim();
        SseEmitter emitter = new SseEmitter(DEBUG_STREAM_SSE_TIMEOUT);
        AiSseChannel channel = new AiSseChannel(emitter);
        LoginUser loginUser = SecurityContextHolder.getLoginUser();
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setLoginUser(loginUser);
            try {
                AiFlowEngine.FlowResult result = aiFlowEngine.execute(workflow.getFlowConfig(), debugMessage,
                        List.of(), params,
                        new AiFlowEngine.FlowEventListener() {
                            @Override
                            public void onNodeStart(AiFlowGraph.FlowNode node) {
                                channel.sendEvent("node_start", Map.of(
                                        "nodeId", node.id(),
                                        "nodeType", node.type() == null ? "" : node.type(),
                                        "nodeName", node.label() == null ? "" : node.label()));
                            }

                            @Override
                            public void onNodeDelta(String nodeId, String delta) {
                                channel.sendEvent("node_delta", Map.of("nodeId", nodeId, "delta", delta));
                            }

                            @Override
                            public void onNodeEnd(AiFlowNodeTraceVo trace) {
                                channel.sendEvent("node_end", trace);
                            }
                        });
                String reply = result.success() ? result.finalText() : "编排执行失败：" + result.errorMessage();
                channel.sendEvent("delta", reply);
                channel.sendEvent("meta", Map.of(
                        "success", result.success(),
                        "nodeTraces", result.traces()));
                channel.sendDone();
                channel.complete();
            } catch (Exception ex) {
                log.warn("AI workflow debug stream failed, workflowId={}", workflowId, ex);
                channel.sendEvent("error", ex.getMessage() == null ? "编排调试异常" : ex.getMessage());
                channel.complete();
            } finally {
                SecurityContextHolder.clear();
            }
        });
        return emitter;
    }

    private AiWorkflowPo requireDebuggableWorkflow(Long workflowId, String message) {
        AiWorkflowPo workflow = requireExisting(workflowId);
        if (!"advanced".equals(workflow.getWorkflowType())) {
            throw new BusinessException("仅 advanced 编排工作流支持调试运行");
        }
        if (!StringUtils.hasText(message)) {
            throw new BusinessException("调试输入不能为空");
        }
        return workflow;
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
        validateSuggestedQuestions(workflow.getSuggestedQuestions());
        validateFlowConfig(workflow);
    }

    /**
     * advanced 工作流保存时后端复核画布 DAG（前端校验只是体验层）：
     * 唯一 start、无环、无孤岛、节点数上限。画布为空允许保存（视为尚未编排）。
     */
    private void validateFlowConfig(AiWorkflowPo workflow) {
        if (!"advanced".equals(workflow.getWorkflowType())) {
            return;
        }
        String flowConfig = workflow.getFlowConfig();
        if (!StringUtils.hasText(flowConfig) || "{}".equals(flowConfig.trim())) {
            return;
        }
        AiFlowGraph.parse(flowConfig);
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

    /**
     * 合并请求体到库内现值：未提交的字段保持原样（见 {@link AiServiceSupport#copyIfPresent}）。
     * 编排设计器保存画布只提交 workflowId + flowConfig，其余字段必须沿用库内值。
     */
    private void copyEditableFields(AiWorkflowPo source, AiWorkflowPo target) {
        copyIfPresent(source.getWorkflowName(), target::setWorkflowName);
        copyIfPresent(source.getDescription(), target::setDescription);
        copyIfPresent(source.getWorkflowType(), target::setWorkflowType);
        copyIfPresent(source.getModelId(), target::setModelId);
        copyIfPresent(source.getKnowledgeBaseIds(), target::setKnowledgeBaseIds);
        copyIfPresent(source.getMcpServerIds(), target::setMcpServerIds);
        copyIfPresent(source.getSystemPrompt(), target::setSystemPrompt);
        copyIfPresent(source.getFlowConfig(), target::setFlowConfig);
        copyIfPresent(source.getPrologue(), target::setPrologue);
        copyIfPresent(source.getSuggestedQuestions(), target::setSuggestedQuestions);
        copyIfPresent(source.getStatus(), target::setStatus);
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
        workflow.setSuggestedQuestions(StringUtils.hasText(workflow.getSuggestedQuestions()) ? workflow.getSuggestedQuestions().trim() : "[]");
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
