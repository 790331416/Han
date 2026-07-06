package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.han.ai.domain.vo.AiChatImageVo;
import com.han.ai.domain.vo.AiChatKnowledgeSourceVo;
import com.han.ai.domain.vo.AiChatToolTraceVo;
import com.han.ai.domain.vo.AiFlowNodeTraceVo;
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

    /**
     * 图片附件 JSON（[{fileId,url,name}]），库存原文不直出前端
     */
    @JsonIgnore
    private String images;

    /**
     * 扩展元数据 JSON（{nodeTraces:[...]}，advanced 编排执行轨迹等），库存原文不直出前端
     */
    @JsonIgnore
    private String meta;

    private LocalDateTime createTime;

    /**
     * 图片附件（由 images JSON 解析，供前端渲染）
     */
    @TableField(exist = false)
    private List<AiChatImageVo> imageList;

    @TableField(exist = false)
    private List<AiChatKnowledgeSourceVo> knowledgeSources;

    @TableField(exist = false)
    private List<AiChatToolTraceVo> toolExecutions;

    /**
     * 编排节点执行时间线（由 meta JSON 解析，advanced 工作流消息专有）
     */
    @TableField(exist = false)
    private List<AiFlowNodeTraceVo> nodeTraces;
}
