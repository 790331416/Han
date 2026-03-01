package com.han.common.core.context;

/**
 * 安全上下文接口（定义在 core 层，避免上层模块循环依赖）
 * <p>
 * 由 han-common-security 模块提供实现，han-common-mybatis 等模块通过此接口获取当前用户信息。
 */
public interface SecurityContext {

    /**
     * 获取当前用户ID
     */
    Long getUserId();

    /**
     * 获取当前租户ID
     */
    Long getTenantId();

    /**
     * 获取当前部门ID
     */
    Long getDeptId();

    /**
     * 获取当前用户昵称
     */
    String getNickname();

    /**
     * 是否已登录
     */
    boolean isLogin();

    /**
     * 是否为超级管理员
     */
    boolean isAdmin();
}
