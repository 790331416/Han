package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Generate post-production TTS audio for one storyboard shot.
 */
@Data
public class AivideoShotTtsGenerateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "分镜ID不能为空")
    private Long shotId;

    private String text;

    private String voiceType;

    private String speaker;

    private Integer ttsStartMs;

    private Integer ttsEndMs;

    private BigDecimal speedRatio;

    private BigDecimal volumeRatio;

    private BigDecimal pitchRatio;
}
