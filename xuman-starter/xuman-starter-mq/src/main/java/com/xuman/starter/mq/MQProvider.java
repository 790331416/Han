package com.xuman.starter.mq;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * 消息队列提供者接口（可插拔）
 */
public interface MQProvider {

    /**
     * 发送消息
     *
     * @param topic   主题/队列名
     * @param message 消息内容
     */
    void send(String topic, Object message);

    /**
     * 发送延迟消息
     *
     * @param topic   主题/队列名
     * @param message 消息内容
     * @param delay   延迟时间
     */
    void sendDelay(String topic, Object message, Duration delay);

    /**
     * 订阅消息
     *
     * @param topic   主题/队列名
     * @param type    消息类型
     * @param handler 消息处理器
     */
    <T> void subscribe(String topic, Class<T> type, Consumer<T> handler);

    /**
     * 是否启用
     */
    boolean isEnabled();
}
