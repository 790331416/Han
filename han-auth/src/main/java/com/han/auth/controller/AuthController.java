package com.han.auth.controller;

import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.AuthService;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

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
}
