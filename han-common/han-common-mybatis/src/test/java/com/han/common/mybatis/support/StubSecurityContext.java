package com.han.common.mybatis.support;

import com.han.common.core.context.SecurityContext;

import java.util.Set;

/**
 * 测试用的可变 SecurityContext，避免每个用例都写一遍 Mockito 打桩。
 */
public class StubSecurityContext implements SecurityContext {

    private Long userId;
    private Long tenantId;
    private Long deptId;
    private String nickname;
    private Set<Long> dataScopeDeptIds;
    private boolean login;
    private boolean admin;

    public static StubSecurityContext anonymous() {
        return new StubSecurityContext();
    }

    public static StubSecurityContext tenantUser(Long tenantId, Long userId) {
        StubSecurityContext context = new StubSecurityContext();
        context.tenantId = tenantId;
        context.userId = userId;
        context.login = true;
        return context;
    }

    public StubSecurityContext withDataScopeDeptIds(Set<Long> deptIds) {
        this.dataScopeDeptIds = deptIds;
        return this;
    }

    public StubSecurityContext withAdmin(boolean admin) {
        this.admin = admin;
        return this;
    }

    public StubSecurityContext withDeptId(Long deptId) {
        this.deptId = deptId;
        return this;
    }

    public StubSecurityContext withNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public StubSecurityContext withTenantId(Long tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public Long getTenantId() {
        return tenantId;
    }

    @Override
    public Long getDeptId() {
        return deptId;
    }

    @Override
    public String getNickname() {
        return nickname;
    }

    @Override
    public Set<Long> getDataScopeDeptIds() {
        return dataScopeDeptIds;
    }

    @Override
    public boolean isLogin() {
        return login;
    }

    @Override
    public boolean isAdmin() {
        return admin;
    }
}
