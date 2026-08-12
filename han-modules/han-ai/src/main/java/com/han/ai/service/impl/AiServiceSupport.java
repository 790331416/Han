package com.han.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.function.Consumer;

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

    /**
     * 部分更新字段合并：仅当请求体带上该字段（非 null）时才覆盖库内现值。
     *
     * <p>管理端编辑接口直接以实体接收请求体，请求体里缺省的字段反序列化后是 null。
     * 若无条件回写，部分字段提交（如编排设计器只提交 workflowId + flowConfig）会把未提交字段
     * 一并清成 null，随后被保存校验拦成必填报错，或把可选字段静默重置成 normalize 默认值。
     *
     * <p>持久化语义不变：除显式声明
     * {@link com.baomidou.mybatisplus.annotation.FieldStrategy#ALWAYS} 的列外，
     * {@code updateById} 按默认 NOT_NULL 策略跳过 null 字段，本就不会把 null 落库。
     * 反过来，需要「清空即恢复默认」的 ALWAYS 列必须保留无条件回写，不能改用本方法，
     * 否则会丢掉显式置空能力。
     *
     * @param value  请求体中的字段值，null 表示本次未提交该字段
     * @param setter 目标实体的写入方法
     */
    protected <V> void copyIfPresent(V value, Consumer<V> setter) {
        if (value != null) {
            setter.accept(value);
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
