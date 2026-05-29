package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI short-drama shot asset.
 */
@Data
@TableName("ai_video_shot")
public class AiVideoShotPo {

    @TableId(value = "shot_id", type = IdType.AUTO)
    private Long shotId;

    private Long projectId;

    private Long tenantId;

    private Integer episodeNo;

    private Integer shotNo;

    private Integer durationSec;

    private Long sceneId;

    private String characterIds;

    private String shotType;

    private String cameraPosition;

    private String cameraMovement;

    private String actionDesc;

    private String dialogue;

    private String voiceOver;

    private String emotion;

    private String promptText;

    private String referenceMediaIds;

    private Long keyframeMediaId;

    private Long tailFrameMediaId;

    private Long videoMediaId;

    private String confirmStatus;

    private String generationStatus;

    private Integer sortOrder;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
