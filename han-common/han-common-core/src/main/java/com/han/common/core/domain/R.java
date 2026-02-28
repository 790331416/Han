package com.han.common.core.domain;

import com.han.common.core.constant.Constants;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结果
 */
@Data
@NoArgsConstructor
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态码 */
    private int code;

    /** 消息 */
    private String msg;

    /** 数据 */
    private T data;

    /** 时间戳 */
    private long timestamp = System.currentTimeMillis();

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功
     */
    public static <T> R<T> ok() {
        return new R<>(Constants.SUCCESS, "操作成功", null);
    }

    /**
     * 成功 - 带数据
     */
    public static <T> R<T> ok(T data) {
        return new R<>(Constants.SUCCESS, "操作成功", data);
    }

    /**
     * 成功 - 带消息和数据
     */
    public static <T> R<T> ok(String msg, T data) {
        return new R<>(Constants.SUCCESS, msg, data);
    }

    /**
     * 失败
     */
    public static <T> R<T> fail() {
        return new R<>(Constants.FAIL, "操作失败", null);
    }

    /**
     * 失败 - 带消息
     */
    public static <T> R<T> fail(String msg) {
        return new R<>(Constants.FAIL, msg, null);
    }

    /**
     * 失败 - 带状态码和消息
     */
    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    /**
     * 失败 - 带字符串状态码和消息
     */
    public static <T> R<T> fail(String code, String msg) {
        int codeInt;
        try {
            codeInt = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            codeInt = Constants.FAIL;
        }
        return new R<>(codeInt, msg, null);
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return Constants.SUCCESS == this.code;
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }
}
