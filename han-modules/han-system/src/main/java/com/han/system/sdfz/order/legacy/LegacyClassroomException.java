package com.han.system.sdfz.order.legacy;

import java.io.Serial;

/**
 * 与三课堂交互失败。
 *
 * <p>{@link #retryable} 决定失败分级：网络超时、连接失败、锁等待属于可重试；
 * 课程不存在、听讲班已删除这类属于不可重试，直接转人工，不消耗重试次数。</p>
 */
public class LegacyClassroomException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public LegacyClassroomException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public LegacyClassroomException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static LegacyClassroomException retryable(String message, Throwable cause) {
        return new LegacyClassroomException(message, true, cause);
    }

    public static LegacyClassroomException permanent(String message) {
        return new LegacyClassroomException(message, false);
    }
}
