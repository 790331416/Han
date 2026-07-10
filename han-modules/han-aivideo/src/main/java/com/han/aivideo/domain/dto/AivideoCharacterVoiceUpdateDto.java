package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Voice profile saved on a character asset for post-production TTS inheritance.
 */
@Data
public class AivideoCharacterVoiceUpdateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "角色ID不能为空")
    private Long characterId;

    private String voiceMode;

    private String voiceType;

    private String voiceName;

    private String voiceDesc;

    private Long voiceReferenceMediaId;

    private String voiceSampleText;

    private BigDecimal voiceSpeedRatio;

    private BigDecimal voiceVolumeRatio;

    private BigDecimal voicePitchRatio;
}
