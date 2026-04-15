package com.han.common.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

/**
 * XSS 过滤器
 * <p>对所有请求参数进行 XSS 清洗，富文本字段通过 Jsoup 白名单过滤。
 */
@Slf4j
public class XssFilter implements Filter {

    /** 排除路径（如文件上传等不需要 XSS 过滤的接口） */
    private final List<String> excludes;

    public XssFilter(List<String> excludes) {
        this.excludes = excludes != null ? excludes : List.of();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        if (isExclude(req)) {
            chain.doFilter(request, response);
        } else {
            chain.doFilter(new XssHttpServletRequestWrapper(req), response);
        }
    }

    private boolean isExclude(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludes.stream().anyMatch(path::startsWith);
    }
}
