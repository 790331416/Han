package com.han.auth.service;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.SocialBindingVO;
import com.han.api.system.domain.UserVO;
import com.han.api.tenant.TenantServiceClient;
import com.han.api.tenant.domain.TenantVO;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.SocialUser;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanSecureUtil;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.util.XuIdUtil;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.core.util.XuStrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社交登录编排层。
 *
 * <p>统一编排各 {@link SocialOAuthProvider}（GitHub / 微信）：
 * <ul>
 *   <li>state 防 CSRF：授权 URL 生成时下发一次性 state（Redis 5 分钟），回调必须原样带回；</li>
 *   <li>绑定票据 ticket：扫码成功但未绑定（或多租户需选择）时，第三方身份信息不回传前端，
 *       以一次性 ticket（Redis 10 分钟）暂存服务端，绑定 / 选租户凭 ticket 完成；</li>
 *   <li>登录签发：命中绑定后复用 {@link IAuthService#issueLoginForUser} 公共出口
 *       （账号状态、租户有效性、权限装载、互踢、登录日志与密码登录一致）。</li>
 * </ul>
 */
@Slf4j
@Service
public class SocialLoginService {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);
    /** 单张 ticket 允许的绑定密码错误次数（防止拿 ticket 暴力试密码） */
    private static final int MAX_BIND_ATTEMPTS = 5;

    private final Map<String, SocialOAuthProvider> providers;
    private final LoginMethodSettingService loginMethodSettingService;
    private final SystemServiceClient systemServiceClient;
    private final TenantServiceClient tenantServiceClient;
    private final IAuthService authService;
    private final SecurityProperties securityProperties;
    private final StringRedisTemplate redisTemplate;
    private final LoginAttemptGuard loginAttemptGuard;

    public SocialLoginService(List<SocialOAuthProvider> providerList,
                              LoginMethodSettingService loginMethodSettingService,
                              SystemServiceClient systemServiceClient,
                              TenantServiceClient tenantServiceClient,
                              IAuthService authService,
                              SecurityProperties securityProperties,
                              StringRedisTemplate redisTemplate,
                              LoginAttemptGuard loginAttemptGuard) {
        this.providers = providerList.stream()
                .collect(Collectors.toUnmodifiableMap(SocialOAuthProvider::provider, Function.identity()));
        this.loginMethodSettingService = loginMethodSettingService;
        this.systemServiceClient = systemServiceClient;
        this.tenantServiceClient = tenantServiceClient;
        this.authService = authService;
        this.securityProperties = securityProperties;
        this.redisTemplate = redisTemplate;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    /**
     * 各提供方可用状态（已配置凭据且开关开启才为 true，供登录页决定入口显隐）
     */
    public Map<String, Boolean> listProviders() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        providers.forEach((name, provider) ->
                result.put(name, provider.isConfigured() && loginMethodSettingService.isProviderEnabled(name)));
        return result;
    }

    /**
     * 生成第三方授权 URL（含一次性 state）
     */
    public String buildAuthorizeUrl(String providerName, String redirectUri) {
        if (XuStrUtil.isBlank(redirectUri)) {
            throw new BusinessException("redirectUri 不能为空");
        }
        SocialOAuthProvider provider = requireEnabledProvider(providerName);
        String state = XuIdUtil.uuid();
        redisTemplate.opsForValue().set(CacheConstants.SOCIAL_STATE_KEY + state, providerName, STATE_TTL);
        return provider.buildAuthorizeUrl(redirectUri, state);
    }

    /**
     * OAuth 回调：校验 state → 换取第三方用户 → 按绑定情况登录或下发 ticket。
     *
     * <p>返回三种形态：
     * <ul>
     *   <li>已绑定单账号：{@code {bound:true, login:LoginVO}}</li>
     *   <li>已绑定多租户账号：{@code {bound:true, multiTenant:true, ticket, tenants:[{tenantId,tenantName}]}}，
     *       前端选租户后调 {@link #loginByTicket}；</li>
     *   <li>未绑定：{@code {bound:false, ticket, provider, nickname, avatar}}，
     *       前端引导账号密码绑定后调 {@link #bindAndLogin}。</li>
     * </ul>
     */
    public Map<String, Object> handleCallback(String providerName, String code, String state) {
        if (XuStrUtil.isBlank(code)) {
            throw new BusinessException("授权码不能为空");
        }
        consumeState(providerName, state);

        SocialOAuthProvider provider = requireEnabledProvider(providerName);
        SocialUser socialUser = provider.fetchUser(code);

        List<SocialBindingVO> bindings = loadBindings(providerName, socialUser.getOpenId());

        Map<String, Object> result = new LinkedHashMap<>();
        if (bindings.isEmpty()) {
            result.put("bound", false);
            result.put("ticket", createTicket(socialUser, List.of()));
            result.put("provider", providerName);
            result.put("nickname", nvl(socialUser.getNickname()));
            result.put("avatar", nvl(socialUser.getAvatar()));
            return result;
        }

        if (bindings.size() == 1) {
            result.put("bound", true);
            result.put("login", loginByBinding(bindings.get(0)));
            return result;
        }

        // 同一第三方身份在多个租户各有绑定，需要用户选择
        result.put("bound", true);
        result.put("multiTenant", true);
        result.put("ticket", createTicket(socialUser, bindings.stream()
                .map(b -> new SocialTicket.Binding(b.getUserId(), b.getTenantId()))
                .toList()));
        result.put("tenants", bindings.stream().map(b -> {
            Map<String, Object> tenant = new LinkedHashMap<String, Object>();
            tenant.put("tenantId", b.getTenantId());
            tenant.put("tenantName", resolveTenantName(b.getTenantId()));
            return tenant;
        }).toList());
        return result;
    }

    /**
     * 未绑定：账号密码核验后绑定第三方身份并直接登录
     */
    public LoginVO bindAndLogin(String ticketId, String username, String password, Long tenantId) {
        if (XuStrUtil.isBlank(username) || XuStrUtil.isBlank(password)) {
            throw new BusinessException("用户名和密码不能为空");
        }
        SocialTicket ticket = requireTicket(ticketId);

        R<UserVO> userResult = tenantId != null
                ? systemServiceClient.getUserByUsername(username, tenantId)
                : systemServiceClient.getUserByUsername(username);
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            recordBindFail(ticketId);
            throw new BusinessException("用户名或密码错误");
        }
        UserVO user = userResult.getData();

        String rawPassword = password;
        if (securityProperties.isEnabled()) {
            try {
                rawPassword = HanSecureUtil.rsaDecrypt(rawPassword, securityProperties.getPrivateKey());
            } catch (Exception e) {
                log.warn("社交绑定时密码解密失败: username={}", username);
                throw new BusinessException("密码解密失败，请重试");
            }
        }
        if (!PasswordUtil.matches(rawPassword, user.getPassword())) {
            recordBindFail(ticketId);
            throw new BusinessException("用户名或密码错误");
        }

        // 核验通过才消费 ticket；绑定关系写入 han-system（accessToken 不落库）
        deleteTicket(ticketId);
        R<Void> bindResult = systemServiceClient.bindSocialUser(user.getUserId(), user.getTenantId(),
                ticket.provider(), ticket.openId(), null, ticket.nickname(), ticket.avatar());
        if (bindResult.getCode() != Constants.SUCCESS) {
            throw new BusinessException(XuStrUtil.isNotBlank(bindResult.getMsg()) ? bindResult.getMsg() : "绑定失败");
        }
        log.info("用户[{}]绑定社交账号后登录: provider={}", username, ticket.provider());
        return authService.issueLoginForUser(user, ClientType.PC, false);
    }

    /**
     * 多租户绑定：按用户选择的租户签发登录
     */
    public LoginVO loginByTicket(String ticketId, Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException("租户ID不能为空");
        }
        SocialTicket ticket = requireTicket(ticketId);
        SocialTicket.Binding binding = ticket.bindings().stream()
                .filter(b -> tenantId.equals(b.tenantId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("该微信账号未绑定所选租户下的用户"));
        deleteTicket(ticketId);

        R<UserVO> userResult = systemServiceClient.getUserById(binding.userId());
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            throw new BusinessException("绑定的用户不存在，请联系管理员");
        }
        return authService.issueLoginForUser(userResult.getData(), ClientType.PC, false);
    }

    // ==================== 内部实现 ====================

    private SocialOAuthProvider requireEnabledProvider(String providerName) {
        SocialOAuthProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new BusinessException("不支持的登录方式: " + providerName);
        }
        if (!provider.isConfigured()) {
            throw new BusinessException("该登录方式未配置");
        }
        if (!loginMethodSettingService.isProviderEnabled(providerName)) {
            throw new BusinessException("该登录方式未开启");
        }
        return provider;
    }

    private void consumeState(String providerName, String state) {
        if (XuStrUtil.isBlank(state)) {
            throw new BusinessException("授权状态无效，请重新发起登录");
        }
        String key = CacheConstants.SOCIAL_STATE_KEY + state;
        String stored = redisTemplate.opsForValue().getAndDelete(key);
        if (!providerName.equals(stored)) {
            throw new BusinessException("授权状态已失效，请重新发起登录");
        }
    }

    private List<SocialBindingVO> loadBindings(String providerName, String openId) {
        R<List<SocialBindingVO>> result = systemServiceClient.listSocialBindings(providerName, openId);
        if (result.getCode() != Constants.SUCCESS) {
            throw new BusinessException("查询绑定关系失败，请稍后重试");
        }
        return result.getData() != null ? result.getData() : List.of();
    }

    private LoginVO loginByBinding(SocialBindingVO binding) {
        R<UserVO> userResult = systemServiceClient.getUserById(binding.getUserId());
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            throw new BusinessException("绑定的用户不存在，请联系管理员");
        }
        return authService.issueLoginForUser(userResult.getData(), ClientType.PC, false);
    }

    private String createTicket(SocialUser socialUser, List<SocialTicket.Binding> bindings) {
        String ticketId = XuIdUtil.uuid();
        SocialTicket ticket = new SocialTicket(socialUser.getProvider(), socialUser.getOpenId(),
                socialUser.getRawOpenId(), socialUser.getNickname(), socialUser.getAvatar(),
                socialUser.getEmail(), bindings);
        redisTemplate.opsForValue().set(CacheConstants.SOCIAL_TICKET_KEY + ticketId,
                XuJsonUtil.toJsonString(ticket), TICKET_TTL);
        return ticketId;
    }

    private SocialTicket requireTicket(String ticketId) {
        if (XuStrUtil.isBlank(ticketId)) {
            throw new BusinessException("绑定凭证不能为空");
        }
        String json = redisTemplate.opsForValue().get(CacheConstants.SOCIAL_TICKET_KEY + ticketId);
        if (XuStrUtil.isBlank(json)) {
            throw new BusinessException("绑定凭证已过期，请重新扫码");
        }
        return XuJsonUtil.parseObject(json, SocialTicket.class);
    }

    /**
     * ticket 维度的绑定失败计数。
     * <p>自增与设置 TTL 走 {@link LoginAttemptGuard#incrementWithTtl} 的原子脚本，
     * 避免两次往返之间中断留下无 TTL 的永生计数 key。
     */
    private void recordBindFail(String ticketId) {
        String failKey = CacheConstants.SOCIAL_TICKET_KEY + ticketId + ":fail";
        long count = loginAttemptGuard.incrementWithTtl(failKey, TICKET_TTL);
        if (count >= MAX_BIND_ATTEMPTS) {
            deleteTicket(ticketId);
            throw new BusinessException("密码错误次数过多，请重新扫码后再试");
        }
    }

    private void deleteTicket(String ticketId) {
        redisTemplate.delete(CacheConstants.SOCIAL_TICKET_KEY + ticketId);
        redisTemplate.delete(CacheConstants.SOCIAL_TICKET_KEY + ticketId + ":fail");
    }

    private String resolveTenantName(Long tenantId) {
        if (tenantId == null) {
            return "";
        }
        try {
            R<TenantVO> result = tenantServiceClient.getTenantById(tenantId);
            if (result != null && result.getCode() == Constants.SUCCESS && result.getData() != null
                    && XuStrUtil.isNotBlank(result.getData().getTenantName())) {
                return result.getData().getTenantName();
            }
        } catch (Exception e) {
            log.warn("查询租户名称失败: tenantId={}", tenantId, e);
        }
        return "tenant-" + tenantId;
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    /**
     * 服务端暂存的一次性社交登录票据（不回传第三方 openId 到前端）
     */
    record SocialTicket(String provider, String openId, String rawOpenId,
                        String nickname, String avatar, String email,
                        List<Binding> bindings) {
        record Binding(Long userId, Long tenantId) {
        }
    }
}
