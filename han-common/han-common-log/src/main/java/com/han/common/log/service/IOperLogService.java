package com.han.common.log.service;

import com.han.common.log.domain.OperLogEvent;

/**
 * 操作日志写入接口（SPI 模式）
 * <p>
 * han-common-log 定义此接口，由 han-system 模块实现并注册 Bean。
 * OperLogAspect 通过此接口异步写入日志，避免 common-log 依赖 system 模块。
 */
public interface IOperLogService {

    /**
     * 记录操作日志
     */
    void recordOperLog(OperLogEvent event);
}
