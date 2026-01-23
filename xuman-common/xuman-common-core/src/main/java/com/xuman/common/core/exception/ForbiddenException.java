package com.xuman.common.core.exception;

/**
 * 禁止访问异常（无权限）
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException() {
        super("没有权限访问");
    }
}
