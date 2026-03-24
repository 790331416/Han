package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Knowledge base.
 */
@Data
@TableName("ai_knowledge_base")
public class AiKnowledgeBasePo {

    @TableId(value = "kb_id", type = IdType.AUTO)
    private Long kbId;

    private String kbName;

    private String description;

    private String kbType;

    private Long embeddingModelId;

    private Integer documentCount;

    private Integer paragraphCount;

    private Long charCount;

    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
