package com.han.aivideo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Generated media asset view.
 */
@Data
public class AivideoMediaAssetVo {

    private Long mediaId;

    private Long projectId;

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

    private LocalDateTime createTime;
}
