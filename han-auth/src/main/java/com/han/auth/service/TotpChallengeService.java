package com.han.auth.service;

import com.han.auth.config.LoginSecurityProperties;
import com.han.auth.domain.TotpChallenge;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuIdUtil;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.core.util.XuStrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * TOTP 二次提交挑战票据（一次性，服务端暂存）。
 *
 * <p>解决「图形验证码是一次性的、而 2FA 登录要提交两次」的结构性冲突：
 * 第一段消费掉验证码后签发挑战票据，第二段凭票据 + 动态码完成登录，
 * 不再要求前端重放已被消费的验证码。
 *
 * <p>票据同时承担「不得跳过第一段」的约束——没有走完账号密码与验证码校验
 * 就拿不到票据，因此第二段不需要、也不应该再收密码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotpChallengeService {

    private static final String CHALLENGE_KEY = CacheConstants.CACHE_PREFIX + "totp_challenge:";
    private static final String FAIL_SUFFIX = ":fail";

    private final StringRedisTemplate redisTemplate;
    private final LoginSecurityProperties properties;
    private final LoginAttemptGuard loginAttemptGuard;

    /**
     * 签发挑战票据
     *
     * @return 票据 ID（回传前端）
     */
    public String issue(TotpChallenge challenge) {
        String ticketId = XuIdUtil.uuid();
        redisTemplate.opsForValue().set(CHALLENGE_KEY + ticketId,
                XuJsonUtil.toJsonString(challenge), properties.getTotpChallengeTtl());
        return ticketId;
    }

    /**
     * 读取票据但不消费（动态码校验失败时票据仍需保留，允许在限次内重试）
     */
    public TotpChallenge require(String ticketId) {
        if (XuStrUtil.isBlank(ticketId)) {
            throw new BusinessException("两步验证凭证不能为空");
        }
        String json = redisTemplate.opsForValue().get(CHALLENGE_KEY + ticketId);
        if (XuStrUtil.isBlank(json)) {
            throw new BusinessException("两步验证已超时，请重新登录");
        }
        return XuJsonUtil.parseObject(json, TotpChallenge.class);
    }

    /**
     * 记录一次动态码错误。
     *
     * <p>超过单票据允许的错误次数即作废票据，逼迫攻击者重新走完第一段（含验证码）。
     *
     * @return 本票据剩余的重试次数（&lt;= 0 表示票据已作废）
     */
    public int recordFailure(String ticketId) {
        long count = loginAttemptGuard.incrementWithTtl(CHALLENGE_KEY + ticketId + FAIL_SUFFIX,
                properties.getTotpChallengeTtl());
        int remaining = properties.getTotpChallengeMaxAttempts() - (int) count;
        if (remaining <= 0) {
            consume(ticketId);
        }
        return remaining;
    }

    /**
     * 消费（作废）票据
     */
    public void consume(String ticketId) {
        if (XuStrUtil.isBlank(ticketId)) {
            return;
        }
        redisTemplate.delete(CHALLENGE_KEY + ticketId);
        redisTemplate.delete(CHALLENGE_KEY + ticketId + FAIL_SUFFIX);
    }
}
