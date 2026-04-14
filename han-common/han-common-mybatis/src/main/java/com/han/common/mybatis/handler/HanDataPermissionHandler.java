package com.han.common.mybatis.handler;

import com.han.common.core.context.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 数据权限处理器
 */
@Slf4j
@RequiredArgsConstructor
public class HanDataPermissionHandler {

    private final SecurityContext securityContext;

    /**
     * 获取当前用户的数据权限部门 ID 列表
     *
     * <p>返回 null 表示不限制；返回空集合表示仅本人模式。
     */
    public Set<Long> getDataScopeDeptIds() {
        if (!securityContext.isLogin() || securityContext.isAdmin()) {
            return null;
        }

        Set<Long> deptIds = securityContext.getDataScopeDeptIds();
        log.debug("数据权限过滤: userId={}, deptId={}, deptIds={}",
                securityContext.getUserId(),
                securityContext.getDeptId(),
                deptIds);
        return deptIds;
    }

    /**
     * 判断当前用户是否需要数据权限过滤
     */
    public boolean needDataScope() {
        return securityContext.isLogin() && !securityContext.isAdmin();
    }
}
