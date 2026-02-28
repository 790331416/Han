package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * SSO单点登录控制器
 */
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoController {

    /**
     * SSO登录入口
     * GET /sso/login?client_id=xxx&redirect_uri=xxx&state=xxx
     * 
     * 流程:
     * 1. 第三方应用跳转到此接口
     * 2. 检查用户是否已登录
     * 3. 未登录则跳转到登录页,登录后回到此接口
     * 4. 已登录则生成ticket/code,重定向回第三方应用
     */
    @GetMapping("/login")
    public R<String> ssoLogin(@RequestParam("client_id") String clientId,
                               @RequestParam("redirect_uri") String redirectUri,
                               @RequestParam(value = "state", required = false) String state) {
        // 检查用户是否已登录
        if (!SecurityContextHolder.isLogin()) {
            // 返回需要登录的标识,前端跳转到登录页
            return R.fail(401, "请先登录");
        }
        
        Long userId = SecurityContextHolder.getUserId();
        
        // TODO: 生成SSO Ticket
        String ticket = generateSsoTicket(userId, clientId, redirectUri);
        
        // 返回ticket,前端拼接redirect_uri跳转
        return R.ok(ticket);
    }

    /**
     * SSO登出
     * GET /sso/logout?redirect_uri=xxx
     * 
     * 流程:
     * 1. 清除当前系统登录状态
     * 2. 通知所有已登录的第三方应用登出(可选)
     * 3. 重定向回指定地址
     */
    @GetMapping("/logout")
    public R<Void> ssoLogout(@RequestParam(value = "redirect_uri", required = false) String redirectUri) {
        // TODO: 实现SSO登出
        // 1. 清除本系统登录态
        // 2. 广播登出事件给其他已登录应用
        return R.ok();
    }

    /**
     * 验证SSO Ticket
     * POST /sso/validate
     * 
     * 第三方应用后端调用此接口验证ticket,获取用户信息
     */
    @PostMapping("/validate")
    public R<Object> validateTicket(@RequestParam String ticket,
                                     @RequestParam("client_id") String clientId,
                                     @RequestParam("client_secret") String clientSecret) {
        // TODO: 验证ticket,返回用户信息
        return R.ok();
    }

    /**
     * 检查SSO登录状态
     * GET /sso/check
     * 
     * 用于前端静默检查是否已SSO登录
     */
    @GetMapping("/check")
    public R<Boolean> checkLogin() {
        return R.ok(SecurityContextHolder.isLogin());
    }

    private String generateSsoTicket(Long userId, String clientId, String redirectUri) {
        // TODO: 生成SSO Ticket并缓存
        return "ST-" + System.currentTimeMillis();
    }
}
