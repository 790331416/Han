package com.han.auth.service.impl;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.LoginLogDTO;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.IAuthService;
import com.han.auth.service.TotpService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.core.util.HanIpUtil;
import com.han.common.core.util.HanSecureUtil;
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
public class AuthServiceImpl implements IAuthService {

    private final StringRedisTemplate redisTemplate;
    private final SystemServiceClient systemServiceClient;
    private final TenantServiceClient tenantServiceClient;
    private final SecurityProperties securityProperties;
    private final TotpService totpService;

    private static final Duration PC_TOKEN_EXPIRE = Duration.ofMinutes(30);
    private static final Duration APP_TOKEN_EXPIRE = Duration.ofDays(7);
    private static final Duration WECHAT_TOKEN_EXPIRE = Duration.ofDays(30);

    private static final Duration PC_REFRESH_EXPIRE = Duration.ofDays(7);
    private static final Duration APP_REFRESH_EXPIRE = Duration.ofDays(30);
    private static final Duration WECHAT_REFRESH_EXPIRE = Duration.ofDays(90);

    private static final int PASSWORD_EXPIRE_DAYS = 90;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(10);
    private static final String LOGIN_FAIL_KEY = CacheConstants.CACHE_PREFIX + "login_fail:";

    @Override
    public LoginVO login(LoginDTO dto) {
        if (XuStrUtil.isNotBlank(dto.getCode())) {
            validateCaptcha(dto.getCode(), dto.getUuid());
        }

        R<UserVO> userResult = dto.getTenantId() != null
                ? systemServiceClient.getUserByUsername(dto.getUsername(), dto.getTenantId())
                : systemServiceClient.getUserByUsername(dto.getUsername());
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            recordLoginFail(dto.getUsername(), dto.getTenantId(), "用户不存在");
            throw new BusinessException("用户名或密码错误");
        }

        UserVO user = userResult.getData();
        if (user.getStatus() != 0) {
            recordLoginFail(dto.getUsername(), user.getTenantId(), "账号已停用");
            throw new BusinessException("账号已停用，请联系管理员");
        }

        checkLoginLockout(dto.getUsername(), user.getTenantId());

        String rawPassword = dto.getPassword();
        if (securityProperties.isEnabled()) {
            try {
                rawPassword = HanSecureUtil.rsaDecrypt(rawPassword, securityProperties.getPrivateKey());
            } catch (Exception e) {
                log.warn("用户[{}]密码解密失败", dto.getUsername());
                throw new BusinessException("密码解密失败，请重试");
            }
        }

        if (!PasswordUtil.matches(rawPassword, user.getPassword())) {
            int remaining = incrementLoginFail(dto.getUsername(), user.getTenantId());
            recordLoginFail(dto.getUsername(), user.getTenantId(), "密码错误");
            if (remaining <= 0) {
                throw new BusinessException("密码错误次数过多，账户已锁定" + LOCKOUT_DURATION.toMinutes() + "分钟");
            }
            throw new BusinessException("用户名或密码错误，还可尝试" + remaining + "次");
        }

        boolean forceChangePwd = isForceChangePassword(user);
        boolean totpEnabled = user.getTotpEnabled() != null && user.getTotpEnabled() == 1;
        if (totpEnabled) {
            String totpCode = dto.getTotpCode();
            if (totpCode == null || totpCode.isBlank()) {
                return LoginVO.builder()
                        .requireTotp(true)
                        .build();
            }

            R<String> secretResult = systemServiceClient.getTotpSecret(user.getUserId());
            String secret = secretResult.getData();
            if (secret == null || !totpService.verifyCode(secret, totpCode)) {
                int remaining = incrementLoginFail(dto.getUsername(), user.getTenantId());
                if (remaining <= 0) {
                    throw new BusinessException("验证码错误次数过多，账户已锁定" + LOCKOUT_DURATION.toMinutes() + "分钟");
                }
                throw new BusinessException("两步验证码错误，还可尝试" + remaining + "次");
            }
        }

        clearLoginFail(dto.getUsername(), user.getTenantId());

        if (user.getTenantId() != null && user.getTenantId() != 1L) {
            try {
                R<Boolean> validResult = tenantServiceClient.checkTenantValid(user.getTenantId());
                if (validResult.getData() == null || !validResult.getData()) {
                    recordLoginFail(dto.getUsername(), user.getTenantId(), "租户已停用或已过期");
                    throw new BusinessException("租户已停用或已过期，请联系管理员");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("校验租户有效性失败，跳过校验: tenantId={}", user.getTenantId(), e);
            }
        }

        R<Set<String>> permsResult = systemServiceClient.getPermissionsByUserId(user.getUserId());
        Set<String> permissions = permsResult.getData();
        R<Set<Long>> dataScopeDeptIdsResult = systemServiceClient.getDataScopeDeptIds(user.getUserId());
        Set<Long> dataScopeDeptIds = dataScopeDeptIdsResult != null ? dataScopeDeptIdsResult.getData() : null;

        LoginUser loginUser = buildLoginUser(user, dto.getClientType(), permissions, dataScopeDeptIds);

        String accessToken = generateToken();
        String refreshToken = generateToken();

        Duration tokenExpire = getTokenExpire(dto.getClientType());
        Duration refreshExpire = getRefreshExpire(dto.getClientType());
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire.toMillis());

