package com.han.ai.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑并重新生成请求。
 */
@Data
public class AiMessageEditRequest {

    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    @NotNull(message = "消息ID不能为空")
    private Long messageId;

    @Size(max = 20000, message = "消息内容不能超过 20000 字符")
    private String content;
}
