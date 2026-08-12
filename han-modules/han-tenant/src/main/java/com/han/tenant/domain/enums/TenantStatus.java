package com.han.tenant.domain.enums;

import com.han.common.core.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 租户状态。
 * <p>
 * 状态取值原先以字面量 0 / 1 散落在服务实现与定时任务里，且 {@code updateStatus} 不校验入参，
 * 传入 2 会让租户进入既不「有效」也不出现在有效列表里的未定义状态，界面上无法恢复。
 */
@Getter
@AllArgsConstructor
public enum TenantStatus {

    /** 正常 */
    NORMAL(0, "正常"),

    /** 停用 */
    DISABLED(1, "停用");

    private final int code;
    private final String desc;

    /**
     * 按状态码解析，未知状态返回 null。
     */
    public static TenantStatus resolve(Integer code) {
        if (code == null) {
            return null;
        }
        for (TenantStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 按状态码解析，未知状态直接拒绝。
     */
    public static TenantStatus require(Integer code) {
        TenantStatus status = resolve(code);
        if (status == null) {
            throw new BusinessException("租户状态非法，只允许 0（正常）或 1（停用）");
        }
        return status;
    }
}
