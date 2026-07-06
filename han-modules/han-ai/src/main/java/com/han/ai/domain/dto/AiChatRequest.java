package com.han.ai.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * AI chat request.
 */
@Data
public class AiChatRequest {

    private Long conversationId;

    private Long workflowId;

    private Long modelId;

    private String message;

    /**
     * 图片附件文件ID列表（多模态输入，须模型支持视觉）
     */
    private List<Long> imageFileIds;
}
