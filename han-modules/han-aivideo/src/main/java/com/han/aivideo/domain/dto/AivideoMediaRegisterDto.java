package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Register an uploaded file URL or external URL as an AIVideo media asset.
 */
@Data
public class AivideoMediaRegisterDto {

    @NotNull(message = "projectId cannot be null")
    private Long projectId;

    @NotBlank(message = "assetType cannot be blank")
    private String assetType;

    private String bizType;

    private Long bizId;

    private Long fileId;

    private String fileUrl;

    private Long thumbnailFileId;

    private String promptText;

    private String paramsJson;

    private Boolean selected = Boolean.TRUE;

    private String comment;
}
