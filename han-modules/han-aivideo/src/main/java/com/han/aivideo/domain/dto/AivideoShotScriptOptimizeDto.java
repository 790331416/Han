package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Optimize one storyboard shot script based on current preflight failures.
 */
@Data
public class AivideoShotScriptOptimizeDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "分镜ID不能为空")
    private Long shotId;

    private String customPrompt;

    private List<String> preflightFailures;
}
