package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Text content confirm DTO.
 */
@Data
public class AivideoContentConfirmDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "版本ID不能为空")
    private Long versionId;

    private String comment;
}
