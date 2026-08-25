package com.han.auth.service.impl;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.api.system.domain.LoginLogDTO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.api.tenant.domain.TenantVO;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.IdentitySelectDTO;
import com.han.auth.domain.IdentityVO;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.TenantSimpleVo;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.IAuthService;
import com.han.auth.service.TotpService;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.core.util.HanIpUtil;
import com.han.common.core.util.HanSecureUtil;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.util.XuIdUtil;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.core.util.XuStrUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final CaptchaSettingService captchaSettingService;

    private static final Duration PC_TOKEN_EXPIRE = Duration.ofMinutes(30);
    private static final Duration APP_TOKEN_EXPIRE = Duration.ofDays(7);
    private static final Duration WECHAT_TOKEN_EXPIRE = Duration.ofDays(30);

    private static final Duration PC_REFRESH_EXPIRE = Duration.ofDays(7);
    private static final Duration APP_REFRESH_EXPIRE = Duration.ofDays(30);
    private static final Duration WECHAT_REFRESH_EXPIRE = Duration.ofDays(90);
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);

    /** 会话索引 Set 的过期时间：覆盖 access token 最大有效期（微信 30 天），留一天余量。 */
    private static final Duration SESSION_INDEX_TTL = Duration.ofDays(31);

    private static final int PASSWORD_EXPIRE_DAYS = 90;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(10);
    private static final String LOGIN_FAIL_KEY = CacheConstants.CACHE_PREFIX + "login_fail:";

    /** 登录时多学校身份选择的一次性票据（Redis 5 分钟，GETDEL 一次性消费）。 */
    private static final String IDENTITY_TICKET_KEY = CacheConstants.CACHE_PREFIX + "identity_ticket:";
    private static final Duration IDENTITY_TICKET_TTL = Duration.ofMinutes(5);

    /** 校内岗位编码到中文名映射；未命中返回空串。 */
    private static final Map<String, String> DUTY_NAME_MAP = Map.of(
            "SCHOOL_ADMIN", "管理员",
            "TEACHER", "普通教师");

    /** 教育身份查询失败时统一文案（禁止降级账号级 token）。 */
    private static final String IDENTITY_SERVICE_UNAVAILABLE = "身份服务暂时不可用，请稍后重试";
    /** 教育入口账号当前无有效身份时的文案。 */
    private static final String NO_VALID_IDENTITY = "当前账号没有有效教育身份，请联系管理员";
    /** PC 管理端下非管理身份/无管理角色时的文案。 */
    private static final String NO_MANAGEMENT_PERMISSION = "该身份没有管理端权限";
    /** 多身份账号未显式选择身份时的文案。 */
    private static final String MULTI_IDENTITY_SELECT_REQUIRED = "当前账号存在多个教育身份，请先选择身份";

    @Override
    public LoginVO login(LoginDTO dto) {
        // 消费 sys.account.captchaEnabled：浏览器入口（PC/H5）验证码必填必校验，关闭时跳过。
        // App / 微信登录无验证码控件，保持原「传了才校验」行为。
        if (captchaSettingService.isCaptchaEnabled()) {
            boolean browserLogin = dto.getClientType() == ClientType.PC || dto.getClientType() == ClientType.H5;
            if (browserLogin && XuStrUtil.isBlank(dto.getCode())) {
                throw new BusinessException("验证码不能为空");
            }
            if (XuStrUtil.isNotBlank(dto.getCode())) {
                validateCaptcha(dto.getCode(), dto.getUuid());
            }
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

        return issueLoginIdentityAware(user, dto.getClientType(), forceChangePwd);
    }

    /**
     * 密码登录与社交登录共用的登录态签发出口：
     * 账号状态校验 → 租户有效性校验 → 权限装载 → token 签发 → 互踢 → 登录日志。
     */
    @Override
    public LoginVO issueLoginForUser(UserVO user, ClientType clientType, boolean forceChangePassword) {
        validateLoginAccount(user);
        requireManagementRole(user, clientType);
        validateTenantAvailable(user);
        Set<String> permissions = loadPermissions(user.getUserId());
        Set<Long> dataScopeDeptIds = loadDataScopeDeptIds(user.getUserId());
        LoginUser loginUser = buildLoginUser(user, clientType, permissions, dataScopeDeptIds);
        return persistTokens(user, clientType, forceChangePassword, loginUser);
    }

    /**
     * 身份感知签发入口：0 身份走账号级、1 身份自动绑定、≥2 身份返回 requireIdentity + 一次性票据。
     *
     * <p>教育身份查询失败时关闭登录，绝不降级账号级 token；教育入口账号当前无有效身份时
     * 禁止登录（区别于真无教育身份的系统账号）。
     */
    @Override
    public LoginVO issueLoginIdentityAware(UserVO user, ClientType clientType, boolean forceChangePassword) {
        validateLoginAccount(user);
        List<ClassroomIdentityVO> identities = loadClassroomIdentities(user.getUserId());
        if (identities.isEmpty()) {
            if (user.isEducationAccount() || user.isEducationBound()) {
                throw new BusinessException(NO_VALID_IDENTITY);
            }
            return issueLoginForUser(user, clientType, forceChangePassword);
        }
        if (identities.size() == 1) {
            return issueLoginForResolvedIdentity(user, clientType, forceChangePassword, identities.get(0));
        }
        String ticket = generateToken();
        storeIdentityTicket(ticket, user.getUserId(), user.getTenantId(), clientType, forceChangePassword);
        return LoginVO.builder()
                .requireIdentity(true)
                .identityTicket(ticket)
                .identities(identities.stream().map(item -> toIdentityVO(item, false)).toList())
                .build();
    }

    /**
     * 按指定学校身份签发登录态（数字校园 / 身份选择 / 切换 / Refresh 复用的统一出口）。
     *
     * <p>{@code identityId} 为 null 时按单身份自动选择处理：0 个有效身份抛「没有有效教育身份」，
     * ≥2 个抛「请先选择身份」；非 null 时该校身份必须属于当前账号且有效。
     */
    @Override
    public LoginVO issueLoginForIdentity(UserVO user, ClientType clientType, boolean forceChangePassword,
                                         Long identityId) {
        validateLoginAccount(user);
        List<ClassroomIdentityVO> identities = loadClassroomIdentities(user.getUserId());
        return issueLoginForResolvedIdentity(user, clientType, forceChangePassword,
                resolveIdentity(identities, identityId));
    }

    /** 从有效身份列表解析目标身份；未命中或不可自动判定时抛业务错误。 */
    private ClassroomIdentityVO resolveIdentity(List<ClassroomIdentityVO> identities, Long identityId) {
        if (identityId != null) {
            return identities.stream()
                    .filter(item -> item != null
                            && identityId.equals(parseLongOrNull(item.getIdentityId())))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("所选身份无效或不属于当前账号"));
        }
        if (identities.isEmpty()) {
            throw new BusinessException(NO_VALID_IDENTITY);
        }
        if (identities.size() > 1) {
            throw new BusinessException(MULTI_IDENTITY_SELECT_REQUIRED);
        }
        return identities.get(0);
    }

    /**
     * 对已解析出的单个身份完成签发：租户校验 → PC 管理端可用性门禁 → 权限装载 → token 签发。
     */
    private LoginVO issueLoginForResolvedIdentity(UserVO user, ClientType clientType, boolean forceChangePassword,
                                                  ClassroomIdentityVO identity) {
        validateTenantAvailable(user);
        requireIdentityManagementAvailable(user, clientType, identity);
        Set<String> permissions = loadPermissions(user.getUserId());
        Set<Long> dataScopeDeptIds = loadDataScopeDeptIds(user.getUserId());
        ManagementRoles managementRoles = loadManagementRoles(user.getUserId(), user.getRoleKeys());
        LoginUser loginUser = buildIdentityLoginUser(user, clientType, permissions, dataScopeDeptIds,
                identity, managementRoles);
        return persistTokens(user, clientType, forceChangePassword, loginUser);
    }

    /** 账号状态公共校验（密码登录与身份选择/切换复用）。 */
    private void validateLoginAccount(UserVO user) {
        if (user == null || user.getUserId() == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 0) {
            recordLoginFail(user.getUsername(), user.getTenantId(), "账号已停用");
            throw new BusinessException("账号已停用，请联系管理员");
        }
    }

    /** 非默认租户时校验租户有效性，失败降级为跳过（与旧登录行为一致）。 */
    private void validateTenantAvailable(UserVO user) {
        if (user.getTenantId() != null && user.getTenantId() != 1L) {
            try {
                R<Boolean> validResult = tenantServiceClient.checkTenantValid(user.getTenantId());
                if (validResult.getData() == null || !validResult.getData()) {
                    recordLoginFail(user.getUsername(), user.getTenantId(), "租户已停用或已过期");
                    throw new BusinessException("租户已停用或已过期，请联系管理员");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("校验租户有效性失败，跳过校验: tenantId={}", user.getTenantId(), e);
            }
        }
    }

    private Set<String> loadPermissions(Long userId) {
        R<Set<String>> permsResult = systemServiceClient.getPermissionsByUserId(userId);
        return permsResult != null ? permsResult.getData() : null;
    }

    private Set<Long> loadDataScopeDeptIds(Long userId) {
        R<Set<Long>> dataScopeDeptIdsResult = systemServiceClient.getDataScopeDeptIds(userId);
        return dataScopeDeptIdsResult != null ? dataScopeDeptIdsResult.getData() : null;
    }

    /** 生成 Token 并写入 Redis（互踢、在线标记、登录日志由本方法统一完成）。 */
    private LoginVO persistTokens(UserVO user, ClientType clientType, boolean forceChangePassword,
                                  LoginUser loginUser) {
        String accessToken = generateToken();
        String refreshToken = generateToken();

        Duration tokenExpire = getTokenExpire(clientType);
        Duration refreshExpire = getRefreshExpire(clientType);
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire.toMillis());

        handleMultiLogin(user.getUserId(), clientType);

        String tokenKey = CacheConstants.TOKEN_KEY + accessToken;
        String refreshKey = CacheConstants.REFRESH_TOKEN_KEY + refreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + user.getUserId() + ":" + clientType.getCode();

        redisTemplate.opsForValue().set(tokenKey, XuJsonUtil.toJsonString(loginUser), tokenExpire);
        redisTemplate.opsForValue().set(refreshKey, accessToken, refreshExpire);
        redisTemplate.opsForValue().set(userKey, accessToken, tokenExpire);
        markOnline(accessToken);
        addToSessionIndex(user.getUserId(), loginUser, accessToken);

        recordLoginSuccess(user.getUserId(), user.getUsername(), clientType);

        return buildLoginVO(accessToken, refreshToken, tokenExpire, forceChangePassword, user.getUserId(),
                user.getUsername(), user.getNickname(), user.getAvatar(), user.getPhone());
    }

    /** 会话索引：user 会话 Set 键。 */
    private String userSessionsKey(Long userId) {
        return CacheConstants.SESSION_USER_KEY + userId;
    }

    /** 会话索引：identity 会话 Set 键。 */
    private String identitySessionsKey(Long userId, Long identityId) {
        return CacheConstants.SESSION_IDENTITY_KEY + userId + ":" + identityId;
    }

    /** 身份索引：账号下全部 identityId 的 Set 键。 */
    private String userIdentitiesKey(Long userId) {
        return CacheConstants.IDENTITIES_USER_KEY + userId;
    }

    /** 将新签发的 accessToken 写入 user 会话 Set，identityScoped 时同时写入 identity Set 与 user identities Set。 */
    private void addToSessionIndex(Long userId, LoginUser loginUser, String accessToken) {
        if (userId == null || loginUser == null) {
            return;
        }
        String userSessions = userSessionsKey(userId);
        redisTemplate.opsForSet().add(userSessions, accessToken);
        redisTemplate.expire(userSessions, SESSION_INDEX_TTL);
        if (loginUser.isIdentityScoped() && loginUser.getIdentityId() != null) {
            String identitySessions = identitySessionsKey(userId, loginUser.getIdentityId());
            redisTemplate.opsForSet().add(identitySessions, accessToken);
            redisTemplate.expire(identitySessions, SESSION_INDEX_TTL);

            String identities = userIdentitiesKey(userId);
            redisTemplate.opsForSet().add(identities, String.valueOf(loginUser.getIdentityId()));
            redisTemplate.expire(identities, SESSION_INDEX_TTL);
        }
    }

    /** 从 user 会话 Set 移除 accessToken，identityScoped 时同时从 identity Set 移除。 */
    private void removeFromSessionIndex(Long userId, LoginUser loginUser, String accessToken) {
        if (userId == null || loginUser == null) {
            return;
        }
        redisTemplate.opsForSet().remove(userSessionsKey(userId), accessToken);
        if (loginUser.isIdentityScoped() && loginUser.getIdentityId() != null) {
            redisTemplate.opsForSet().remove(identitySessionsKey(userId, loginUser.getIdentityId()), accessToken);
        }
    }

    /** 读取 Set 成员，空集合按空集处理（Redis 返回 null 或不存在）。 */
    private Set<String> sessionMembers(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : Set.of();
    }

    /** Refresh 失败时作废旧登录态：删旧 access/refresh token、在线标记，并从会话索引移除旧 token。 */
    private void deleteOldRefreshSession(LoginUser oldLoginUser, String oldAccessToken, String refreshKey) {
        redisTemplate.delete(CacheConstants.TOKEN_KEY + oldAccessToken);
        redisTemplate.delete(refreshKey);
        redisTemplate.delete(CacheConstants.ONLINE_KEY + oldAccessToken);
        removeFromSessionIndex(oldLoginUser.getUserId(), oldLoginUser, oldAccessToken);
    }

    /** 管理端 PC 登录必须有管理端角色；校端登录走独立兼容凭证，不复用此门禁。 */
    private static void requireManagementRole(UserVO user, ClientType clientType) {
        if (clientType != ClientType.PC || user.isAdmin()) {
            return;
        }
        Set<String> roleKeys = user.getRoleKeys();
        boolean hasManagementRole = roleKeys != null && roleKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .map(key -> key.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(key -> !Set.of("teacher", "student").contains(key));
        if (!hasManagementRole) {
            throw new BusinessException("该账号未配置管理端权限，不能登录管理端");
        }
    }

    /**
     * PC 管理端强制身份可用性：普通教师/学生身份不能签发空菜单 token；
     * SCHOOL_ADMIN 无管理角色时按身份侧给出的原因禁用。H5/App 课堂不受此门禁限制。
     */
    private void requireIdentityManagementAvailable(UserVO user, ClientType clientType,
                                                    ClassroomIdentityVO identity) {
        if (clientType != ClientType.PC || user.isAdmin()) {
            return;
        }
        if (identity == null) {
            throw new BusinessException(NO_VALID_IDENTITY);
        }
        if (!isTeacherSchoolAdmin(identity)) {
            throw new BusinessException(NO_MANAGEMENT_PERMISSION);
        }
        if (identity.isManagementAvailable()) {
            return;
        }
        String reason = XuStrUtil.isNotBlank(identity.getManagementUnavailableReason())
                ? identity.getManagementUnavailableReason() : NO_MANAGEMENT_PERMISSION;
        throw new BusinessException(reason);
    }

    /** roleKey 命中 teacher/student 即视为非管理端角色，与 han-system 管理角色 SQL 同口径。 */
    private static boolean isTeacherOrStudentKey(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            return false;
        }
        String lower = roleKey.toLowerCase(Locale.ROOT);
        return lower.contains("teacher") || lower.contains("student");
    }

    /** 身份会话的管理角色 ID/Key 集合（按 roleKey 过滤 teacher/student，保证与权限一致）。 */
    private record ManagementRoles(Set<Long> roleIds, Set<String> roleKeys) {
    }

    /**
     * 加载账号的管理角色（排除 teacher/student），供身份会话写入一致的 roleIds/roleKeys。
     *
     * <p>优先按 {@code getRolesByUserId} 的 roleId+roleKey 配对过滤；查询失败时回退到
     * roleKeys 过滤（roleIds 置空，保守不残留账号管理角色 ID）。
     */
    private ManagementRoles loadManagementRoles(Long userId, Set<String> accountRoleKeys) {
        try {
            R<List<RoleVO>> result = systemServiceClient.getRolesByUserId(userId);
            if (result != null && result.getCode() == Constants.SUCCESS && result.getData() != null) {
                Set<Long> roleIds = new LinkedHashSet<>();
                Set<String> roleKeys = new LinkedHashSet<>();
                for (RoleVO role : result.getData()) {
                    if (role == null || role.getRoleKey() == null || role.getRoleKey().isBlank()) {
                        continue;
                    }
                    if (isTeacherOrStudentKey(role.getRoleKey())) {
                        continue;
                    }
                    if (role.getStatus() != null && role.getStatus() != 0) {
                        continue;
                    }
                    if (role.getRoleId() != null) {
                        roleIds.add(role.getRoleId());
                    }
                    roleKeys.add(role.getRoleKey());
                }
                return new ManagementRoles(Set.copyOf(roleIds), Set.copyOf(roleKeys));
            }
        } catch (Exception e) {
            log.warn("查询账号管理角色失败，回退到 roleKeys 过滤: userId={}", userId, e);
        }
        return new ManagementRoles(Set.of(), filterManagementRoleKeys(accountRoleKeys));
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

        LoginUser oldLoginUser = XuJsonUtil.parseObject(userJson, LoginUser.class);
        if (oldLoginUser == null || oldLoginUser.getUserId() == null || oldLoginUser.getClientType() == null) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        ClientType clientType = oldLoginUser.getClientType();

        // 刷新重查账号 + 当前身份 + 人员/离校/学校/岗位/角色，重建完整 LoginUser，不复用旧权限。
        UserVO user = requireActiveUser(oldLoginUser.getUserId());

        LoginUser loginUser;
        if (oldLoginUser.isIdentityScoped()) {
            ClassroomIdentityVO identity = findValidIdentity(user.getUserId(), oldLoginUser.getIdentityId());
            if (identity == null) {
                // 身份失效：删旧 access/refresh token 并从会话索引移除后 401，禁止继续持有旧权限。
                deleteOldRefreshSession(oldLoginUser, oldAccessToken, refreshKey);
                throw new UnauthorizedException("当前身份已失效，请重新登录");
            }
            // Refresh 再执行 PC 管理门禁：PC 身份已无管理能力时拒绝续期，要求重登；
            // H5/App 教师不受 PC 门禁影响。
            try {
                requireIdentityManagementAvailable(user, clientType, identity);
            } catch (BusinessException e) {
                deleteOldRefreshSession(oldLoginUser, oldAccessToken, refreshKey);
                throw new UnauthorizedException("当前身份已无管理端权限，请重新登录");
            }
            Set<String> permissions = loadPermissions(user.getUserId());
            Set<Long> dataScopeDeptIds = loadDataScopeDeptIds(user.getUserId());
            ManagementRoles managementRoles = loadManagementRoles(user.getUserId(), user.getRoleKeys());
            loginUser = buildIdentityLoginUser(user, clientType, permissions, dataScopeDeptIds,
                    identity, managementRoles);
        } else {
            Set<String> permissions = loadPermissions(user.getUserId());
            Set<Long> dataScopeDeptIds = loadDataScopeDeptIds(user.getUserId());
            loginUser = buildLoginUser(user, clientType, permissions, dataScopeDeptIds);
        }

        String newAccessToken = generateToken();
        String newRefreshToken = generateToken();

        Duration tokenExpire = getTokenExpire(clientType);
        Duration refreshExpire = getRefreshExpire(clientType);
        loginUser.setExpireTime(System.currentTimeMillis() + tokenExpire.toMillis());

        redisTemplate.delete(oldTokenKey);
        redisTemplate.delete(refreshKey);
        removeFromSessionIndex(oldLoginUser.getUserId(), oldLoginUser, oldAccessToken);

        String newTokenKey = CacheConstants.TOKEN_KEY + newAccessToken;
        String newRefreshKey = CacheConstants.REFRESH_TOKEN_KEY + newRefreshToken;
        String userKey = CacheConstants.LOGIN_USER_KEY + loginUser.getUserId() + ":" + clientType.getCode();

        redisTemplate.opsForValue().set(newTokenKey, XuJsonUtil.toJsonString(loginUser), tokenExpire);
        redisTemplate.opsForValue().set(newRefreshKey, newAccessToken, refreshExpire);
        redisTemplate.opsForValue().set(userKey, newAccessToken, tokenExpire);
        markOnline(newAccessToken);
        addToSessionIndex(loginUser.getUserId(), loginUser, newAccessToken);

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
            if (loginUser != null && loginUser.getUserId() != null && loginUser.getClientType() != null) {
                redisTemplate.delete(CacheConstants.ONLINE_KEY + token);
                // 从 user 会话 Set 移除当前 token；identityScoped 时同时从 identity Set 移除。
                removeFromSessionIndex(loginUser.getUserId(), loginUser, token);

                // login_user 仅当其值等于当前 accessToken 时才删除，避免误删同设备新会话索引。
                String userKey = CacheConstants.LOGIN_USER_KEY + loginUser.getUserId() + ":" + loginUser.getClientType().getCode();
                String deviceToken = redisTemplate.opsForValue().get(userKey);
                if (token.equals(deviceToken)) {
                    redisTemplate.delete(userKey);
                }

                if (loginUser.isIdentityScoped() && loginUser.getIdentityId() != null) {
                    // 身份会话只撤当前身份课堂凭证，不误撤其他身份。
                    revokeIdentityClassroomSession(loginUser.getUserId(), loginUser.getIdentityId());
                } else {
                    revokeClassroomSession(loginUser.getUserId());
                }
            }
        }

        redisTemplate.delete(tokenKey);
        log.info("用户登出成功");
    }

    /**
     * 登出时一并作废这个人的三课堂兼容凭证。
     *
     * <p>兼容凭证是自包含 JWS、有效期一小时，光删 Han 的登录态撤销不了它：
     * 只要 Redis 里的会话键还在，Han 网关就会继续放行，用户「登出」之后
     * 拿旧凭证仍能读三课堂业务数据。撤销的唯一兑现点就是删这个会话键。
     *
     * <p>索引键由 han-system 的 {@code LegacyTokenIssuer} 按人写入，这里按同样的约定读回，
     * 两个服务共用同一个 Redis。取不到就什么都不做——这个人本来就没换过兼容凭证。
     */
    private void revokeClassroomSession(Long hanUserId) {
        if (hanUserId == null) {
            return;
        }
        String activeKey = ClassroomTokenCodec.ACTIVE_KEY_PREFIX + hanUserId;
        String classroomToken = redisTemplate.opsForValue().get(activeKey);
        redisTemplate.delete(activeKey);
        if (XuStrUtil.isBlank(classroomToken)) {
            return;
        }
        String tokenId = classroomTokenId(classroomToken);
        if (tokenId != null) {
            redisTemplate.delete(ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId);
            log.info("登出同时作废三课堂兼容凭证, userId={}", hanUserId);
        }
    }

    /** 只读 payload 里的 jti，不验签：这里是撤销，拿不准就多删一个键，不存在误放行风险。 */
    private static String classroomTokenId(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            String json = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
                    java.nio.charset.StandardCharsets.UTF_8);
            Object jti = XuJsonUtil.parseObject(json, java.util.Map.class).get("jti");
            return jti instanceof String text && !text.isBlank() ? text : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 按身份粒度作废该身份持有的课堂正式凭证（身份级 Active Key + 会话键）。 */
    private void revokeIdentityClassroomSession(Long hanUserId, Long identityId) {
        if (hanUserId == null || identityId == null) {
            return;
        }
        String identityKey = ClassroomTokenCodec.activeIdentityKey(String.valueOf(hanUserId), String.valueOf(identityId));
        String classroomToken = redisTemplate.opsForValue().get(identityKey);
        redisTemplate.delete(identityKey);
        if (XuStrUtil.isNotBlank(classroomToken)) {
            String tokenId = classroomTokenId(classroomToken);
            if (tokenId != null) {
                redisTemplate.delete(ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId);
                log.info("撤销身份课堂凭证, userId={}, identityId={}", hanUserId, identityId);
            }
        }
    }

    /**
     * 会话撤销：{@code identityId} 为空撤销该账号全部会话与课堂凭证，指定时只撤该身份。
     *
     * <p>撤销全部会话以 {@code auth:sessions:user:{userId}} 为唯一依据，不再只遍历
     * {@code login_user:{userId}:{clientType}} 读最后一枚 token；身份粒度撤销以
     * {@code auth:sessions:identity:{userId}:{identityId}} 为依据，并作废该身份课堂凭证。
     */
    @Override
    public void revokeSession(Long userId, Long identityId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (identityId != null) {
            revokeIdentitySessions(userId, identityId);
        } else {
            revokeAccountSessions(userId);
        }
    }

    /** 身份级撤销：删该身份全部客户端会话 + 身份课堂 Active/Session Key + 身份索引。 */
    private void revokeIdentitySessions(Long userId, Long identityId) {
        String identitySessions = identitySessionsKey(userId, identityId);
        for (String accessToken : sessionMembers(identitySessions)) {
            redisTemplate.delete(CacheConstants.TOKEN_KEY + accessToken);
            redisTemplate.delete(CacheConstants.ONLINE_KEY + accessToken);
            redisTemplate.opsForSet().remove(userSessionsKey(userId), accessToken);
        }
        redisTemplate.delete(identitySessions);
        revokeIdentityClassroomSession(userId, identityId);
        redisTemplate.opsForSet().remove(userIdentitiesKey(userId), String.valueOf(identityId));
    }

    /** 账号级撤销：删全部客户端会话 + 各身份课堂凭证 + 账号级课堂兼容凭证 + 全部会话/身份索引。 */
    private void revokeAccountSessions(Long userId) {
        String userSessions = userSessionsKey(userId);
        for (String accessToken : sessionMembers(userSessions)) {
            redisTemplate.delete(CacheConstants.TOKEN_KEY + accessToken);
            redisTemplate.delete(CacheConstants.ONLINE_KEY + accessToken);
        }
        // 旧索引兜底：部署前签发的 token 可能未进新会话 Set，遍历全部 ClientType 读
        // login_user:{userId}:{clientType} 指向的 accessToken 一并删除，避免漏撤旧 Token。
        for (ClientType clientType : ClientType.values()) {
            String userKey = CacheConstants.LOGIN_USER_KEY + userId + ":" + clientType.getCode();
            String accessToken = redisTemplate.opsForValue().get(userKey);
            if (XuStrUtil.isNotBlank(accessToken)) {
                redisTemplate.delete(CacheConstants.TOKEN_KEY + accessToken);
                redisTemplate.delete(CacheConstants.ONLINE_KEY + accessToken);
            }
            redisTemplate.delete(userKey);
        }
        String identities = userIdentitiesKey(userId);
        for (String identityId : sessionMembers(identities)) {
            Long parsedIdentityId = parseLongOrNull(identityId);
            if (parsedIdentityId != null) {
                redisTemplate.delete(identitySessionsKey(userId, parsedIdentityId));
                revokeIdentityClassroomSession(userId, parsedIdentityId);
            }
        }
        revokeClassroomSession(userId);
        redisTemplate.delete(userSessions);
        redisTemplate.delete(identities);
    }

    @Override
    public List<TenantSimpleVo> getMyTenants() {
        LoginUser current = requireLoginUser();
        String username = current.getUsername();
        List<Map<String, Object>> accounts = loadUserTenantAccounts(username);
        if (accounts.isEmpty()) {
            return List.of();
        }

        Map<Long, TenantVO> tenants = loadValidTenantMap();
        Long currentTenantId = current.getTenantId();
        return accounts.stream()
                .map(account -> {
                    Long tenantId = toLong(account.get("tenantId"));
                    Integer accountStatus = toInteger(account.get("status"));
                    TenantVO tenant = tenantId != null ? tenants.get(tenantId) : null;
                    if (tenant == null && tenantId != null) {
                        tenant = loadTenantById(tenantId);
                    }
                    Integer tenantStatus = tenant != null ? tenant.getStatus() : 1;
                    return TenantSimpleVo.builder()
                            .tenantId(tenantId)
                            .tenantName(tenant != null && XuStrUtil.isNotBlank(tenant.getTenantName())
                                    ? tenant.getTenantName()
                                    : "tenant-" + tenantId)
                            .status(accountStatus != null && accountStatus == 0 && tenantStatus != null && tenantStatus == 0 ? 0 : 1)
                            .current(tenantId != null && tenantId.equals(currentTenantId))
                            .build();
                })
                .toList();
    }

    @Override
    public LoginVO switchTenant(Long tenantId, String authorization) {
        if (tenantId == null) {
            throw new BusinessException("绉熸埛ID涓嶈兘涓虹┖");
        }

        LoginUser current = requireLoginUser();
        UserVO targetUser = requireSwitchTargetUser(current, tenantId);
        ClientType clientType = current.getClientType() != null ? current.getClientType() : ClientType.PC;

        // 租户切换后重新检查目标租户身份：不直接恢复账号级权限，多身份仍需选择。
        LoginVO result = issueLoginIdentityAware(targetUser, clientType, false);
        logout(authorization);
        return result;
    }

    @Override
    public LoginVO selectIdentity(IdentitySelectDTO dto) {
        if (dto == null || XuStrUtil.isBlank(dto.getIdentityTicket())) {
            throw new BusinessException("身份票据不能为空");
        }
        if (dto.getIdentityId() == null) {
            throw new BusinessException("身份ID不能为空");
        }
        IdentityTicket ticket = consumeIdentityTicket(dto.getIdentityTicket());
        if (ticket == null) {
            throw new BusinessException("身份票据已过期或已使用，请重新登录");
        }
        if (ticket.userId() == null) {
            throw new BusinessException("身份票据无效，请重新登录");
        }
        UserVO user = requireActiveUser(ticket.userId());
        // 校验用户租户与票据租户一致，防止票据被跨租户账号消费。
        if (!Objects.equals(ticket.tenantId(), user.getTenantId())) {
            throw new BusinessException("身份票据与当前账号租户不一致，请重新登录");
        }
        ClientType clientType = ClientType.fromCode(ticket.clientType());
        // 选择后原样恢复登录时的 forceChangePassword。
        return issueLoginForIdentity(user, clientType, ticket.forceChangePassword(), dto.getIdentityId());
    }

    @Override
    public List<IdentityVO> getMyIdentities() {
        LoginUser current = requireLoginUser();
        Long currentIdentityId = current.getIdentityId();
        return loadClassroomIdentities(current.getUserId()).stream()
                .map(item -> toIdentityVO(item, currentIdentityId != null && item.getIdentityId() != null
                        && currentIdentityId.equals(parseLongOrNull(item.getIdentityId()))))
                .toList();
    }

    @Override
    public LoginVO switchIdentity(Long identityId, String authorization) {
        if (identityId == null) {
            throw new BusinessException("身份ID不能为空");
        }
        LoginUser current = requireLoginUser();
        UserVO user = requireActiveUser(current.getUserId());
        ClientType clientType = current.getClientType() != null ? current.getClientType() : ClientType.PC;

        // 先作废旧登录态。logout 会删除同 userId+clientType 的 login_user 索引键，
        // 因此必须在新 userKey 写入前执行，否则会误删刚换发的新索引。
        // 旧身份课堂凭证按身份粒度作废，登录态整体作废。
        if (current.isIdentityScoped() && current.getIdentityId() != null) {
            revokeIdentityClassroomSession(current.getUserId(), current.getIdentityId());
        }
        logout(authorization);

        return issueLoginForIdentity(user, clientType, false, identityId);
    }

    /**
     * 读取当前账号有效身份列表。身份服务异常或返回失败时关闭登录，
     * 抛「身份服务暂时不可用」，绝不降级为账号级 token。
     */
    private List<ClassroomIdentityVO> loadClassroomIdentities(Long userId) {
        if (userId == null) {
            return List.of();
        }
        R<List<ClassroomIdentityVO>> result;
        try {
            result = systemServiceClient.listClassroomIdentities(userId);
        } catch (Exception e) {
            log.warn("查询学校身份列表异常，关闭登录: userId={}", userId, e);
            throw new BusinessException(IDENTITY_SERVICE_UNAVAILABLE);
        }
        if (result == null || result.getCode() != Constants.SUCCESS || result.getData() == null) {
            log.warn("查询学校身份列表失败，关闭登录: userId={}, code={}", userId,
                    result != null ? result.getCode() : null);
            throw new BusinessException(IDENTITY_SERVICE_UNAVAILABLE);
        }
        return result.getData();
    }

    /** 身份必须属于当前账号且仍有效。 */
    private ClassroomIdentityVO findValidIdentity(Long userId, Long identityId) {
        if (userId == null || identityId == null) {
            return null;
        }
        return loadClassroomIdentities(userId).stream()
                .filter(item -> item.getIdentityId() != null
                        && identityId.equals(parseLongOrNull(item.getIdentityId())))
                .findFirst()
                .orElse(null);
    }

    private UserVO requireActiveUser(Long userId) {
        R<UserVO> userResult = systemServiceClient.getUserById(userId);
        if (userResult == null || userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            throw new BusinessException("用户不存在");
        }
        UserVO user = userResult.getData();
        if (user.getStatus() == null || user.getStatus() != 0) {
            throw new BusinessException("账号已停用，请联系管理员");
        }
        return user;
    }

    private void storeIdentityTicket(String ticket, Long userId, Long tenantId, ClientType clientType,
                                     boolean forceChangePassword) {
        IdentityTicket payload = new IdentityTicket(userId, tenantId,
                clientType != null ? clientType.getCode() : ClientType.PC.getCode(), forceChangePassword);
        redisTemplate.opsForValue().set(IDENTITY_TICKET_KEY + ticket,
                XuJsonUtil.toJsonString(payload), IDENTITY_TICKET_TTL);
    }

    /** GETDEL 原子读取并删除，保证票据一次性；不存在或解析失败返回 null。 */
    private IdentityTicket consumeIdentityTicket(String ticket) {
        String json = redisTemplate.opsForValue().getAndDelete(IDENTITY_TICKET_KEY + ticket);
        if (XuStrUtil.isBlank(json)) {
            return null;
        }
        try {
            Map<String, Object> values = XuJsonUtil.parseObject(json, Map.class);
            if (values == null) {
                return null;
            }
            Long userId = toLong(values.get("userId"));
            Long tenantId = toLong(values.get("tenantId"));
            String clientType = values.get("clientType") instanceof String text && !text.isBlank()
                    ? text : ClientType.PC.getCode();
            boolean forceChangePassword = Boolean.TRUE.equals(values.get("forceChangePassword"));
            return new IdentityTicket(userId, tenantId, clientType, forceChangePassword);
        } catch (RuntimeException e) {
            log.warn("解析身份票据失败: ticket={}", ticket, e);
            return null;
        }
    }

    private IdentityVO toIdentityVO(ClassroomIdentityVO identity, boolean current) {
        boolean managementAvailable = identity != null && identity.isManagementAvailable()
                && "TEACHER".equalsIgnoreCase(blankToEmpty(identity.getPersonType()));
        String managementUnavailableReason = managementAvailable ? ""
                : (identity != null ? blankToEmpty(identity.getManagementUnavailableReason()) : "");
        return IdentityVO.builder()
                .identityId(parseLongOrNull(identity.getIdentityId()))
                .schoolId(parseLongOrNull(identity.getSchoolId()))
                .schoolName(identity.getSchoolName())
                .personType(identity.getPersonType())
                .dutyCode(identity.getDutyCode())
                .dutyName(dutyNameOf(identity.getDutyCode()))
                .identityDisplayName(identity.getUserName())
                .current(current)
                .managementAvailable(managementAvailable)
                .managementUnavailableReason(managementUnavailableReason)
                .build();
    }

    /** 登录时下发的一次性身份选择票据：保存并校验 userId/tenantId/clientType/forceChangePassword。 */
    private record IdentityTicket(Long userId, Long tenantId, String clientType, boolean forceChangePassword) {
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isSchoolAdmin(String dutyCode) {
        return dutyCode != null && "SCHOOL_ADMIN".equalsIgnoreCase(dutyCode.trim());
    }

    /**
     * 校内管理员身份：教育人员类型为教师（TEACHER）且岗位为 SCHOOL_ADMIN。
     *
     * <p>与「PC 管理端可用」四要素口径一致；学生即使误配 SCHOOL_ADMIN 岗位也不得进管理端。
     */
    private boolean isTeacherSchoolAdmin(ClassroomIdentityVO identity) {
        return identity != null
                && "TEACHER".equalsIgnoreCase(blankToEmpty(identity.getPersonType()))
                && isSchoolAdmin(identity.getDutyCode());
    }

    private String dutyNameOf(String dutyCode) {
        if (dutyCode == null || dutyCode.isBlank()) {
            return "";
        }
        return DUTY_NAME_MAP.getOrDefault(dutyCode.trim().toUpperCase(Locale.ROOT), "");
    }

    /** 过滤掉 teacher/student 相关角色 key，仅保留管理端角色。 */
    private Set<String> filterManagementRoleKeys(Set<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return Set.of();
        }
        return roleKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .filter(key -> !isTeacherOrStudentKey(key))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String blankToEmpty(String value) {
        return value != null ? value : "";
    }

    private LoginUser requireLoginUser() {
        LoginUser current = SecurityContextHolder.getLoginUser();
        if (current == null || current.getUserId() == null || XuStrUtil.isBlank(current.getUsername())) {
            throw new UnauthorizedException("鐧诲綍宸茶繃鏈燂紝璇烽噸鏂扮櫥褰?");
        }
        return current;
    }

    private UserVO requireSwitchTargetUser(LoginUser current, Long tenantId) {
        String username = current.getUsername();
        boolean hasAccount = loadUserTenantAccounts(username).stream()
                .anyMatch(account -> tenantId.equals(toLong(account.get("tenantId")))
                        && Integer.valueOf(0).equals(toInteger(account.get("status"))));
        if (!hasAccount) {
            throw new BusinessException("褰撳墠鐢ㄦ埛鍦ㄧ洰鏍囩鎴蜂笅鏃犲彲鐢ㄨ处鍙?");
        }

        if (!tenantId.equals(current.getTenantId())) {
            try {
                R<Boolean> validResult = tenantServiceClient.checkTenantValid(tenantId);
                if (validResult == null || validResult.getData() == null || !validResult.getData()) {
            throw new BusinessException("绉熸埛宸插仠鐢ㄦ垨宸茶繃鏈?");
                }

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("切换租户时租户服务不可用: targetTenantId={}", tenantId, e);
                throw new BusinessException("租户服务不可用，暂时无法切换租户");
            }
        }

        R<UserVO> userResult = systemServiceClient.getUserByUsername(username, tenantId);
        if (userResult == null || userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            throw new BusinessException("鐩爣绉熸埛鐢ㄦ埛涓嶅瓨鍦?");
        }
        UserVO user = userResult.getData();
        if (user.getStatus() == null || user.getStatus() != 0) {
            throw new BusinessException("鐩爣绉熸埛鐢ㄦ埛宸插仠鐢?");
        }
        return user;
    }

    private List<Map<String, Object>> loadUserTenantAccounts(String username) {
        R<List<Map<String, Object>>> result = systemServiceClient.getUserTenants(username);
        if (result == null || result.getCode() != Constants.SUCCESS || result.getData() == null) {
            return List.of();
        }
        return result.getData();
    }

    private Map<Long, TenantVO> loadValidTenantMap() {
        Map<Long, TenantVO> tenants = new HashMap<>();
        try {
            R<List<TenantVO>> result = tenantServiceClient.listAllValidTenants();
            if (result != null && result.getCode() == Constants.SUCCESS && result.getData() != null) {
                for (TenantVO tenant : result.getData()) {
                    if (tenant.getTenantId() != null) {
                        tenants.put(tenant.getTenantId(), tenant);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("鏌ヨ鏈夋晥绉熸埛鍒楄〃澶辫触锛屽皢鎸夌鎴稩D鍥為€€鏌ヨ", e);
        }
        return tenants;
    }

    private TenantVO loadTenantById(Long tenantId) {
        try {
            R<TenantVO> result = tenantServiceClient.getTenantById(tenantId);
            if (result != null && result.getCode() == Constants.SUCCESS) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("鏌ヨ绉熸埛淇℃伅澶辫触: tenantId={}", tenantId, e);
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && XuStrUtil.isNotBlank(text)) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && XuStrUtil.isNotBlank(text)) {
            return Integer.parseInt(text);
        }
        return null;
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

    /**
     * 身份化 LoginUser：写入身份字段，并按身份岗位收敛管理端角色与权限。
     *
     * <p>dutyCode=SCHOOL_ADMIN 时写入账号「非 teacher/student」管理角色的 ID/Key 与权限并集；
     * 其余（TEACHER/STUDENT/未知）roleIds/roleKeys/permissions 全部置空，管理端不可用，
     * 且不残留账号管理角色 ID。identityDisplayName 始终写姓名。
     */
    private LoginUser buildIdentityLoginUser(UserVO user, ClientType clientType, Set<String> permissions,
                                             Set<Long> dataScopeDeptIds, ClassroomIdentityVO identity,
                                             ManagementRoles managementRoles) {
        boolean schoolAdmin = isTeacherSchoolAdmin(identity);
        Set<Long> roleIds = schoolAdmin ? managementRoles.roleIds() : Set.of();
        Set<String> roleKeys = schoolAdmin ? managementRoles.roleKeys() : Set.of();
        Set<String> effectivePermissions = schoolAdmin && permissions != null ? permissions : Set.of();
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
                .roleIds(roleIds)
                .roleKeys(roleKeys)
                .permissions(effectivePermissions)
                .deptIds(dataScopeDeptIds)
                .identityScoped(true)
                .identityId(parseLongOrNull(identity.getIdentityId()))
                .schoolId(parseLongOrNull(identity.getSchoolId()))
                .schoolName(identity.getSchoolName())
                .personType(identity.getPersonType())
                .dutyCode(identity.getDutyCode())
                .dutyName(dutyNameOf(identity.getDutyCode()))
                .identityDisplayName(identity.getUserName())
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
                redisTemplate.delete(CacheConstants.ONLINE_KEY + oldToken);
                log.info("用户[{}]PC 端被踢出，新设备登录", userId);
            }
        }
    }

    private void markOnline(String accessToken) {
        redisTemplate.opsForValue().set(CacheConstants.ONLINE_KEY + accessToken, accessToken, ONLINE_WINDOW);
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
