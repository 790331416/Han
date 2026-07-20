package com.han.auth.service;

import com.han.api.system.domain.UserVO;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.TenantSimpleVo;
import com.han.common.core.enums.ClientType;

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
     * 为已完成身份核验的用户签发登录态（密码登录与社交登录共用的公共出口）。
     * <p>包含账号状态校验、租户有效性校验、权限装载、token 签发、互踢与登录日志。
     */
    LoginVO issueLoginForUser(UserVO user, ClientType clientType, boolean forceChangePassword);

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
