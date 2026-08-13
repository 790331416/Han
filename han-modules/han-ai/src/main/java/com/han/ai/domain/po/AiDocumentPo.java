package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档。
 */
@Data
@TableName("ai_document")
public class AiDocumentPo {

    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;

    private Long kbId;

    private String docName;

    private String docType;

    private String filePath;

    private Long fileSize;

    private Long charCount;

    private Integer paragraphCount;

    private String indexStatus;

    private String indexError;

    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
