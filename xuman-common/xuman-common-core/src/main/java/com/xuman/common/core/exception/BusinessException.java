package com.xuman.common.core.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String message;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
        this.message = message;
    }
}