        handleMultiLogin(user.getUserId(), dto.getClientType());

        String tokenKey = CacheConstants.TOKEN_KEY + accessToken;
        String refreshKey = CacheConstants.REFRESH_TOKEN_KEY + refreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + user.getUserId() + ":" + dto.getClientType().getCode();

        redisTemplate.opsForValue().set(tokenKey, XuJsonUtil.toJsonString(loginUser), tokenExpire);
        redisTemplate.opsForValue().set(refreshKey, accessToken, refreshExpire);
        redisTemplate.opsForValue().set(userKey, accessToken, tokenExpire);

        recordLoginSuccess(user.getUserId(), dto.getUsername(), dto.getClientType());

        return buildLoginVO(accessToken, refreshToken, tokenExpire, forceChangePwd, user.getUserId(),
                user.getUsername(), user.getNickname(), user.getAvatar(), user.getPhone());
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        if (XuStrUtil.isBlank(refreshToken)) {
            throw new UnauthorizedException("刷新 Token 不能为空");
        }

        String refreshKey = CacheConstants.REFRESH_TOKEN_KEY + refreshToken;
        String oldAccessToken = redisTemplate.opsForValue().get(refreshKey);
        if (XuStrUtil.isBlank(oldAccessToken)) {
            throw new UnauthorizedException("刷新 Token 已过期，请重新登录");
        }

