package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 单点登录控制器。
 */
@RestController
@RequestMapping({"/sso", "/open/sso"})
@RequiredArgsConstructor
public class SsoController {

    /**
     * SSO 登录入口。
     */
    @GetMapping("/login")
    @PermissionExempt("SSO 登录入口，由登录态和业务逻辑控制")
    public R<String> ssoLogin(@RequestParam("client_id") String clientId,
                              @RequestParam("redirect_uri") String redirectUri,
                              @RequestParam(value = "state", required = false) String state) {
        if (!SecurityContextHolder.isLogin()) {
            return R.fail(401, "请先登录");
        }

        Long userId = SecurityContextHolder.getUserId();
        String ticket = generateSsoTicket(userId, clientId, redirectUri);
        return R.ok(ticket);
    }

    /**
     * SSO 登出入口。
     */
    @GetMapping("/logout")
    @PermissionExempt("SSO 登出入口，由登录态和业务逻辑控制")
    public R<Void> ssoLogout(@RequestParam(value = "redirect_uri", required = false) String redirectUri) {
        // TODO: 实现 SSO 登出。
        // 1. 清除本系统登录态
        // 2. 广播登出事件给其他已登录应用
        return R.ok();
    }

    /**
     * 校验 SSO Ticket，供第三方服务端调用。
     */
    @PostMapping("/validate")
    @PermissionExempt("SSO Ticket 校验公开端点，由客户端凭证和业务参数校验控制")
    public R<Object> validateTicket(@RequestParam String ticket,
                                    @RequestParam("client_id") String clientId,
                                    @RequestParam("client_secret") String clientSecret) {
        // TODO: 校验 Ticket 并返回用户信息。
        return R.ok();
    }

    /**
     * 检查当前用户是否已登录。
     */
    @GetMapping("/check")
    @PermissionExempt("SSO 登录态检查端点，由登录态和业务逻辑控制")
    public R<Boolean> checkLogin() {
        return R.ok(SecurityContextHolder.isLogin());
    }

    private String generateSsoTicket(Long userId, String clientId, String redirectUri) {
        // TODO: 生成 SSO Ticket 并缓存。
        return "ST-" + System.currentTimeMillis();
    }
}
