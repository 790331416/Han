package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI short-drama text content version.
 */
@Data
@TableName("ai_video_content_version")
public class AiVideoContentVersionPo {

    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;

    private Long projectId;

    private Long tenantId;

    private Long documentId;

    private String contentType;

    private Integer versionNo;

    private String title;

    private String contentText;

    private String contentJson;

    private Long promptTemplateId;

    private String customPrompt;

    private Long modelId;

    private Long taskId;

    private String selected;

    private String confirmStatus;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
