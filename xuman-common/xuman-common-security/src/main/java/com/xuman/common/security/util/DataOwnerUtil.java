package com.xuman.common.security.util;

import com.xuman.common.core.exception.ForbiddenException;
import com.xuman.common.security.context.SecurityContextHolder;
import com.xuman.common.security.domain.LoginUser;

import java.util.Objects;
import java.util.Set;

/**
 * 数据归属校验工具（防越权）
 */
public final class DataOwnerUtil {

    private DataOwnerUtil() {}

    /**
     * 校验数据是否属于当前用户
     */
    public static void checkOwner(Long ownerId) {
        Long currentUserId = SecurityContextHolder.getUserId();
        if (!Objects.equals(ownerId, currentUserId)) {
            throw new ForbiddenException("无权操作此数据");
        }
    }

    /**
     * 校验数据是否属于当前租户
     */
    public static void checkTenant(Long dataTenantId) {
        Long currentTenantId = SecurityContextHolder.getTenantId();
        if (!Objects.equals(dataTenantId, currentTenantId)) {
            throw new ForbiddenException("无权操作其他租户数据");
        }
    }

    /**
     * 校验用户是否有权操作指定部门数据
     */
    public static void checkDeptPermission(Long deptId) {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            throw new ForbiddenException("未登录");
        }
        if (user.isAdmin()) {
            return;
        }

        Set<Long> allowedDepts = user.getDeptIds();
        if (allowedDepts == null || !allowedDepts.contains(deptId)) {
            throw new ForbiddenException("无权操作此部门数据");
        }
    }

    /**
     * 校验是否可以操作指定用户
     */
    public static void checkUserPermission(Long targetUserId, boolean allowSelf) {
        Long currentUserId = SecurityContextHolder.getUserId();
        if (Objects.equals(targetUserId, currentUserId)) {
            if (!allowSelf) {
                throw new ForbiddenException("不能操作自己");
            }
            return;
        }

        // 非管理员不能操作其他用户（需根据业务调整）
        if (!SecurityContextHolder.isAdmin()) {
            throw new ForbiddenException("无权操作此用户");
        }
    }

    /**
     * 校验是否可以分配指定角色
     */
    public static void checkRolePermission(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            throw new ForbiddenException("未登录");
        }
        if (user.isAdmin()) {
            return;
        }

        Set<Long> allowedRoles = user.getRoleIds();
        for (Long roleId : roleIds) {
            if (allowedRoles == null || !allowedRoles.contains(roleId)) {
                throw new ForbiddenException("无权分配角色: " + roleId);
            }
        }
    }
}
