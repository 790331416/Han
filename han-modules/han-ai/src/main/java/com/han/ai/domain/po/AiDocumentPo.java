package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Knowledge base document.
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

    /** Han统一文件服务ID；为空时兼容历史本地文档路径。 */
    private Long fileId;

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
