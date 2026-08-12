package com.han.auth.config;

import com.han.common.core.enums.ClientType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * 登录安全策略配置。
 *
 * <p>把此前散落在 {@code AuthServiceImpl} 里的登录安全常量收敛到配置，
 * 默认值与改造前的硬编码行为保持一致，运维可按环境收紧。
 */
@Data
@Component
@ConfigurationProperties(prefix = "han.security.login")
public class LoginSecurityProperties {

    /** 触发账号锁定的连续失败次数 */
    private int maxAttempts = 5;

    /** 账号锁定时长 */
    private Duration lockoutDuration = Duration.ofMinutes(10);

    /**
     * 强制校验图形验证码的客户端类型。
     * <p>默认仅 PC（与改造前一致）；App / 微信端无验证码控件，传了才校验。
     * 若要堵住「换非 PC 端点绕过验证码」的撞库路径，把对应 ClientType 加进来即可。
     */
    private Set<ClientType> captchaRequiredClients = EnumSet.of(ClientType.PC);

    /** TOTP 二次提交挑战票据有效期 */
    private Duration totpChallengeTtl = Duration.ofMinutes(5);

    /** 单张 TOTP 挑战票据允许的动态码错误次数，超出即作废票据 */
    private int totpChallengeMaxAttempts = 5;

    /**
     * 租户服务不可用时是否放行登录。
     * <p>默认 false（fail-closed，与 {@code switchTenant} 口径一致）。
     * 长时间故障时运维可临时置 true 降级。
     */
    private boolean tenantCheckFailOpen = false;

    /** 租户有效性校验结果的本地缓存时长（只缓存「有效」，吸收发布期抖动） */
    private Duration tenantCheckCacheTtl = Duration.ofSeconds(60);

    /** 绑定 2FA 时是否要求二次确认当前密码（开启前需前端配合传 password） */
    private boolean totpBindRequirePassword = false;

    /** TOTP 动态码在有效窗口内的重放拦截时长 */
    private Duration totpReplayGuardTtl = Duration.ofMinutes(2);
}
