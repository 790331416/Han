package com.han.common.mybatis.handler;

import com.han.common.core.context.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 数据权限处理器（工具类，非 AOP）
 * <p>
 * 由 Service 层在查询前调用，根据当前用户角色的 dataScope
 * 返回数据范围部门ID列表，供 Mapper XML 的 SQL 条件使用。
 * <p>
 * 数据权限级别：
 * <ul>
 *   <li>1 - 全部数据（不限制）</li>
 *   <li>2 - 自定义部门（查 sys_role_dept）</li>
 *   <li>3 - 本部门数据</li>
 *   <li>4 - 本部门及下级</li>
 *   <li>5 - 仅本人</li>
 * </ul>
 * <p>
 * 使用方式：在 Service 查询前调用 {@link #getDataScopeDeptIds()} 获取部门ID列表，
 * 注入到 Query 对象的 deptIds 字段中，Mapper XML 通过
 * {@code <if test="query.deptIds != null">} 条件过滤。
 */
@Slf4j
@RequiredArgsConstructor
public class HanDataPermissionHandler {

    private final SecurityContext securityContext;

    /**
     * 获取当前用户的数据权限部门ID列表
     * <p>
     * 返回 null 表示不限制（全部数据或管理员）。
     * 返回空列表表示无权限（仅本人模式由 createBy 字段控制）。
     */
    public Set<Long> getDataScopeDeptIds() {
        if (!securityContext.isLogin() || securityContext.isAdmin()) {
            return null;
        }

        // LoginUser 中已有 deptIds 字段（数据权限部门ID列表），
        // 在用户登录时由 AuthServiceImpl 查询并缓存到 Redis。
        // 此处直接从 SecurityContext → LoginUser → deptIds 获取。
        log.debug("数据权限过滤: userId={}, deptId={}", securityContext.getUserId(), securityContext.getDeptId());

        // TODO: 从 LoginUser.getDeptIds() 获取（需通过 SecurityContextHolder 访问）
        // 当前返回 null 表示不限制，待 LoginUser.deptIds 在登录时填充后生效
        return null;
    }

    /**
     * 判断当前用户是否需要数据权限过滤
     */
    public boolean needDataScope() {
        return securityContext.isLogin() && !securityContext.isAdmin();
    }
}
