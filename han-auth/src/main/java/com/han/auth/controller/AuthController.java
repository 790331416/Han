package com.han.auth.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.TenantSimpleVo;
import com.han.auth.domain.VendorPublicRegisterDTO;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.IAuthService;
import com.han.auth.service.VendorRegistrationService;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RateLimiter;
import com.han.common.security.annotation.RateLimiter.LimitType;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final CaptchaSettingService captchaSettingService;
    private final VendorRegistrationService vendorRegistrationService;

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
     * H5 登录。
     *
     * <p>H5 属于浏览器暴露面，和 PC 一样要求验证码；认证、锁定和 Token 签发仍统一交给
     * {@link IAuthService}，不建立第二套校端账号体系。</p>
     */
    @RateLimiter(key = "login", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/h5/login")
    @PermissionExempt("H5 登录入口，登录前公开访问，方法内完成验证码、密码和租户校验")
    public R<LoginVO> h5Login(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.H5);
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

    /** 厂商注册专用公钥；始终返回，不能用旧登录开关关闭。 */
    @GetMapping("/vendor/publicKey")
    @PermissionExempt("厂商注册前获取专用 RSA 公钥和测试兼容开关")
    public R<Map<String, Object>> vendorPublicKey() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", true);
        result.put("publicKey", securityProperties.getPublicKey());
        result.put("allowInsecureHttp", vendorRegistrationService.isInsecureHttpRegistrationAllowed());
        return R.ok(result);
    }

    /** 厂商公开注册入口，验证码和密码解密统一由 auth 完成。 */
    @PostMapping("/vendor/register")
    @PermissionExempt("厂商公开注册入口，方法内完成验证码、密码安全模式和账号申请校验")
    @RateLimiter(key = "vendorRegister", time = 60, count = 5, limitType = LimitType.IP)
    @RepeatSubmit(interval = 10, message = "请勿重复提交厂商申请")
    public R<String> vendorRegister(@RequestBody @Valid VendorPublicRegisterDTO dto) {
        return R.ok(vendorRegistrationService.register(dto));
    }

    /** 厂商公开查询申请状态，申请编号和联系电话必须同时匹配。 */
    @GetMapping("/vendor/application/status")
    @PermissionExempt("厂商公开状态查询，必须同时匹配申请编号和联系电话")
    @RateLimiter(key = "vendorStatus", time = 60, count = 30, limitType = LimitType.IP)
    public R<OpenVendorApplicationStatusVO> vendorStatus(
            @RequestParam @Size(min = 1, max = 32) @Pattern(regexp = "[A-Za-z0-9-]{1,32}") String applicationNo,
            @RequestParam @Size(min = 6, max = 20) @Pattern(regexp = "[0-9+()\\- ]{6,20}") String contactPhone) {
        return R.ok(vendorRegistrationService.queryStatus(applicationNo, contactPhone));
    }

    /**
     * 获取验证码
     * <p>
     * 消费 sys_config 的 sys.account.captchaEnabled 开关：关闭时返回 enabled=false 且不生成图片，
     * 前端据此隐藏验证码输入框。
     */
    @RateLimiter(key = "captcha", time = 60, count = 20, limitType = LimitType.IP)
    @GetMapping("/captcha")
    @PermissionExempt("登录前验证码入口，配合限流和一次性缓存校验")
    public R<Map<String, String>> captcha() {
        Map<String, String> result = new LinkedHashMap<>();
        boolean enabled = captchaSettingService.isCaptchaEnabled();
        result.put("enabled", String.valueOf(enabled));
        if (!enabled) {
            return R.ok(result);
        }
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(150, 40, 4, 30);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = captcha.getCode();
        String captchaKey = CacheConstants.CAPTCHA_KEY + uuid;
        redisTemplate.opsForValue().set(captchaKey, code, Duration.ofMinutes(5));
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
