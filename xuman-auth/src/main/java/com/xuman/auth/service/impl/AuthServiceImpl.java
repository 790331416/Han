package com.xuman.auth.service.impl;

import com.xuman.api.system.SystemServiceClient;
import com.xuman.api.system.domain.UserVO;
import com.xuman.auth.domain.LoginDTO;
import com.xuman.auth.domain.LoginVO;
import com.xuman.auth.service.AuthService;
import com.xuman.common.core.constant.CacheConstants;
import com.xuman.common.core.constant.Constants;
import com.xuman.common.core.domain.R;
import com.xuman.common.core.enums.ClientType;
import com.xuman.common.core.exception.BusinessException;
import com.xuman.common.core.exception.UnauthorizedException;
import com.xuman.common.core.util.PasswordUtil;
import com.xuman.common.core.util.XuIdUtil;
import com.xuman.common.core.util.XuJsonUtil;
import com.xuman.common.core.util.XuStrUtil;
import com.xuman.common.security.domain.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final StringRedisTemplate redisTemplate;
    private final SystemServiceClient systemServiceClient;

    /**
     * Token有效期配置（秒）
     */
    private static final long PC_TOKEN_EXPIRE = 30 * 60;           // PC端30分钟
    private static final long APP_TOKEN_EXPIRE = 7 * 24 * 60 * 60; // App端7天
    private static final long WECHAT_TOKEN_EXPIRE = 30 * 24 * 60 * 60; // 微信端30天
    
    private static final long PC_REFRESH_EXPIRE = 7 * 24 * 60 * 60;     // PC刷新7天
    private static final long APP_REFRESH_EXPIRE = 30 * 24 * 60 * 60;   // App刷新30天
    private static final long WECHAT_REFRESH_EXPIRE = 90 * 24 * 60 * 60; // 微信刷新90天

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 校验验证码（如果启用）
        if (XuStrUtil.isNotBlank(dto.getCode())) {
            validateCaptcha(dto.getCode(), dto.getUuid());
        }

        // 2. 查询用户信息
        R<UserVO> userResult = systemServiceClient.getUserByUsername(dto.getUsername());
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            recordLoginFail(dto.getUsername(), "用户不存在");
            throw new BusinessException("用户名或密码错误");
        }
        
        UserVO user = userResult.getData();

        // 3. 校验用户状态
        if (user.getStatus() != 0) {
            recordLoginFail(dto.getUsername(), "账号已停用");
            throw new BusinessException("账号已停用，请联系管理员");
        }

        // 4. 校验密码
        if (!PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            recordLoginFail(dto.getUsername(), "密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        // 5. 查询用户权限
        R<Set<String>> permsResult = systemServiceClient.getPermissionsByUserId(user.getUserId());
        Set<String> permissions = permsResult.getData();

        // 6. 构建登录用户信息
        LoginUser loginUser = buildLoginUser(user, dto.getClientType(), permissions);

        // 7. 生成Token
        String accessToken = generateToken();
        String refreshToken = generateToken();

        // 8. 计算过期时间
        long tokenExpire = getTokenExpire(dto.getClientType());
        long refreshExpire = getRefreshExpire(dto.getClientType());
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire * 1000);

        // 9. 处理多端登录限制
        handleMultiLogin(user.getUserId(), dto.getClientType());

        // 10. 缓存Token和用户信息
        String tokenKey = CacheConstants.TOKEN_KEY + accessToken;
        String refreshKey = CacheConstants.REFRESH_TOKEN_KEY + refreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + user.getUserId() + ":" + dto.getClientType().getCode();

        redisTemplate.opsForValue().set(tokenKey, XuJsonUtil.toJsonStr(loginUser), tokenExpire, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(refreshKey, accessToken, refreshExpire, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(userKey, accessToken, tokenExpire, TimeUnit.SECONDS);

        // 11. 记录登录成功日志
        recordLoginSuccess(user.getUserId(), dto.getUsername(), dto.getClientType());

        // 12. 返回登录结果
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(tokenExpire)
                .userInfo(LoginVO.UserInfoVO.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .phone(user.getPhone())
                        .build())
                .build();
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        if (XuStrUtil.isBlank(refreshToken)) {
            throw new UnauthorizedException("刷新Token不能为空");
        }

        String refreshKey = CacheConstants.REFRESH_TOKEN_KEY + refreshToken;
        String oldAccessToken = redisTemplate.opsForValue().get(refreshKey);
        
        if (XuStrUtil.isBlank(oldAccessToken)) {
            throw new UnauthorizedException("刷新Token已过期，请重新登录");
        }

        // 获取旧的用户信息
        String oldTokenKey = CacheConstants.TOKEN_KEY + oldAccessToken;
        String userJson = redisTemplate.opsForValue().get(oldTokenKey);
        
        if (XuStrUtil.isBlank(userJson)) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }

        LoginUser loginUser = XuJsonUtil.parseObj(userJson, LoginUser.class);

        // 生成新Token
        String newAccessToken = generateToken();
        String newRefreshToken = generateToken();

        // 计算过期时间
        long tokenExpire = getTokenExpire(loginUser.getClientType());
        long refreshExpire = getRefreshExpire(loginUser.getClientType());
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire * 1000);

        // 删除旧Token
        redisTemplate.delete(oldTokenKey);
        redisTemplate.delete(refreshKey);

        // 缓存新Token
        String newTokenKey = CacheConstants.TOKEN_KEY + newAccessToken;
        String newRefreshKey = CacheConstants.REFRESH_TOKEN_KEY + newRefreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + loginUser.getUserId() + ":" + loginUser.getClientType().getCode();

        redisTemplate.opsForValue().set(newTokenKey, XuJsonUtil.toJsonStr(loginUser), tokenExpire, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(newRefreshKey, newAccessToken, refreshExpire, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(userKey, newAccessToken, tokenExpire, TimeUnit.SECONDS);

        return LoginVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(tokenExpire)
                .userInfo(LoginVO.UserInfoVO.builder()
                        .userId(loginUser.getUserId())
                        .username(loginUser.getUsername())
                        .nickname(loginUser.getNickname())
                        .avatar(loginUser.getAvatar())
                        .phone(loginUser.getPhone())
                        .build())
                .build();
    }

    @Override
    public void logout(String token) {
        if (XuStrUtil.isBlank(token)) {
            return;
        }

        // 移除Bearer前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        String tokenKey = CacheConstants.TOKEN_KEY + token;
        String userJson = redisTemplate.opsForValue().get(tokenKey);
        
        if (XuStrUtil.isNotBlank(userJson)) {
            LoginUser loginUser = XuJsonUtil.parseObj(userJson, LoginUser.class);
            String userKey = CacheConstants.LOGIN_USER_KEY + loginUser.getUserId() + ":" + loginUser.getClientType().getCode();
            redisTemplate.delete(userKey);
        }
        
        redisTemplate.delete(tokenKey);
        log.info("用户登出成功");
    }

    /**
     * 校验验证码
     */
    private void validateCaptcha(String code, String uuid) {
        String captchaKey = CacheConstants.CAPTCHA_KEY + uuid;
        String captcha = redisTemplate.opsForValue().get(captchaKey);
        redisTemplate.delete(captchaKey);
        
        if (XuStrUtil.isBlank(captcha)) {
            throw new BusinessException("验证码已过期");
        }
        if (!code.equalsIgnoreCase(captcha)) {
            throw new BusinessException("验证码错误");
        }
    }

    /**
     * 构建登录用户信息
     */
    private LoginUser buildLoginUser(UserVO user, ClientType clientType, Set<String> permissions) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setPhone(user.getPhone());
        loginUser.setEmail(user.getEmail());
        loginUser.setClientType(clientType);
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setRoleIds(user.getRoleIds());
        loginUser.setRoleKeys(user.getRoleKeys());
        loginUser.setPermissions(permissions);
        return loginUser;
    }

    /**
     * 生成Token
     */
    private String generateToken() {
        return XuIdUtil.simpleUUID();
    }

    /**
     * 获取Token过期时间
     */
    private long getTokenExpire(ClientType clientType) {
        return switch (clientType) {
            case PC -> PC_TOKEN_EXPIRE;
            case APP, H5 -> APP_TOKEN_EXPIRE;
            case WECHAT_MP, WECHAT_OA -> WECHAT_TOKEN_EXPIRE;
            default -> PC_TOKEN_EXPIRE;
        };
    }

    /**
     * 获取刷新Token过期时间
     */
    private long getRefreshExpire(ClientType clientType) {
        return switch (clientType) {
            case PC -> PC_REFRESH_EXPIRE;
            case APP, H5 -> APP_REFRESH_EXPIRE;
            case WECHAT_MP, WECHAT_OA -> WECHAT_REFRESH_EXPIRE;
            default -> PC_REFRESH_EXPIRE;
        };
    }

    /**
     * 处理多端登录限制（踢掉旧登录）
     */
    private void handleMultiLogin(Long userId, ClientType clientType) {
        // PC端限制单设备登录
        if (clientType == ClientType.PC) {
            String userKey = CacheConstants.LOGIN_USER_KEY + userId + ":" + clientType.getCode();
            String oldToken = redisTemplate.opsForValue().get(userKey);
            if (XuStrUtil.isNotBlank(oldToken)) {
                String oldTokenKey = CacheConstants.TOKEN_KEY + oldToken;
                redisTemplate.delete(oldTokenKey);
                log.info("用户[{}]PC端被踢出，新设备登录", userId);
            }
        }
    }

    /**
     * 记录登录失败
     */
    private void recordLoginFail(String username, String message) {
        log.warn("用户[{}]登录失败: {}", username, message);
        // TODO: 记录到登录日志表
        // TODO: 实现登录失败次数限制
    }

    /**
     * 记录登录成功
     */
    private void recordLoginSuccess(Long userId, String username, ClientType clientType) {
        log.info("用户[{}]登录成功, 客户端类型: {}", username, clientType.getCode());
        // TODO: 记录到登录日志表
        // TODO: 更新用户最后登录时间和IP
    }
}
