package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Indexed paragraph.
 */
@Data
@TableName("ai_paragraph")
public class AiParagraphPo {

    @TableId(value = "paragraph_id", type = IdType.AUTO)
    private Long paragraphId;

    private Long docId;

    private Long kbId;

    private String title;

    private String content;

    private Integer charCount;

    private Integer hitCount;

    private String embedding;

    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
