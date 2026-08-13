package com.han.ai.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 对话请求。
 */
@Data
public class AiChatRequest {

    private Long conversationId;

    private Long workflowId;

    private Long modelId;

    /**
     * 用户消息。长度上限同时是成本上限：无限制的长文本会原样透传给模型按 token 计费。
     */
    @Size(max = 20000, message = "消息内容不能超过 20000 字符")
    private String message;

    /**
     * 图片附件文件ID列表（多模态输入，需模型支持视觉）
     */
    @Size(max = 4, message = "单条消息最多携带 4 张图片")
    private List<Long> imageFileIds;
}
