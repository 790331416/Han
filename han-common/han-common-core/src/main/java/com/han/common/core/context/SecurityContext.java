package com.han.common.core.context;

import java.util.Set;

/**
 * 安全上下文接口
 *
 * <p>定义在 core 层，避免上层模块循环依赖。
 */
public interface SecurityContext {

    /**
     * 获取当前用户 ID
     */
    Long getUserId();

    /**
     * 获取当前租户 ID
     */
    Long getTenantId();

    /**
     * 获取当前部门 ID
     */
    Long getDeptId();

    /**
     * 获取当前用户昵称
     */
    String getNickname();

    /**
     * 获取当前用户的数据权限部门 ID 列表
     */
    Set<Long> getDataScopeDeptIds();

    /**
     * 是否已登录
     */
    boolean isLogin();

    /**
     * 是否为超级管理员
     */
    boolean isAdmin();
}
