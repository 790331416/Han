package com.han.auth.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.IAuthService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
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

    /**
     * PC端登录
     */
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.PC);
        return R.ok(authService.login(dto));
    }

    /**
     * App登录
     */
    @PostMapping("/app/login")
    public R<LoginVO> appLogin(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.APP);
        return R.ok(authService.login(dto));
    }

    /**
     * 微信小程序登录
     */
    @PostMapping("/wechat/mp/login")
    public R<LoginVO> wechatMpLogin(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.WECHAT_MP);
        return R.ok(authService.login(dto));
    }

    /**
     * 微信公众号登录
     */
    @PostMapping("/wechat/oa/login")
    public R<LoginVO> wechatOaLogin(@RequestBody @Valid LoginDTO dto) {
        dto.setClientType(ClientType.WECHAT_OA);
        return R.ok(authService.login(dto));
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        return R.ok(authService.refreshToken(refreshToken));
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        authService.logout(token);
        return R.ok();
    }

    /**
     * 获取验证码
     */
    @GetMapping("/captcha")
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
}
