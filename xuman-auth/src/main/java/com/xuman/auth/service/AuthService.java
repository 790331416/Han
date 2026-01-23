package com.xuman.auth.service;

import com.xuman.auth.domain.LoginDTO;
import com.xuman.auth.domain.LoginVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 登录
     */
    LoginVO login(LoginDTO dto);

    /**
     * 刷新Token
     */
    LoginVO refreshToken(String refreshToken);

    /**
     * 登出
     */
    void logout(String token);
}