        String oldTokenKey = CacheConstants.TOKEN_KEY + oldAccessToken;
        String userJson = redisTemplate.opsForValue().get(oldTokenKey);
        if (XuStrUtil.isBlank(userJson)) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }

        LoginUser loginUser = XuJsonUtil.parseObject(userJson, LoginUser.class);

        String newAccessToken = generateToken();
        String newRefreshToken = generateToken();

        Duration tokenExpire = getTokenExpire(loginUser.getClientType());
        Duration refreshExpire = getRefreshExpire(loginUser.getClientType());
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire.toMillis());

        redisTemplate.delete(oldTokenKey);
        redisTemplate.delete(refreshKey);

        String newTokenKey = CacheConstants.TOKEN_KEY + newAccessToken;
        String newRefreshKey = CacheConstants.REFRESH_TOKEN_KEY + newRefreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + loginUser.getUserId() + ":" + loginUser.getClientType().getCode();

        redisTemplate.opsForValue().set(newTokenKey, XuJsonUtil.toJsonString(loginUser), tokenExpire);
        redisTemplate.opsForValue().set(newRefreshKey, newAccessToken, refreshExpire);
        redisTemplate.opsForValue().set(userKey, newAccessToken, tokenExpire);

        return buildLoginVO(newAccessToken, newRefreshToken, tokenExpire, false, loginUser.getUserId(),
                loginUser.getUsername(), loginUser.getNickname(), loginUser.getAvatar(), loginUser.getPhone());
    }

    @Override
    public void logout(String token) {
        if (XuStrUtil.isBlank(token)) {
            return;
        }

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

    private LoginUser buildLoginUser(UserVO user, ClientType clientType, Set<String> permissions, Set<Long> dataScopeDeptIds) {
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
                .loginIp(getClientIp())
                .loginTime(System.currentTimeMillis())
                .roleIds(user.getRoleIds())
                .roleKeys(user.getRoleKeys())
                .permissions(permissions)
                .deptIds(dataScopeDeptIds)
                .build();
    }

    private String generateToken() {
        return XuIdUtil.uuid();
    }

    private Duration getTokenExpire(ClientType clientType) {
        return switch (clientType) {
            case PC -> PC_TOKEN_EXPIRE;
            case APP, H5 -> APP_TOKEN_EXPIRE;
            case WECHAT_MP, WECHAT_OA -> WECHAT_TOKEN_EXPIRE;
            default -> PC_TOKEN_EXPIRE;
        };
    }

    private Duration getRefreshExpire(ClientType clientType) {
        return switch (clientType) {
            case PC -> PC_REFRESH_EXPIRE;
            case APP, H5 -> APP_REFRESH_EXPIRE;
            case WECHAT_MP, WECHAT_OA -> WECHAT_REFRESH_EXPIRE;
            default -> PC_REFRESH_EXPIRE;
        };
    }

    private boolean isForceChangePassword(UserVO user) {
        if (user.getPwdResetFlag() != null && user.getPwdResetFlag() == 1) {
            return true;
        }
        if (PASSWORD_EXPIRE_DAYS > 0 && user.getPwdUpdateTime() != null) {
            java.time.LocalDateTime expireTime = user.getPwdUpdateTime().plusDays(PASSWORD_EXPIRE_DAYS);
            return java.time.LocalDateTime.now().isAfter(expireTime);
        }
        return false;
    }

    private LoginVO buildLoginVO(String accessToken, String refreshToken, Duration expiresIn,
                                 boolean forceChangePassword,
                                 Long userId, String username, String nickname, String avatar, String phone) {
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn.toSeconds())
                .forceChangePassword(forceChangePassword)
                .requireTotp(false)
                .userInfo(LoginVO.UserInfoVO.builder()
                        .userId(userId)
                        .username(username)
                        .nickname(nickname)
                        .avatar(avatar)
                        .phone(phone)
                        .build())
                .build();
    }

    private void handleMultiLogin(Long userId, ClientType clientType) {
        if (clientType == ClientType.PC) {
            String userKey = CacheConstants.LOGIN_USER_KEY + userId + ":" + clientType.getCode();
            String oldToken = redisTemplate.opsForValue().get(userKey);
            if (XuStrUtil.isNotBlank(oldToken)) {
                String oldTokenKey = CacheConstants.TOKEN_KEY + oldToken;
                redisTemplate.delete(oldTokenKey);
                log.info("用户[{}]PC 端被踢出，新设备登录", userId);
            }
        }
    }

    private void recordLoginFail(String username, Long tenantId, String message) {
        log.warn("用户[{}]登录失败: {}", username, message);
        try {
            String userAgent = getUserAgent();
            String clientIp = getClientIp();
            LoginLogDTO dto = LoginLogDTO.builder()
                    .username(username)
                    .status(1)
                    .message(message)
                    .ipAddr(clientIp)
                    .loginLocation(resolveLocation(clientIp))
                    .browser(parseBrowser(userAgent))
                    .os(parseOs(userAgent))
                    .build();
            systemServiceClient.recordLoginLog(dto);
        } catch (Exception e) {
            log.error("记录登录失败日志异常", e);
        }
    }

    private void recordLoginSuccess(Long userId, String username, ClientType clientType) {
        log.info("用户[{}]登录成功, 客户端类型: {}", username, clientType.getCode());
        try {
            String userAgent = getUserAgent();
            String clientIp = getClientIp();
            LoginLogDTO dto = LoginLogDTO.builder()
                    .username(username)
                    .status(0)
                    .message("登录成功")
                    .clientType(clientType.getCode())
                    .ipAddr(clientIp)
                    .loginLocation(resolveLocation(clientIp))
                    .browser(parseBrowser(userAgent))
                    .os(parseOs(userAgent))
                    .build();
            systemServiceClient.recordLoginLog(dto);
        } catch (Exception e) {
            log.error("记录登录成功日志异常", e);
        }
    }

    private String getClientIp() {
        try {
            var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                var request = sra.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.substring(0, ip.indexOf(',')).trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("获取客户端 IP 失败", e);
        }
        return "unknown";
    }

    private String getUserAgent() {
        try {
            var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                return sra.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("获取 User-Agent 失败", e);
        }
        return null;
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg")) return "Edge";
        if (ua.contains("chrome") && !ua.contains("edg")) return "Chrome";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("safari") && !ua.contains("chrome")) return "Safari";
        if (ua.contains("opera") || ua.contains("opr")) return "Opera";
        if (ua.contains("msie") || ua.contains("trident")) return "IE";
        return "unknown";
    }

    private String resolveLocation(String ip) {
        return HanIpUtil.getLocation(ip);
    }

    private String parseOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        if (userAgent.contains("Windows NT 10")) return "Windows 10";
        if (userAgent.contains("Windows NT 11")) return "Windows 11";
        if (userAgent.contains("Windows NT 6.3")) return "Windows 8.1";
        if (userAgent.contains("Windows NT 6.1")) return "Windows 7";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac OS X")) return "macOS";
        if (userAgent.contains("Linux") && userAgent.contains("Android")) return "Android";
        if (userAgent.contains("Linux")) return "Linux";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        return "unknown";
    }

    private void checkLoginLockout(String username, Long tenantId) {
        String key = buildLoginFailKey(username, tenantId);
        String failCount = redisTemplate.opsForValue().get(key);
        if (failCount != null && Integer.parseInt(failCount) >= MAX_LOGIN_ATTEMPTS) {
            Long ttl = redisTemplate.getExpire(key);
            long minutes = (ttl != null && ttl > 0) ? (ttl + 59) / 60 : LOCKOUT_DURATION.toMinutes();
            throw new BusinessException("账户已锁定，请" + minutes + "分钟后再试");
        }
    }

    private int incrementLoginFail(String username, Long tenantId) {
        String key = buildLoginFailKey(username, tenantId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, LOCKOUT_DURATION);
        }
        return MAX_LOGIN_ATTEMPTS - (count != null ? count.intValue() : 1);
    }

    private void clearLoginFail(String username, Long tenantId) {
        redisTemplate.delete(buildLoginFailKey(username, tenantId));
    }

    private String buildLoginFailKey(String username, Long tenantId) {
        String tenantSegment = tenantId != null ? String.valueOf(tenantId) : "default";
        return LOGIN_FAIL_KEY + tenantSegment + ":" + username;
    }
}
