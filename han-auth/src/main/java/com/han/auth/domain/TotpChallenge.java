package com.han.auth.domain;

import com.han.common.core.enums.ClientType;

/**
 * TOTP 二次提交挑战票据的服务端载荷。
 *
 * <p>第一段（账号密码 + 图形验证码）校验通过后由服务端签发，前端只拿到票据 ID；
 * 第二段凭「票据 ID + 动态码」完成登录，不再重复提交密码与图形验证码。
 *
 * @param userId              已通过密码校验的用户 ID
 * @param tenantId            用户所属租户
 * @param username            用户名（用于失败计数与登录日志）
 * @param clientType          第一段登录使用的客户端类型，防止二次提交时改成长有效期端
 * @param forceChangePassword 第一段判定出的强制改密结果，避免二次提交时丢失
 * @param captchaUuid         第一段使用的图形验证码 uuid，登录完成后一并清理
 * @param issuedAt            签发时间戳（毫秒）
 */
public record TotpChallenge(
        Long userId,
        Long tenantId,
        String username,
        ClientType clientType,
        boolean forceChangePassword,
        String captchaUuid,
        long issuedAt
) {
}
