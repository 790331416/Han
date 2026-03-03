package com.han.common.security.filter;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.util.HanJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Gateway 传递的请求头中提取用户信息，填充到 SecurityContextHolder
 *
 * <p>Gateway AuthFilter 验证 Token 后，会在请求头中设置：
 * <ul>
 *   <li>X-User-Id: 用户ID</li>
 *   <li>X-User-Name: 用户名</li>
 *   <li>X-Tenant-Id: 租户ID</li>
 * </ul>
 */
@Component
@Order(-100)
@RequiredArgsConstructor
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            LoginUser loginUser = null;

            // 优先从 Bearer Token 加载完整用户信息（含权限）
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7);
                String tokenKey = CacheConstants.TOKEN_KEY + token;
                String userJson = redisTemplate.opsForValue().get(tokenKey);
                if (userJson != null && !userJson.isBlank()) {
                    loginUser = HanJsonUtil.parseObject(userJson, LoginUser.class);
                }
            }

            // 回退：从请求头构建最小用户信息（内部服务调用场景）
            if (loginUser == null) {
                String userIdStr = request.getHeader("X-User-Id");
                if (userIdStr != null && !userIdStr.isBlank()) {
                    loginUser = LoginUser.builder()
                            .userId(Long.parseLong(userIdStr))
                            .username(request.getHeader("X-User-Name"))
                            .build();
                }
            }

            // 允许请求头覆盖租户ID（租户切换场景）
            if (loginUser != null) {
                String tenantIdStr = request.getHeader("X-Tenant-Id");
                if (tenantIdStr != null && !tenantIdStr.isBlank()) {
                    loginUser.setTenantId(Long.parseLong(tenantIdStr));
                }
                SecurityContextHolder.setLoginUser(loginUser);
            }

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clear();
        }
    }
}
