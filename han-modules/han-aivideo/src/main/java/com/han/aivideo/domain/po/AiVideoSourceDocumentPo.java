package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI short-drama source document.
 */
@Data
@TableName("ai_video_source_document")
public class AiVideoSourceDocumentPo {

    @TableId(value = "document_id", type = IdType.AUTO)
    private Long documentId;

    private Long projectId;

    private Long tenantId;

    private String sourceType;

    private Long fileId;

    private String fileName;

    private String rawText;

    private String parsedText;

    private String chapterJson;

    private Long charCount;

    private String parseStatus;

    private String parseError;

    private String confirmed;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
