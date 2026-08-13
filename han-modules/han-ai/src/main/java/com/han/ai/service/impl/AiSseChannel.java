package com.han.ai.service.impl;

import com.han.common.core.util.XuJsonUtil;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI SSE 发送通道（对话流式 / 编排调试流式共用）：
 * 统一事件封装为旧协议兼容的 message 事件 + {type,content} JSON；
 * 客户端断开后静默丢弃后续事件，生成与落库继续完成，不因断连中断。
 */
final class AiSseChannel {

    private final SseEmitter emitter;
    private final AtomicBoolean alive = new AtomicBoolean(true);

    AiSseChannel(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(() -> alive.set(false));
        emitter.onTimeout(() -> alive.set(false));
        emitter.onError(throwable -> alive.set(false));
    }

    void sendEvent(String type, Object content) {
        if (!alive.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(XuJsonUtil.toJsonString(Map.of("type", type, "content", content)),
                            MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            alive.set(false);
        }
    }

    void sendDone() {
        if (!alive.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("message").data("[DONE]"));
        } catch (IOException | IllegalStateException ex) {
            alive.set(false);
        }
    }

    void complete() {
        if (!alive.getAndSet(false)) {
            return;
        }
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // SSE 发射器已完成或已超时。
        }
    }
}
