package com.han.ai.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话重命名请求。
 */
@Data
public class AiConversationRenameRequest {

    /** 与 ai_conversation.title 列宽对齐 */
    @Size(max = 500, message = "会话标题不能超过 500 字符")
    private String title;
}
