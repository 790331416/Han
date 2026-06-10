package com.han.aivideo.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Project-level edit readiness summary.
 */
@Data
public class AivideoProjectEditPreflightVo {

    private Boolean ready = false;

    private Integer clipCount = 0;

    private Integer missingShotCount = 0;

    private Integer totalDurationSec = 0;

    private Long bgmAudioMediaId;

    private String bgmAudioUrl;

    private Integer audioTrackCount = 0;

    private List<AivideoProjectEditClipVo> clips = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    private List<String> errors = new ArrayList<>();
}
