package com.han.aivideo.domain.vo;

import com.han.aivideo.domain.po.AiVideoShotPo;
import lombok.Data;

/**
 * Result of AI-assisted storyboard shot script optimization.
 */
@Data
public class AivideoShotScriptOptimizeVo {

    private AiVideoShotPo shot;

    private String optimizedJson;

    private String rawResult;
}
