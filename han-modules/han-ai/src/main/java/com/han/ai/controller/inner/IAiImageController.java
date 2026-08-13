package com.han.ai.controller.inner;

import com.han.ai.service.IAiImageGenerationService;
import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 图像内部控制器。
 */
@InnerAuth
@RestController("innerAiImageController")
@RequestMapping("/inner/ai")
@RequiredArgsConstructor
public class IAiImageController {

    private final IAiImageGenerationService imageGenerationService;

    @PostMapping("/image/generate")
    public R<AiImageGenerateResponse> generateImage(@RequestBody AiImageGenerateRequest request) {
        try {
            return R.ok(imageGenerationService.generate(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }

    @PostMapping("/image/prompt/render")
    public R<String> renderImagePrompt(@RequestBody AiImageGenerateRequest request) {
        try {
            return R.ok(imageGenerationService.renderPrompt(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }
}
