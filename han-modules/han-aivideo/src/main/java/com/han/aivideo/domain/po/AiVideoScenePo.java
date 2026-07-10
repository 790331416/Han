package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI short-drama scene asset.
 */
@Data
@TableName("ai_video_scene")
public class AiVideoScenePo {

    @TableId(value = "scene_id", type = IdType.AUTO)
    private Long sceneId;

    private Long projectId;

    private Long tenantId;

    private String sceneName;

    private String sceneType;

    private Integer episodeNo;

    private String timeDesc;

    private String weather;

    private String atmosphere;

    private String visualFeatures;

    private String colorTone;

    private String props;

    private String negativeElements;

    private String promptText;

    private String completeness;

    private String missingFields;

    private Long lockedMediaId;

    private String confirmStatus;

    private Integer sortOrder;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
