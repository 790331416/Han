package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Asset extraction DTO.
 */
@Data
public class AivideoAssetExtractDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String customPrompt;
}
