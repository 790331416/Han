package com.han.ai.controller.inner;

import com.han.ai.service.IAiTextGenerationService;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI text internal controller.
 */
@InnerAuth
@RestController("innerAiTextController")
@RequestMapping("/inner/ai")
@RequiredArgsConstructor
public class IAiTextController {

    private final IAiTextGenerationService textGenerationService;

    @PostMapping("/text/generate")
    public R<AiTextGenerateResponse> generateText(@RequestBody AiTextGenerateRequest request) {
        try {
            return R.ok(textGenerationService.generate(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }
}
