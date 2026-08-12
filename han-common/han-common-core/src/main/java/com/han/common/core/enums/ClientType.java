package com.han.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ClientType {
    PC("pc", "PC端后台"),
    APP("app", "移动App"),
    H5("h5", "H5页面"),
    WECHAT_MP("wechat_mp", "微信小程序"),
    WECHAT_OA("wechat_oa", "微信公众号"),
    API("api", "开放API");

    private final String code;
    private final String desc;

    /**
     * 按 code 解析客户端类型。
     * <p>无法识别时返回 {@code null}，由调用方显式决定兜底策略。
     * code 通常来自客户端可控的 {@code X-Client-Type} 请求头，
     * 原先兜底成 {@link #PC}（后台管理端）属于 fail-open：传一个不存在的值就能走上管理端分支。
     * 确实需要默认值时请用 {@link #fromCode(String, ClientType)} 并选择权限最低的类型。
     */
    public static ClientType fromCode(String code) {
        for (ClientType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 按 code 解析客户端类型，无法识别时返回调用方指定的默认值。
     */
    public static ClientType fromCode(String code, ClientType defaultType) {
        ClientType type = fromCode(code);
        return type != null ? type : defaultType;
    }
}
