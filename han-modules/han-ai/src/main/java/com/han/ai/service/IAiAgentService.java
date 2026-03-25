package com.han.ai.service;

import com.han.ai.domain.po.AiAgentPo;
import com.han.ai.domain.query.AiAgentQuery;
import com.han.common.core.domain.PageResult;

/**
 * AI agent service.
 */
public interface IAiAgentService {

    PageResult<AiAgentPo> selectPage(AiAgentQuery query);

    AiAgentPo selectById(Long agentId);

    void insert(AiAgentPo agent);

    void update(AiAgentPo agent);

    void deleteById(Long agentId);

    void publish(Long agentId);

    void unpublish(Long agentId);

    String chat(Long agentId, String message, Long conversationId);
}
