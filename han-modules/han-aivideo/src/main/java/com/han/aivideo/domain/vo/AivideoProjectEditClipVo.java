package com.han.aivideo.domain.vo;

import lombok.Data;

/**
 * One selected shot video clip in the project edit timeline.
 */
@Data
public class AivideoProjectEditClipVo {

    private Long shotId;

    private Integer episodeNo;

    private Integer shotNo;

    private Integer durationSec;

    private Integer stitchGroupNo;

    private String transitionBeforeType;

    private String transitionBeforeDesc;

    private String transitionEffect;

    private String actionDesc;

    private Long videoMediaId;

    private String videoUrl;

    private Long ttsAudioMediaId;

    private String ttsAudioUrl;

    private Integer timelineStartMs;

    private Integer timelineEndMs;
}
