package com.han.common.core.exception;

/**
 * 唯一性冲突异常。
 *
 * <p>用独立错误码 409 与一般业务失败区分，前端可据此提示"该编码已存在"并定位到具体字段，
 * 而不是把唯一键冲突和系统故障都显示成"系统繁忙"。</p>
 */
public class ConflictException extends BusinessException {

    /** 冲突错误码，前端按此码识别唯一性冲突。 */
    public static final String CODE = "409";

    public ConflictException(String message) {
        super(CODE, message);
    }

    public ConflictException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
