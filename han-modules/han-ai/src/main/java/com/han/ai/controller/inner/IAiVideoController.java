package com.han.ai.controller.inner;

import com.han.ai.service.IAiVideoGenerationService;
import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateResponse;
import com.han.api.ai.domain.AiVideoTaskQueryRequest;
import com.han.api.ai.domain.AiVideoTaskQueryResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 视频内部控制器。
 */
@InnerAuth
@RestController("innerAiVideoController")
@RequestMapping("/inner/ai")
@RequiredArgsConstructor
public class IAiVideoController {

    private final IAiVideoGenerationService videoGenerationService;

    @PostMapping("/video/generate")
    public R<AiVideoGenerateResponse> generateVideo(@RequestBody AiVideoGenerateRequest request) {
        try {
            return R.ok(videoGenerationService.generate(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }

    @PostMapping("/video/task/query")
    public R<AiVideoTaskQueryResponse> queryVideoTask(@RequestBody AiVideoTaskQueryRequest request) {
        try {
            return R.ok(videoGenerationService.queryTask(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }

    @PostMapping("/video/prompt/render")
    public R<String> renderVideoPrompt(@RequestBody AiVideoGenerateRequest request) {
        try {
            return R.ok(videoGenerationService.renderPrompt(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }
}
