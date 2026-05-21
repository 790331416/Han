package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI short-drama review record.
 */
@Data
@TableName("ai_video_review_record")
public class AiVideoReviewRecordPo {

    @TableId(value = "review_id", type = IdType.AUTO)
    private Long reviewId;

    private Long projectId;

    private Long tenantId;

    private String targetType;

    private Long targetId;

    private String actionType;

    private String beforeStatus;

    private String afterStatus;

    private String comment;

    private String extraPrompt;

    private Long reviewUserId;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;
}
