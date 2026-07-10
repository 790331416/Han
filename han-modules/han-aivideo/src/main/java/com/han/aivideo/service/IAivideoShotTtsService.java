package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoShotTtsGenerateDto;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;

public interface IAivideoShotTtsService {

    AiVideoMediaAssetPo generateShotTts(AivideoShotTtsGenerateDto dto);
}
