package com.han.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 推送服务 — 维护用户连接，支持实时消息推送
 */
@Slf4j
@Service
public class SseEmitterService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 连接
     */
    public SseEmitter connect(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        // 发送连接成功事件
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }
        log.debug("SSE 连接建立: userId={}, connectionCount={}", userId, emitters.getOrDefault(userId, new CopyOnWriteArrayList<>()).size());
        return emitter;
    }

    /**
     * 断开连接
     */
    public void disconnect(String userId) {
        List<SseEmitter> emitterList = emitters.remove(userId);
        if (emitterList != null) {
            for (SseEmitter emitter : emitterList) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * 向指定用户推送消息
     */
    public void sendToUser(String userId, String eventName, Object data) {
        List<SseEmitter> emitterList = emitters.get(userId);
        if (emitterList != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitterList) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                    log.debug("SSE 推送失败，移除连接: userId={}", userId);
                }
            }
            deadEmitters.forEach(emitter -> removeEmitter(userId, emitter));
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitterList = emitters.get(userId);
        if (emitterList == null) {
            return;
        }
        emitterList.remove(emitter);
        if (emitterList.isEmpty()) {
            emitters.remove(userId);
        }
    }

    /**
     * 向所有在线用户广播消息
     */
    public void broadcast(String eventName, Object data) {
        List<String> emptyUsers = new ArrayList<>();
        emitters.forEach((userId, emitterList) -> {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitterList) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            }
            deadEmitters.forEach(emitter -> removeEmitter(userId, emitter));
            if (emitterList.isEmpty()) {
                emptyUsers.add(userId);
            }
        });
        emptyUsers.forEach(emitters::remove);
    }

    /**
     * 在线用户数
     */
    public int getOnlineCount() {
        return emitters.size();
    }
}
