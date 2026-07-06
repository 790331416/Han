package com.han.ai.domain.dto;

import lombok.Data;

/**
 * 对话内文生图请求。
 */
@Data
public class AiChatImageRequest {

    private Long conversationId;

    /**
     * IMAGE 类型模型ID，不传则用默认图片模型
     */
    private Long modelId;

    private String prompt;

    /**
     * 尺寸，如 1024x1024；不传按模型默认
     */
    private String size;
}
