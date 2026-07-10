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

    /**
     * 原文ID；为空时新增原文，不为空时更新当前未确认原文。
     */
    private Long documentId;

    private String sourceType;

    private Long fileId;

    private String fileName;

    private String rawText;
}
