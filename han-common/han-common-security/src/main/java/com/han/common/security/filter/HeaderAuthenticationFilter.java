package com.han.common.security.filter;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.HanJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.common.security.interceptor.InnerAuthSignatureVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 还原服务端登录态并填充到 SecurityContextHolder
 *
 * <p><b>身份只有两个合法来源，优先级从高到低：</b>
 * <ol>
 *   <li><b>Bearer Token 对应的 Redis 会话</b>：外部流量（经网关）的唯一来源。以
 *       {@code Authorization: Bearer <token>} 取 {@code CacheConstants.TOKEN_KEY + token}，
 *       解析出完整 {@link LoginUser}（含 userId、tenantId 与权限集合）。</li>
 *   <li><b>经内部签名绑定的身份头</b>：服务间 {@code /inner/**} 调用没有终端用户 Token，
 *       需要由调用方透传发起人身份，否则下游拿不到租户上下文。仅当
 *       {@link InnerAuthSignatureVerifier#isIdentityTrusted} 通过时才采信。</li>
 * </ol>
 *
 * <p><b>不信任裸请求头。</b>历史实现在没有 Token 时无条件用 {@code X-User-Id} 构造登录态，
 * 配合 {@code LoginUser.isAdmin()} 的 {@code userId == 1L} 判定，任何能直连服务端口的人加一个
 * 请求头即为超级管理员；同时 {@code X-Tenant-Id} 会无条件覆盖已解析出的租户上下文，构成水平越权。
 * 现在这两条路径都要求内部签名覆盖身份头，签名不通过一律不建立登录态。
 *
 * <p>网关 {@code AuthFilter} 会在转发前剥离客户端自带的身份头与全部 {@code X-Inner-*} 头，
 * 因此外部流量无法伪造成内部调用。
 *
 * <p>租户切换请走 {@code POST /auth/switchTenant}：该接口会校验账号在目标租户下确实存在并重新签发
 * Token，而不是靠请求头改写租户上下文。
 */
@Slf4j
@Component
@Order(-100)
@RequiredArgsConstructor
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String INNER_PATH_PREFIX = "/inner/";

    private final StringRedisTemplate redisTemplate;
    private final InnerAuthSignatureVerifier signatureVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            LoginUser loginUser = loadFromSession(request);
            if (loginUser == null) {
                loginUser = loadFromSignedInnerCall(request);
            }

            if (loginUser != null) {
                SecurityContextHolder.setLoginUser(loginUser);
            }

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clear();
        }
    }

    /**
     * 从 Bearer Token 对应的服务端会话还原完整登录态。
     *
     * <p>会话里的 tenantId 是权威值，不接受任何请求头覆盖。
     */
    private LoginUser loadFromSession(HttpServletRequest request) {
        String authorization = request.getHeader(Constants.AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(Constants.TOKEN_PREFIX)) {
            return null;
        }

        String token = authorization.substring(Constants.TOKEN_PREFIX.length());
        String userJson = redisTemplate.opsForValue().get(CacheConstants.TOKEN_KEY + token);
        if (userJson == null || userJson.isBlank()) {
            return null;
        }
        return HanJsonUtil.parseObject(userJson, LoginUser.class);
    }

    /**
     * 从已被内部签名绑定的身份头构建最小登录态，仅用于 {@code /inner/**} 服务间调用。
     *
     * <p>只有权限集合为空的最小身份：内部接口靠 {@code @InnerAuth} 鉴权，不依赖权限点，
     * 这里携带身份是为了让下游拿到 userId 与 tenantId（租户隔离与审计字段需要）。
     */
    private LoginUser loadFromSignedInnerCall(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith(INNER_PATH_PREFIX)) {
            return null;
        }

        String userIdHeader = request.getHeader(Constants.USER_ID_HEADER);
        String tenantIdHeader = request.getHeader(Constants.TENANT_ID_HEADER);
        if (isBlank(userIdHeader) && isBlank(tenantIdHeader)) {
            return null;
        }

        if (!signatureVerifier.isIdentityTrusted(request)) {
            // 不静默丢弃：调用方以为透传了身份，下游却拿不到，会退化成无租户上下文的查询
            log.warn("内部调用携带了未被签名绑定的身份头，已忽略: path={}, signVersion={}",
                    request.getRequestURI(), request.getHeader(InnerAuthSignatureVerifier.SIGN_VERSION_HEADER));
            return null;
        }

        LoginUser loginUser = LoginUser.builder()
                .userId(parseLong(userIdHeader))
                .username(request.getHeader(Constants.USERNAME_HEADER))
                .tenantId(parseLong(tenantIdHeader))
                .build();

        if (loginUser.getUserId() == null && loginUser.getTenantId() == null) {
            return null;
        }
        return loginUser;
    }

    private Long parseLong(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("内部调用身份头数值非法，已忽略: value={}", value);
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
