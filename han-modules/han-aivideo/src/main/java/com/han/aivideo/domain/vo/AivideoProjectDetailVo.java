package com.han.aivideo.domain.vo;

import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
import com.han.aivideo.domain.po.AiVideoContentVersionPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.domain.po.AiVideoSourceDocumentPo;
import lombok.Data;

import java.util.List;

/**
 * AI short-drama project detail VO.
 */
@Data
public class AivideoProjectDetailVo {

    private AiVideoProjectPo project;

    private AiVideoProjectSettingPo setting;

    private List<AiVideoSourceDocumentPo> documents;

    private List<AiVideoContentVersionPo> contentVersions;

    private List<AiVideoCharacterPo> characters;

    private List<AiVideoScenePo> scenes;

    private List<AiVideoShotPo> shots;

    private AiVideoGenerationTaskPo latestTask;
}
