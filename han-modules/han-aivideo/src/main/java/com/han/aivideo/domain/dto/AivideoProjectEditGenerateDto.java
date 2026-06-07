package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Project-level final edit generation request.
 */
@Data
public class AivideoProjectEditGenerateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String videoName;

    private Boolean includeAudio;

    private Integer priority;
}
