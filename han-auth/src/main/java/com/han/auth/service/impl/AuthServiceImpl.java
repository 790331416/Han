package com.han.auth.service.impl;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.UserVO;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.AuthService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.util.XuIdUtil;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.core.util.XuStrUtil;
import com.han.common.security.domain.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final StringRedisTemplate redisTemplate;
    private final SystemServiceClient systemServiceClient;

    /** Token 有效期配置 */
    private static final Duration PC_TOKEN_EXPIRE = Duration.ofMinutes(30);
    private static final Duration APP_TOKEN_EXPIRE = Duration.ofDays(7);
    private static final Duration WECHAT_TOKEN_EXPIRE = Duration.ofDays(30);

    /** Refresh Token 有效期配置 */
    private static final Duration PC_REFRESH_EXPIRE = Duration.ofDays(7);
    private static final Duration APP_REFRESH_EXPIRE = Duration.ofDays(30);
    private static final Duration WECHAT_REFRESH_EXPIRE = Duration.ofDays(90);

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
        Duration tokenExpire = getTokenExpire(dto.getClientType());
        Duration refreshExpire = getRefreshExpire(dto.getClientType());
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire.toMillis());

        // 9. 处理多端登录限制
        handleMultiLogin(user.getUserId(), dto.getClientType());

        // 10. 缓存Token和用户信息
        String tokenKey = CacheConstants.TOKEN_KEY + accessToken;
        String refreshKey = CacheConstants.REFRESH_TOKEN_KEY + refreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + user.getUserId() + ":" + dto.getClientType().getCode();

        redisTemplate.opsForValue().set(tokenKey, XuJsonUtil.toJsonString(loginUser), tokenExpire);
        redisTemplate.opsForValue().set(refreshKey, accessToken, refreshExpire);
        redisTemplate.opsForValue().set(userKey, accessToken, tokenExpire);

        // 11. 记录登录成功日志
        recordLoginSuccess(user.getUserId(), dto.getUsername(), dto.getClientType());

        // 12. 返回登录结果
        return buildLoginVO(accessToken, refreshToken, tokenExpire, user.getUserId(),
                user.getUsername(), user.getNickname(), user.getAvatar(), user.getPhone());
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

        LoginUser loginUser = XuJsonUtil.parseObject(userJson, LoginUser.class);

        // 生成新Token
        String newAccessToken = generateToken();
        String newRefreshToken = generateToken();

        // 计算过期时间
        Duration tokenExpire = getTokenExpire(loginUser.getClientType());
        Duration refreshExpire = getRefreshExpire(loginUser.getClientType());
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire.toMillis());

        // 删除旧Token
        redisTemplate.delete(oldTokenKey);
        redisTemplate.delete(refreshKey);

        // 缓存新Token
        String newTokenKey = CacheConstants.TOKEN_KEY + newAccessToken;
        String newRefreshKey = CacheConstants.REFRESH_TOKEN_KEY + newRefreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + loginUser.getUserId() + ":" + loginUser.getClientType().getCode();

        redisTemplate.opsForValue().set(newTokenKey, XuJsonUtil.toJsonString(loginUser), tokenExpire);
        redisTemplate.opsForValue().set(newRefreshKey, newAccessToken, refreshExpire);
        redisTemplate.opsForValue().set(userKey, newAccessToken, tokenExpire);

        return buildLoginVO(newAccessToken, newRefreshToken, tokenExpire, loginUser.getUserId(),
                loginUser.getUsername(), loginUser.getNickname(), loginUser.getAvatar(), loginUser.getPhone());
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
            LoginUser loginUser = XuJsonUtil.parseObject(userJson, LoginUser.class);
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
        return LoginUser.builder()
                .userId(user.getUserId())
                .tenantId(user.getTenantId())
                .deptId(user.getDeptId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .clientType(clientType)
                .loginTime(System.currentTimeMillis())
                .roleIds(user.getRoleIds())
                .roleKeys(user.getRoleKeys())
                .permissions(permissions)
                .build();
    }

    /**
     * 生成Token
     */
    private String generateToken() {
        return XuIdUtil.uuid();
    }

    /**
     * 获取Token过期时间
     */
    private Duration getTokenExpire(ClientType clientType) {
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
    private Duration getRefreshExpire(ClientType clientType) {
        return switch (clientType) {
            case PC -> PC_REFRESH_EXPIRE;
            case APP, H5 -> APP_REFRESH_EXPIRE;
            case WECHAT_MP, WECHAT_OA -> WECHAT_REFRESH_EXPIRE;
            default -> PC_REFRESH_EXPIRE;
        };
    }

    /**
     * 构建登录响应VO
     */
    private LoginVO buildLoginVO(String accessToken, String refreshToken, Duration expiresIn,
                                  Long userId, String username, String nickname, String avatar, String phone) {
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn.toSeconds())
                .userInfo(LoginVO.UserInfoVO.builder()
                        .userId(userId)
                        .username(username)
                        .nickname(nickname)
                        .avatar(avatar)
                        .phone(phone)
                        .build())
                .build();
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
