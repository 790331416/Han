package com.han.aivideo.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI short-drama generated media asset.
 */
@Data
@TableName("ai_video_media_asset")
public class AiVideoMediaAssetPo {

    @TableId(value = "media_id", type = IdType.AUTO)
    private Long mediaId;

    private Long projectId;

    private Long tenantId;

    private String assetType;

    private String bizType;

    private Long bizId;

    private Long fileId;

    private String fileUrl;

    private Long thumbnailFileId;

    private String promptText;

    private String negativePrompt;

    private Long modelId;

    private Long taskId;

    private String paramsJson;

    private Integer candidateNo;

    private String selected;

    private String assetStatus;

    private BigDecimal costAmount;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
