package com.han.common.web.sensitive;

/**
 * 脱敏类型枚举
 */
public enum SensitiveType {

    /** 手机号：138****1234 */
    PHONE,

    /** 邮箱：t***@example.com */
    EMAIL,

    /** 身份证：110***********1234 */
    ID_CARD,

    /** 银行卡：6222 **** **** 1234 */
    BANK_CARD,

    /** 姓名：*三 / **三 */
    NAME,

    /** 地址：保留前6个字符，其余脱敏 */
    ADDRESS,

    /** 自定义：通过 prefixKeep / suffixKeep 控制 */
    CUSTOM
}
