package com.xuman.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据权限类型枚举
 */
@Getter
@AllArgsConstructor
public enum DataScopeType {

    ALL("1", "全部数据"),
    CUSTOM("2", "自定义数据"),
    DEPT("3", "本部门数据"),
    DEPT_AND_CHILD("4", "本部门及以下"),
    SELF("5", "仅本人数据");

    private final String code;
    private final String desc;

    public static DataScopeType fromCode(String code) {
        for (DataScopeType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return SELF;
    }
}
