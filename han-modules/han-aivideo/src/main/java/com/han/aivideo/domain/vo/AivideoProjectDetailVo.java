package com.han.aivideo.domain.vo;

import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
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

    private AiVideoGenerationTaskPo latestTask;
}
