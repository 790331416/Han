package com.han.aivideo.domain.vo;

import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import lombok.Data;

import java.util.List;

/**
 * AI short-drama asset summary.
 */
@Data
public class AivideoAssetSummaryVo {

    private List<AiVideoCharacterPo> characters;

    private List<AiVideoScenePo> scenes;

    private List<AiVideoPropPo> props;

    private List<AiVideoShotPo> shots;
}
