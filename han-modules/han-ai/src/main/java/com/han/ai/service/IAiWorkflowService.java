package com.han.ai.service;

import com.han.ai.domain.po.AiWorkflowPo;
import com.han.ai.domain.query.AiWorkflowQuery;
import com.han.common.core.domain.PageResult;

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
}
