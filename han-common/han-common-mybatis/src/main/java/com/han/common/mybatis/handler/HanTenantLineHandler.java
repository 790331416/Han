package com.han.common.mybatis.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.config.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;

import java.util.List;
import java.util.Locale;

/**
 * 多租户 SQL 过滤处理器。
 *
 * <p>自动为业务表追加 tenant_id 条件，并跳过配置中的排除表与系统元数据表。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class HanTenantLineHandler implements TenantLineHandler {

    private static final List<String> DEFAULT_EXCLUDES = List.of(
            "sys_menu",
            "sys_tenant",
            "sys_tenant_package",
            "sys_tenant_package_menu",
            "sys_oper_log",
            "sys_login_log",
            "sys_user_role",
            "sys_user_post",
            "sys_role_menu",
            "sys_role_dept"
    );

    private final TenantProperties tenantProperties;
    private final SecurityContext securityContext;

    @Override
    public Expression getTenantId() {
        Long tenantId = securityContext.getTenantId();
        if (tenantId == null) {
            log.warn("无法获取有效的 tenantId，跳过租户条件注入");
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        Long tenantId = securityContext.getTenantId();
        if (tenantId == null) {
            return true;
        }

        String normalized = tableName == null ? "" : tableName.toLowerCase(Locale.ROOT);
        // PostgreSQL 系统目录和 information_schema 不参与业务租户隔离，
        // 否则会污染代码生成等元数据查询。
        if (normalized.startsWith("pg_") || normalized.startsWith("information_schema")) {
            return true;
        }

        if (DEFAULT_EXCLUDES.stream().anyMatch(item -> item.equalsIgnoreCase(tableName))) {
            return true;
        }

        List<String> excludes = tenantProperties.getExcludes();
        return excludes != null && excludes.stream().anyMatch(item -> item.equalsIgnoreCase(tableName));
    }
}
