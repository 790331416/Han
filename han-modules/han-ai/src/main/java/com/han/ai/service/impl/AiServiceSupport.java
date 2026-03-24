package com.han.ai.service.impl;

import com.han.common.security.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Shared support methods for AI services.
 */
abstract class AiServiceSupport {

    protected static final String STATUS_ENABLED = "0";
    protected static final String STATUS_DISABLED = "1";

    protected int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    protected int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    protected String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    protected String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    protected Long currentTenantId() {
        if (SecurityContextHolder.isAdmin()) {
            return null;
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        return tenantId != null && tenantId > 0 ? tenantId : null;
    }

    protected Long resolveTenantIdForWrite() {
        Long tenantId = SecurityContextHolder.getTenantId();
        return tenantId != null && tenantId > 0 ? tenantId : 0L;
    }

    protected String resolveOperator() {
        String username = SecurityContextHolder.getUsername();
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        Long userId = SecurityContextHolder.getUserId();
        return userId != null ? String.valueOf(userId) : "system";
    }

    protected LocalDateTime now() {
        return LocalDateTime.now();
    }
}
