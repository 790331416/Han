package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Select one generated media candidate.
 */
@Data
public class AivideoMediaSelectDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "媒体ID不能为空")
    private Long mediaId;

    private String bizType;

    private Long bizId;

    private String comment;
}
