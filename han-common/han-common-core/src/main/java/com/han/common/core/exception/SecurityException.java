package com.han.common.core.exception;

import lombok.Getter;

@Getter
public class SecurityException extends RuntimeException {
    private final String code;
    private final String message;

    public SecurityException(String message) {
        super(message);
        this.code = "401";
        this.message = message;
    }

    public SecurityException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
