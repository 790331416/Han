package com.han.ai.service;

import com.han.ai.domain.dto.AiChatImageRequest;
import com.han.ai.domain.dto.AiChatRequest;
import com.han.ai.domain.dto.AiMessageEditRequest;
import com.han.ai.domain.po.AiAgentPo;
import com.han.ai.domain.po.AiChatMessagePo;
import com.han.ai.domain.po.AiConversationPo;
import com.han.ai.domain.query.AiConversationQuery;
import com.han.common.core.domain.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI chat service.
 */
public interface IAiChatService {

    PageResult<AiConversationPo> selectConversationPage(AiConversationQuery query);

    List<AiChatMessagePo> selectMessages(Long conversationId);

    AiChatMessagePo send(AiChatRequest request);

    SseEmitter stream(AiChatRequest request);

    /**
     * 对话内文生图：调 IMAGE 模型生成图片并转存文件服务，产出带图片附件的 assistant 消息。
     */
    AiChatMessagePo generateImage(AiChatImageRequest request);

    /**
     * 公开分享对话（免登录、无状态、不落库）：按智能体配置组装 systemPrompt + RAG 上下文，
     * history 为前端维护的最近对话轮次（role/content）。
     */
    String shareChat(AiAgentPo agent, String message, List<Map<String, String>> history);

    SseEmitter regenerate(Long conversationId);

    SseEmitter editRegenerate(AiMessageEditRequest request);

    void deleteConversation(Long conversationId);

    void clearConversation(Long conversationId);

    void renameConversation(Long conversationId, String title);

    String chatWithAgent(Long agentId, String message, Long conversationId);

    String chatWithWorkflow(Long workflowId, String message, Long conversationId);
}
