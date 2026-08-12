package com.han.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Shared support methods for AI services.
 */
abstract class AiServiceSupport {

    protected static final String STATUS_ENABLED = "0";
    protected static final String STATUS_DISABLED = "1";

    /** 开场推荐问题条数上限（G1-10，agent 与 workflow 共用） */
    protected static final int SUGGESTED_QUESTION_LIMIT = 5;
    /** 单条推荐问题最大字符数 */
    protected static final int SUGGESTED_QUESTION_MAX_LENGTH = 200;

    private static final ObjectMapper SUPPORT_OBJECT_MAPPER = new ObjectMapper();

    /**
     * 校验开场推荐问题（G1-10）：允许空；非空必须是 JSON 字符串数组，
     * 最多 {@link #SUGGESTED_QUESTION_LIMIT} 条，单条非空白且不超长。
     */
    protected void validateSuggestedQuestions(String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        JsonNode node;
        try {
            node = SUPPORT_OBJECT_MAPPER.readTree(value);
        } catch (Exception ex) {
            throw new BusinessException("推荐问题必须是合法JSON数组");
        }
        if (node == null || !node.isArray()) {
            throw new BusinessException("推荐问题必须是JSON字符串数组");
        }
        if (node.size() > SUGGESTED_QUESTION_LIMIT) {
            throw new BusinessException("推荐问题最多 " + SUGGESTED_QUESTION_LIMIT + " 条");
        }
        for (JsonNode item : node) {
            if (item == null || !item.isTextual() || !StringUtils.hasText(item.asText())) {
                throw new BusinessException("推荐问题每一条必须是非空文本");
            }
            if (item.asText().trim().length() > SUGGESTED_QUESTION_MAX_LENGTH) {
                throw new BusinessException("单条推荐问题不能超过 " + SUGGESTED_QUESTION_MAX_LENGTH + " 字");
            }
        }
    }

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
