package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话实体。
 */
@Data
@TableName("ai_conversation")
public class AiConversationPo {

    @TableId(value = "conversation_id", type = IdType.AUTO)
    private Long conversationId;

    private String title;

    private Long workflowId;

    private Long modelId;

    private Long userId;

    private Integer messageCount;

    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
