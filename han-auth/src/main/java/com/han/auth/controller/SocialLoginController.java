package com.han.auth.controller;

import com.han.auth.domain.LoginVO;
import com.han.auth.service.SocialLoginService;
import com.han.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 社交登录控制器（GitHub / 微信扫码等，按 provider 通用分发）
 *
 * <p>编排逻辑（state 防 CSRF、绑定票据、登录签发）统一收口在 {@link SocialLoginService}，
 * 本控制器只做参数透传；第三方 openId 不回传前端，未绑定时以一次性 ticket 代替。
 */
@Slf4j
@RestController
@RequestMapping("/auth/social")
@RequiredArgsConstructor
public class SocialLoginController {

    private final SocialLoginService socialLoginService;

    /**
     * 获取支持的社交登录方式（已配置凭据且开关开启才为 true）
     */
    @GetMapping("/providers")
    public R<Map<String, Boolean>> providers() {
        return R.ok(socialLoginService.listProviders());
    }

    /**
     * 获取第三方授权 URL（含一次性 state）
     */
    @GetMapping("/{provider}/authorize")
    public R<Map<String, String>> authorize(@PathVariable("provider") String provider,
                                            @RequestParam("redirectUri") String redirectUri) {
        return R.ok(Map.of("authorizeUrl", socialLoginService.buildAuthorizeUrl(provider, redirectUri)));
    }

    /**
     * OAuth 回调：已绑定直接返回登录态；未绑定返回一次性 ticket 供后续绑定
     */
    @PostMapping("/{provider}/callback")
    public R<Map<String, Object>> callback(@PathVariable("provider") String provider,
                                           @RequestBody Map<String, String> body) {
        return R.ok(socialLoginService.handleCallback(provider, body.get("code"), body.get("state")));
    }

    /**
     * 用账号密码把 ticket 对应的第三方身份绑定到已有账号，并直接登录
     */
    @PostMapping("/bind")
    public R<LoginVO> bind(@RequestBody Map<String, String> body) {
        Long tenantId = parseTenantId(body.get("tenantId"));
        return R.ok(socialLoginService.bindAndLogin(body.get("ticket"), body.get("username"),
                body.get("password"), tenantId));
    }

    /**
     * 多租户绑定场景：选择租户后凭 ticket 登录
     */
    @PostMapping("/loginByTicket")
    public R<LoginVO> loginByTicket(@RequestBody Map<String, String> body) {
        return R.ok(socialLoginService.loginByTicket(body.get("ticket"), parseTenantId(body.get("tenantId"))));
    }

    private Long parseTenantId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
