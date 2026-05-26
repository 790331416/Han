package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI short-drama project setting snapshot.
 */
@Data
@TableName("ai_video_project_setting")
public class AiVideoProjectSettingPo {

    @TableId(value = "setting_id", type = IdType.AUTO)
    private Long settingId;

    private Long projectId;

    private Long tenantId;

    private Long textModelId;

    private Long imageModelId;

    private Long videoModelId;

    private Long polishPromptTemplateId;

    private Long scriptPromptTemplateId;

    private Long characterPromptTemplateId;

    private Long scenePromptTemplateId;

    private Long sceneImagePromptTemplateId;

    private Long shotPromptTemplateId;

    private Long videoPromptTemplateId;

    private String defaultRatio;

    private String defaultResolution;

    private Integer defaultShotDuration;

    private Integer imageCandidateCount;

    private Integer videoCandidateCount;

    private String previewMode;

    private String contentAuditEnabled;

    private String paramsJson;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
