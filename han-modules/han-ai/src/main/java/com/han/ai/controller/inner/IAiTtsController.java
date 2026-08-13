package com.han.ai.controller.inner;

import com.han.ai.service.IAiTtsGenerationService;
import com.han.api.ai.domain.AiTtsGenerateRequest;
import com.han.api.ai.domain.AiTtsGenerateResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 语音合成内部控制器。
 */
@InnerAuth
@RestController("innerAiTtsController")
@RequestMapping("/inner/ai")
@RequiredArgsConstructor
public class IAiTtsController {

    private final IAiTtsGenerationService ttsGenerationService;

    @PostMapping("/tts/synthesize")
    public R<AiTtsGenerateResponse> synthesize(@RequestBody AiTtsGenerateRequest request) {
        try {
            return R.ok(ttsGenerationService.synthesize(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }
}
