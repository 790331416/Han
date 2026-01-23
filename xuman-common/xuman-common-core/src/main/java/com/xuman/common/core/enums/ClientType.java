package com.xuman.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户端类型枚举
 */
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

    public static ClientType fromCode(String code) {
        for (ClientType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return PC;
    }
}
