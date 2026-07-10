package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Asset confirm DTO.
 */
@Data
public class AivideoAssetConfirmDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "确认目标类型不能为空")
    private String targetType;

    private Long targetId;

    private String comment;
}
