package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.ai.domain.vo.AiChatKnowledgeSourceVo;
import com.han.ai.domain.vo.AiChatToolTraceVo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI chat message entity.
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessagePo {

    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;

    private Long conversationId;

    private String role;

    private String content;

    private Integer tokenCount;

    private Integer sortOrder;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<AiChatKnowledgeSourceVo> knowledgeSources;

    @TableField(exist = false)
    private List<AiChatToolTraceVo> toolExecutions;
}
