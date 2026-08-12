package com.han.common.tenant.exception;

import com.han.common.core.exception.BusinessException;

/**
 * 缺少租户上下文异常。
 *
 * <p>仅在 {@code tenant.missing-context=REJECT} 时抛出。使用独立的错误码，
 * 便于运维按错误码告警并区分「真的没数据」与「租户上下文丢失」。</p>
 */
public class MissingTenantContextException extends BusinessException {

    /** 错误码，便于日志与告警按码聚合 */
    public static final String CODE = "TENANT_CONTEXT_MISSING";

    public MissingTenantContextException(String message) {
        super(CODE, message);
    }
}
