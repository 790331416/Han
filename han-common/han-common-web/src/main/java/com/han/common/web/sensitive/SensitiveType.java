package com.han.common.web.sensitive;

/**
 * 脱敏类型枚举。
 */
public enum SensitiveType {

    /**
     * 手机号。
     */
    PHONE,

    /**
     * 邮箱。
     */
    EMAIL,

    /**
     * 身份证号。
     */
    ID_CARD,

    /**
     * 银行卡号。
     */
    BANK_CARD,

    /**
     * 姓名。
     */
    NAME,

    /**
     * 地址。
     */
    ADDRESS,

    /**
     * 自定义脱敏规则。
     */
    CUSTOM
}
