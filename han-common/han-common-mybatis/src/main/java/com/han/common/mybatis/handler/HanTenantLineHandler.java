package com.han.common.mybatis.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.config.TenantProperties;
import com.han.common.tenant.enums.MissingTenantContextStrategy;
import com.han.common.tenant.exception.MissingTenantContextException;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多租户 SQL 过滤处理器。
 *
 * <p>自动为业务表追加 tenant_id 条件，并跳过配置中的排除表与系统元数据表。</p>
 *
 * <p>拿不到租户上下文时的行为由 {@code tenant.missing-context} 决定：默认
 * {@link MissingTenantContextStrategy#IGNORE}（跳过过滤，与历史行为一致），
 * 同时把这次访问记录到 {@link MissingTenantContextRecorder}，为后续翻转成
 * fail-close 积累运行期证据。</p>
 */
@Slf4j
public class HanTenantLineHandler implements TenantLineHandler {

    /**
     * 默认排除清单（全小写，便于热路径直接 contains 判定）。
     *
     * <p>{@code sys_tenant_package_menu} 在三档 init 结构里并不存在，属历史残留；
     * 保留是无害的，删掉反而会让「排除清单 vs DDL」的对账少一条记录，等结构一致性校验
     * 落地后再统一清理。</p>
     *
     * <p>{@code sys_client} 没有 tenant_id 列（{@code full-init.sql} 的建表语句可查），
     * 一旦有代码在有租户上下文时访问它，插件会拼出 {@code sys_client.tenant_id = ?}，
     * PostgreSQL 会直接报「列不存在」。这是与 fail-open 无关的潜伏缺陷，先补上。</p>
     */
    private static final Set<String> DEFAULT_EXCLUDES = Set.of(
            "sys_menu",
            "sys_tenant",
            "sys_tenant_package",
            "sys_tenant_package_menu",
            "sys_oper_log",
            "sys_login_log",
            "sys_user_role",
            "sys_user_post",
            "sys_role_menu",
            "sys_role_dept",
            "sys_client"
    );

    /** 观测日志中标识这是一次 SQL 过滤判定 */
    private static final String OPERATION_SQL = "SQL";

    private final TenantProperties tenantProperties;
    private final SecurityContext securityContext;
    private final MissingTenantContextRecorder missingContextRecorder;

    /** 配置排除清单的归一化缓存：ignoreTable 在 SQL 解析热路径上，每张表都会调一次 */
    private volatile List<String> cachedExcludeSource;
    private volatile Set<String> cachedExcludes = Set.of();

    public HanTenantLineHandler(TenantProperties tenantProperties, SecurityContext securityContext) {
        this(tenantProperties, securityContext, new MissingTenantContextRecorder());
    }

    public HanTenantLineHandler(TenantProperties tenantProperties,
                                SecurityContext securityContext,
                                MissingTenantContextRecorder missingContextRecorder) {
        this.tenantProperties = tenantProperties;
        this.securityContext = securityContext;
        this.missingContextRecorder = missingContextRecorder == null
                ? new MissingTenantContextRecorder(false, 0L)
                : missingContextRecorder;
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = securityContext.getTenantId();
        if (tenantId == null) {
            if (strategy() == MissingTenantContextStrategy.FILTER) {
                // FILTER 是显式选择的静默空集语义，属预期路径
                log.warn("无租户上下文，按 FILTER 策略注入 tenant_id = NULL（恒不成立，返回空集）");
            } else {
                // ignoreTable() 已按策略先行处置，正常情况下不会走到这里；保留为最后一道防线。
                log.error("无租户上下文却进入了租户条件生成，将注入 tenant_id = NULL（恒不成立）");
            }
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 先判排除表：排除表本就不参与租户隔离，不应污染无租户上下文的观测数据。
        if (isExcludedTable(tableName)) {
            return true;
        }

        Long tenantId = securityContext.getTenantId();
        if (tenantId != null) {
            return false;
        }
        return handleMissingTenantContext(tableName);
    }

    /**
     * 判断表是否被排除在租户过滤之外（排除清单 + 数据库元数据表）。
     *
     * <p>不依赖当前是否有租户上下文，纯粹的配置判定，供填充器等其他组件复用。</p>
     */
    public boolean isExcludedTable(String tableName) {
        String normalized = tableName == null ? "" : tableName.toLowerCase(Locale.ROOT);
        // 数据库系统目录和 information_schema 不参与业务租户隔离，
        // 否则会污染 PostgreSQL/MySQL 代码生成等元数据查询。
        if (normalized.startsWith("pg_") || normalized.startsWith("information_schema")) {
            return true;
        }

        return DEFAULT_EXCLUDES.contains(normalized) || configuredExcludes().contains(normalized);
    }

    /**
     * 取配置排除清单的全小写视图，配置对象未变化时复用上一次的结果。
     */
    private Set<String> configuredExcludes() {
        List<String> source = tenantProperties.getExcludes();
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        if (source != cachedExcludeSource) {
            cachedExcludes = source.stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            cachedExcludeSource = source;
        }
        return cachedExcludes;
    }

    /**
     * 判断表是否启用「租户私有 + 平台共享」双语义。
     */
    public boolean isSharedTable(String tableName) {
        if (tableName == null) {
            return false;
        }
        TenantProperties.Shared shared = tenantProperties.getShared();
        if (shared == null || shared.getTables() == null) {
            return false;
        }
        return shared.getTables().stream().anyMatch(item -> item != null && item.equalsIgnoreCase(tableName));
    }

    /**
     * 构造共享数据的放行条件，例如 {@code tenant_id IS NULL} 或 {@code tenant_id = 0}。
     *
     * <p>无租户上下文时返回 null：此时连自己的数据都还没确定归属，不能顺带放行共享数据。</p>
     *
     * @param tenantColumn 已带表别名的租户列，例如 {@code u.tenant_id}
     * @return 共享条件；未配置任何共享判定方式时返回 null
     */
    public Expression buildSharedTenantExpression(String tenantColumn) {
        if (tenantColumn == null || securityContext.getTenantId() == null) {
            return null;
        }
        TenantProperties.Shared shared = tenantProperties.getShared();
        if (shared == null) {
            return null;
        }

        Expression result = null;
        if (!Boolean.FALSE.equals(shared.getMatchNull())) {
            IsNullExpression isNull = new IsNullExpression();
            isNull.setLeftExpression(new Column(tenantColumn));
            result = isNull;
        }

        List<Long> sharedTenantIds = shared.getTenantIds();
        if (sharedTenantIds != null) {
            for (Long sharedTenantId : sharedTenantIds) {
                if (sharedTenantId == null) {
                    continue;
                }
                EqualsTo equalsTo = new EqualsTo(new Column(tenantColumn), new LongValue(sharedTenantId));
                result = result == null ? equalsTo : new OrExpression(result, equalsTo);
            }
        }
        return result;
    }

    /**
     * 观测器，供运维接口与测试读取无租户上下文的聚合统计。
     */
    public MissingTenantContextRecorder getMissingContextRecorder() {
        return missingContextRecorder;
    }

    /**
     * 无租户上下文时按策略处置。
     *
     * @return true 表示跳过租户过滤（fail-open），false 表示继续注入租户条件
     */
    private boolean handleMissingTenantContext(String tableName) {
        missingContextRecorder.record(OPERATION_SQL, tableName);

        return switch (strategy()) {
            case IGNORE -> true;
            case FILTER -> false;
            case REJECT -> throw new MissingTenantContextException(
                    "缺少租户上下文，拒绝访问租户隔离表: " + tableName);
        };
    }

    private MissingTenantContextStrategy strategy() {
        MissingTenantContextStrategy strategy = tenantProperties.getMissingContext();
        return strategy == null ? MissingTenantContextStrategy.IGNORE : strategy;
    }
}
