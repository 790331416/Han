package com.han.common.security.filter;

import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
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
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String userIdStr = request.getHeader("X-User-Id");
            if (userIdStr != null && !userIdStr.isBlank()) {
                LoginUser loginUser = LoginUser.builder()
                        .userId(Long.parseLong(userIdStr))
                        .username(request.getHeader("X-User-Name"))
                        .build();

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
