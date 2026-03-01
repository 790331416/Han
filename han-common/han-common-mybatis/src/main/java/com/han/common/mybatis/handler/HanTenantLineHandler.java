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

/**
 * 多租户 SQL 过滤处理器
 * <p>
 * 自动为 SQL 追加 tenant_id 条件，排除配置中指定的表。
 */
@Slf4j
@RequiredArgsConstructor
public class HanTenantLineHandler implements TenantLineHandler {

    private final TenantProperties tenantProperties;
    private final SecurityContext securityContext;

    /**
     * 默认不需要租户过滤的表
     */
    private static final List<String> DEFAULT_EXCLUDES = List.of(
            "sys_menu",
            "sys_tenant",
            "sys_tenant_package",
            "sys_oper_log",
            "sys_login_log"
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = securityContext.getTenantId();
        if (tenantId == null) {
            log.warn("无法获取有效的租户ID -> null");
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        Long tenantId = securityContext.getTenantId();
        if (tenantId == null) {
            // 未登录或无租户上下文，跳过租户过滤
            return true;
        }
        // 默认排除表
        if (DEFAULT_EXCLUDES.stream().anyMatch(t -> t.equalsIgnoreCase(tableName))) {
            return true;
        }
        // 配置文件排除表
        List<String> excludes = tenantProperties.getExcludes();
        if (excludes != null && excludes.stream().anyMatch(t -> t.equalsIgnoreCase(tableName))) {
            return true;
        }
        return false;
    }
}
