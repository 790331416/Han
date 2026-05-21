package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Text generation DTO.
 */
@Data
public class AivideoTextGenerateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private Long documentId;

    private String customPrompt;
}
