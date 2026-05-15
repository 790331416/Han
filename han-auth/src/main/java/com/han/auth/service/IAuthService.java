package com.han.auth.service;

import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.TenantSimpleVo;

import java.util.List;

/**
 * 认证服务接口
 */
public interface IAuthService {

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

    List<TenantSimpleVo> getMyTenants();

    LoginVO switchTenant(Long tenantId, String authorization);
}
