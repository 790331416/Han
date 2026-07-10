package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI short-drama character asset.
 */
@Data
@TableName("ai_video_character")
public class AiVideoCharacterPo {

    @TableId(value = "character_id", type = IdType.AUTO)
    private Long characterId;

    private Long projectId;

    private Long tenantId;

    private String characterName;

    private String gender;

    private String ageDesc;

    private String identityDesc;

    private String personalityTags;

    private String storyRole;

    private String relationshipDesc;

    private String appearance;

    private String hairStyle;

    private String costume;

    private String colorStyle;

    private String negativeTraits;

    private String promptText;

    private String completeness;

    private String missingFields;

    private Long lockedMediaId;

    private String voiceMode;

    private String voiceType;

    private String voiceName;

    private String voiceDesc;

    private Long voiceReferenceMediaId;

    private String voiceSampleText;

    private BigDecimal voiceSpeedRatio;

    private BigDecimal voiceVolumeRatio;

    private BigDecimal voicePitchRatio;

    private String confirmStatus;

    private Integer sortOrder;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
