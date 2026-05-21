package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI short-drama generation task.
 */
@Data
@TableName("ai_video_generation_task")
public class AiVideoGenerationTaskPo {

    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    private Long projectId;

    private Long tenantId;

    private String taskType;

    private String bizType;

    private Long bizId;

    private Long modelId;

    private Long promptTemplateId;

    private String promptText;

    private String customPrompt;

    private String paramsJson;

    private String providerTaskId;

    private Long jobId;

    private String taskStatus;

    private Integer progress;

    private BigDecimal estimatedCost;

    private BigDecimal actualCost;

    private Integer tokenCount;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
