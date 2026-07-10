package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI short-drama prop asset.
 */
@Data
@TableName("ai_video_prop")
public class AiVideoPropPo {

    @TableId(value = "prop_id", type = IdType.AUTO)
    private Long propId;

    private Long projectId;

    private Long tenantId;

    private String propName;

    private String propType;

    private String visualDesc;

    private String color;

    private String material;

    private String shape;

    private String ownerCharacterName;

    private Integer firstShotNo;

    private String lastHolder;

    private String continuityRules;

    private String promptText;

    private Long lockedMediaId;

    private String confirmStatus;

    private Integer sortOrder;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
