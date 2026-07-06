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

    /**
     * 重置分享 key：旧分享链接立即失效，返回新 key。
     */
    String resetShareKey(Long agentId);

    /**
     * 按分享 key 查已发布且启用的智能体；无效/未发布/停用返回 null（公开链路专用，不做租户上下文校验）。
     */
    AiAgentPo selectPublishedByShareKey(String shareKey);

    String chat(Long agentId, String message, Long conversationId);
}
