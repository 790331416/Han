package com.han.ai.controller.inner;

import com.han.ai.config.AiStreamExecutor;
import com.han.ai.service.IAiTextGenerationService;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 文本内部控制器。
 */
@Slf4j
@InnerAuth
@RestController("innerAiTextController")
@RequestMapping("/inner/ai")
@RequiredArgsConstructor
public class IAiTextController {

    /** 与 A 层流式口径一致：覆盖编排单流 5 分钟上限 + 收尾余量 */
    private static final long STREAM_TIMEOUT = 330_000L;

    private final IAiTextGenerationService textGenerationService;
    private final AiStreamExecutor aiStreamExecutor;

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
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT);
        // 客户端断开、超时、发送失败之后不再向已失效的 emitter 写入，也不再重复 complete
        AtomicBoolean alive = new AtomicBoolean(true);
        emitter.onCompletion(() -> alive.set(false));
        emitter.onTimeout(() -> alive.set(false));
        emitter.onError(throwable -> alive.set(false));
        try {
            aiStreamExecutor.execute(() -> {
                try {
                    AiTextGenerateResponse response =
                            textGenerationService.stream(request, chunk -> send(emitter, alive, "delta", chunk));
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("modelId", response.getModelId());
                    meta.put("provider", response.getProvider());
                    meta.put("modelCode", response.getModelCode());
                    send(emitter, alive, "meta", meta);
                    sendDone(emitter, alive);
                } catch (Exception exception) {
                    log.warn("Inner AI text stream failed", exception);
                    send(emitter, alive, "error", clientSafeMessage(exception));
                } finally {
                    complete(emitter, alive);
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("Inner AI text stream rejected, queued={}", aiStreamExecutor.queuedTaskCount());
            send(emitter, alive, "error", "AI 服务当前并发已满，请稍后重试");
            complete(emitter, alive);
        }
        return emitter;
    }

    /**
     * 发送失败只熄灭通道并记日志，不再向上抛。
     * 原实现抛 IllegalStateException 会逃出异步任务被静默吞掉，emitter 永不 complete，
     * 请求一直悬挂到超时。
     */
    private void send(SseEmitter emitter, AtomicBoolean alive, String type, Object content) {
        if (!alive.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(XuJsonUtil.toJsonString(Map.of(
                    "type", type,
                    "content", content == null ? "" : content
            ))));
        } catch (IOException | IllegalStateException exception) {
            alive.set(false);
            log.debug("Inner AI text SSE send aborted, type={}", type, exception);
        }
    }

    private void sendDone(SseEmitter emitter, AtomicBoolean alive) {
        if (!alive.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException | IllegalStateException exception) {
            alive.set(false);
        }
    }

    private void complete(SseEmitter emitter, AtomicBoolean alive) {
        if (!alive.getAndSet(false)) {
            return;
        }
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // SSE 发射器已完成或已超时。
        }
    }

    /**
     * 业务异常文案是我们自己写的、面向调用方的提示，可以原样下发；
     * 其余异常只回通用提示，原文只进日志。
     */
    private String clientSafeMessage(Exception exception) {
        if (exception instanceof BusinessException && StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        return "AI 文本生成失败";
    }
}
