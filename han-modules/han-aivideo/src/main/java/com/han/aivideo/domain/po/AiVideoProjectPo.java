package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI short-drama project.
 */
@Data
@TableName("ai_video_project")
public class AiVideoProjectPo {

    @TableId(value = "project_id", type = IdType.AUTO)
    private Long projectId;

    private Long tenantId;

    private String projectName;

    private Long ownerUserId;

    private String topicType;

    private String targetPlatform;

    private String defaultRatio;

    private String defaultStyle;

    private Integer defaultShotDuration;

    private Integer candidateImageCount;

    private String previewMode;

    private String currentStage;

    private String projectStatus;

    private BigDecimal budgetLimit;

    private BigDecimal estimatedCost;

    private BigDecimal actualCost;

    private String summary;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;

    private String remark;
}
