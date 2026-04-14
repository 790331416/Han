package com.han.ai.domain.dto;

import lombok.Data;

/**
 * Edit and regenerate request.
 */
@Data
public class AiMessageEditRequest {

    private Long conversationId;

    private Long messageId;

    private String content;
}
