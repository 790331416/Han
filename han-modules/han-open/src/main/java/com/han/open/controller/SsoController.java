package com.han.open.controller;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.core.util.XuStrUtil;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.service.IOpenAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * SSO 单点登录控制器。
 */
@RestController
@RequestMapping({"/sso", "/open/sso"})
@RequiredArgsConstructor
public class SsoController {

    private static final String SSO_TICKET_KEY_PREFIX = CacheConstants.CACHE_PREFIX + "sso:ticket:";
    private static final Duration SSO_TICKET_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final IOpenAppService openAppService;

    /**
     * SSO 登录入口。
     */
    @GetMapping("/login")
    @PermissionExempt("SSO 登录入口，由登录态和业务逻辑控制")
    public R<String> ssoLogin(@RequestParam("client_id") String clientId,
                              @RequestParam("redirect_uri") String redirectUri,
                              @RequestParam(value = "state", required = false) String state) {
        if (!SecurityContextHolder.isLogin()) {
            return R.fail(401, "请先登录");
        }
        if (!openAppService.validateRedirectUri(clientId, redirectUri)) {
            return R.fail("redirect_uri 不合法");
        }

        Long userId = SecurityContextHolder.getUserId();
        String ticket = generateSsoTicket(userId, clientId, redirectUri, state);
        return R.ok(ticket);
    }

    /**
     * SSO 登出入口。
     */
    @GetMapping("/logout")
    @PermissionExempt("SSO 登出入口，由登录态和业务逻辑控制")
    public R<Void> ssoLogout(@RequestParam(value = "redirect_uri", required = false) String redirectUri,
                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        clearLoginToken(authorization);
        return R.ok();
    }

    /**
     * 校验 SSO Ticket，供第三方服务端调用。
     */
    @PostMapping("/validate")
    @PermissionExempt("SSO Ticket 校验公开端点，由客户端凭证和业务参数校验控制")
    public R<Object> validateTicket(@RequestParam String ticket,
                                    @RequestParam("client_id") String clientId,
                                    @RequestParam("client_secret") String clientSecret) {
        if (!openAppService.validateClient(clientId, clientSecret)) {
            return R.fail("client_id 或 client_secret 无效");
        }

        String key = SSO_TICKET_KEY_PREFIX + ticket;
        Map<Object, Object> payload = redisTemplate.opsForHash().entries(key);
        if (payload == null || payload.isEmpty()) {
            return R.fail("SSO Ticket 无效或已过期");
        }
        if (!clientId.equals(String.valueOf(payload.get("clientId")))) {
            return R.fail("SSO Ticket 不属于当前客户端");
        }

        redisTemplate.delete(key);
        return R.ok(Map.of(
                "valid", true,
                "userId", Long.parseLong(String.valueOf(payload.get("userId"))),
                "clientId", clientId,
                "redirectUri", String.valueOf(payload.get("redirectUri")),
                "state", String.valueOf(payload.getOrDefault("state", ""))
        ));
    }

    /**
     * 检查当前用户是否已登录。
     */
    @GetMapping("/check")
    @PermissionExempt("SSO 登录态检查端点，由登录态和业务逻辑控制")
    public R<Boolean> checkLogin() {
        return R.ok(SecurityContextHolder.isLogin());
    }

    private String generateSsoTicket(Long userId, String clientId, String redirectUri, String state) {
        String ticket = "ST-" + UUID.randomUUID().toString().replace("-", "");
        String key = SSO_TICKET_KEY_PREFIX + ticket;
        redisTemplate.opsForHash().putAll(key, Map.of(
                "userId", String.valueOf(userId),
                "clientId", clientId,
                "redirectUri", redirectUri,
                "state", state != null ? state : ""
        ));
        redisTemplate.expire(key, SSO_TICKET_TTL);
        return ticket;
    }

    private void clearLoginToken(String authorization) {
        if (XuStrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return;
        }
        String token = authorization.substring(7);
        String tokenKey = CacheConstants.TOKEN_KEY + token;
        String userJson = redisTemplate.opsForValue().get(tokenKey);
        if (XuStrUtil.isNotBlank(userJson)) {
            LoginUser loginUser = XuJsonUtil.parseObject(userJson, LoginUser.class);
            if (loginUser != null && loginUser.getUserId() != null && loginUser.getClientType() != null) {
                redisTemplate.delete(CacheConstants.LOGIN_USER_KEY + loginUser.getUserId() + ":" + loginUser.getClientType().getCode());
            }
        }
        redisTemplate.delete(tokenKey);
    }
}
