package com.han.common.core.exception;

import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {
    private final String code;
    private final String message;

    public ForbiddenException(String message) {
        super(message);
        this.code = "403";
        this.message = message;
    }

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
