package com.han.ai.controller.inner;

import com.han.ai.service.IAiTextGenerationService;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    @PostMapping("/text/prompt/render")
    public R<String> renderTextPrompt(@RequestBody AiTextGenerateRequest request) {
        try {
            return R.ok(textGenerationService.renderPrompt(request));
        } catch (BusinessException exception) {
            return R.fail(exception.getMessage());
        }
    }

    @PostMapping(value = "/text/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamText(@RequestBody AiTextGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        CompletableFuture.runAsync(() -> {
            try {
                AiTextGenerateResponse response = textGenerationService.stream(request, chunk -> send(emitter, "delta", chunk));
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("modelId", response.getModelId());
                meta.put("provider", response.getProvider());
                meta.put("modelCode", response.getModelCode());
                send(emitter, "meta", meta);
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception exception) {
                send(emitter, "error", exception.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String type, Object content) {
        try {
            emitter.send(SseEmitter.event().data(XuJsonUtil.toJsonString(Map.of(
                    "type", type,
                    "content", content == null ? "" : content
            ))));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE send failed", exception);
        }
    }
}
