package com.han.ai.domain.dto;

import lombok.Data;

/**
 * AI chat request.
 */
@Data
public class AiChatRequest {

    private Long conversationId;

    private Long workflowId;

    private Long modelId;

    private String message;
}
