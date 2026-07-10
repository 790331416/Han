package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Source document confirm DTO.
 */
@Data
public class AivideoDocumentConfirmDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private Long documentId;

    private String parsedText;

    private String chapterJson;

    private String comment;
}
