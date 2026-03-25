package com.han.ai.service;

import com.han.ai.domain.dto.AiChatRequest;
import com.han.ai.domain.dto.AiMessageEditRequest;
import com.han.ai.domain.po.AiChatMessagePo;
import com.han.ai.domain.po.AiConversationPo;
import com.han.ai.domain.query.AiConversationQuery;
import com.han.common.core.domain.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI chat service.
 */
public interface IAiChatService {

    PageResult<AiConversationPo> selectConversationPage(AiConversationQuery query);

    List<AiChatMessagePo> selectMessages(Long conversationId);

    AiChatMessagePo send(AiChatRequest request);

    SseEmitter stream(AiChatRequest request);

    SseEmitter regenerate(Long conversationId);

    SseEmitter editRegenerate(AiMessageEditRequest request);

    void deleteConversation(Long conversationId);

    void clearConversation(Long conversationId);

    void renameConversation(Long conversationId, String title);

    String chatWithAgent(Long agentId, String message, Long conversationId);

    String chatWithWorkflow(Long workflowId, String message, Long conversationId);
}
