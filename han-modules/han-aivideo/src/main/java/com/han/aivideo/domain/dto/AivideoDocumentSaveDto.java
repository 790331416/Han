package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Source document save DTO.
 */
@Data
public class AivideoDocumentSaveDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String sourceType;

    private Long fileId;

    private String fileName;

    private String rawText;
}
