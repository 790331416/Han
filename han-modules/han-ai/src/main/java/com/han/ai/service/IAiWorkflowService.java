package com.han.ai.service;

import com.han.ai.domain.po.AiWorkflowPo;
import com.han.ai.domain.query.AiWorkflowQuery;
import com.han.ai.domain.vo.AiFlowDebugVo;
import com.han.common.core.domain.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI workflow service.
 */
public interface IAiWorkflowService {

    PageResult<AiWorkflowPo> selectPage(AiWorkflowQuery query);

    AiWorkflowPo selectById(Long workflowId);

    void insert(AiWorkflowPo workflow);

    void update(AiWorkflowPo workflow);

    void deleteById(Long workflowId);

    void publish(Long workflowId);

    void unpublish(Long workflowId);

    String chat(Long workflowId, String message, Long conversationId);

    /**
     * 编排调试运行：不要求已发布、不落会话消息，返回最终回复与节点执行时间线（仅 advanced 工作流）。
     */
    AiFlowDebugVo debug(Long workflowId, String message);

    /**
     * 编排调试流式运行：语义同 {@link #debug}，以 SSE 实时下发
     * node_start / node_delta / node_end 节点事件与最终回复（delta + meta + [DONE]）。
     */
    SseEmitter debugStream(Long workflowId, String message);
}
