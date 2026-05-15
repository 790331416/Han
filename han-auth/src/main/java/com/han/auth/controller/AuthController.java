package com.han.auth.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.TenantSimpleVo;
import com.han.auth.service.IAuthService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RateLimiter;
import com.han.common.security.annotation.RateLimiter.LimitType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    /**
     * PC端登录
     */
    @RateLimiter(key = "login", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/login")
    @PermissionExempt("登录入口，登录前公开访问，方法内完成验证码、密码和租户校验")
    public R<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.PC);
        return R.ok(authService.login(dto));
    }

    /**
     * App登录
     */
    @RateLimiter(key = "login", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/app/login")
    @PermissionExempt("App 登录入口，登录前公开访问，方法内完成验证码、密码和租户校验")
    public R<LoginVO> appLogin(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.APP);
        return R.ok(authService.login(dto));
    }

    /**
     * 微信小程序登录
     */
    @RateLimiter(key = "login", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/wechat/mp/login")
    @PermissionExempt("微信小程序登录入口，登录前公开访问，方法内完成第三方登录校验")
    public R<LoginVO> wechatMpLogin(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.WECHAT_MP);
        return R.ok(authService.login(dto));
    }

    /**
     * 微信公众号登录
     */
    @RateLimiter(key = "login", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/wechat/oa/login")
    @PermissionExempt("微信公众号登录入口，登录前公开访问，方法内完成第三方登录校验")
    public R<LoginVO> wechatOaLogin(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.WECHAT_OA);
        return R.ok(authService.login(dto));
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    @PermissionExempt("刷新 Token 入口，由 RefreshToken 业务凭证校验控制")
    public R<LoginVO> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        return R.ok(authService.refreshToken(refreshToken));
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    @PermissionExempt("登出入口，允许登录态过期后幂等清理本地会话")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        authService.logout(token);
        return R.ok();
    }

    /**
     * 获取 RSA 公钥（密码加密传输用）
     * <p>仅在 han.security.password-encrypt.enabled=true 时返回公钥
     */
    @GetMapping("/publicKey")
    @PermissionExempt("登录前获取密码加密公钥，只返回公钥和开关状态")
    public R<Map<String, Object>> publicKey() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", securityProperties.isEnabled());
        if (securityProperties.isEnabled()) {
            result.put("publicKey", securityProperties.getPublicKey());
        }
        return R.ok(result);
    }

    /**
     * 获取验证码
     */
    @RateLimiter(key = "captcha", time = 60, count = 20, limitType = LimitType.IP)
    @GetMapping("/captcha")
    @PermissionExempt("登录前验证码入口，配合限流和一次性缓存校验")
    public R<Map<String, String>> captcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(150, 40, 4, 30);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = captcha.getCode();
        String captchaKey = CacheConstants.CAPTCHA_KEY + uuid;
        redisTemplate.opsForValue().set(captchaKey, code, Duration.ofMinutes(5));
        Map<String, String> result = new LinkedHashMap<>();
        result.put("uuid", uuid);
        result.put("img", captcha.getImageBase64());
        return R.ok(result);
    }

    @GetMapping("/myTenants")
    @PermissionExempt("个人登录态接口，由网关 Token 校验和当前登录用户上下文控制")
    public R<List<TenantSimpleVo>> myTenants() {
        return R.ok(authService.getMyTenants());
    }

    @PostMapping("/switchTenant")
    @PermissionExempt("个人登录态接口，由网关 Token 校验并在业务内校验目标租户账号")
    public R<LoginVO> switchTenant(@RequestParam Long tenantId,
                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(authService.switchTenant(tenantId, authorization));
    }
}
