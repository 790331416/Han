package com.han.common.mybatis.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.annotation.DataPermission;
import com.han.common.mybatis.context.DataPermissionContextHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限处理器
 *
 * <p>由 {@code DataPermissionInterceptor} 驱动，为标注了 {@link DataPermission} 的语句
 * 追加数据范围条件。未标注的语句一律返回 null，不产生任何额外条件 —— 这保证接线本身
 * 不改变任何现有查询的结果集。</p>
 *
 * <p>注解来源有两处，优先级从高到低：Service 方法上的注解（经
 * {@code DataPermissionAspect} 放入 {@link DataPermissionContextHolder}）、
 * Mapper 方法或 Mapper 接口上的注解（按语句 ID 反射解析）。</p>
 */
@Slf4j
public class HanDataPermissionHandler implements DataPermissionHandler {

    /** MyBatis-Plus 分页插件为 count 语句追加的后缀 */
    private static final String COUNT_STATEMENT_SUFFIX = "_mpCount";

    private final SecurityContext securityContext;

    /** 语句 ID -> 注解，避免每条 SQL 都走一次反射 */
    private final Map<String, Optional<DataPermission>> annotationCache = new ConcurrentHashMap<>();

    public HanDataPermissionHandler(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        DataPermission dataPermission = resolveDataPermission(mappedStatementId);
        if (dataPermission == null) {
            // 未标注数据权限的语句：完全透明，不动原有 WHERE
            return null;
        }

        Expression dataScope = buildDataScopeExpression(dataPermission);
        if (dataScope == null) {
            return null;
        }
        if (where == null) {
            return dataScope;
        }
        // 原 WHERE 是 OR 组合时必须加括号，否则 AND 会被 OR 拆开
        Expression left = where instanceof OrExpression ? parenthesis(where) : where;
        return new AndExpression(left, dataScope);
    }

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

    /**
     * 构造数据范围条件。
     *
     * <p>返回 null 表示不限制；返回恒不成立条件表示拒绝。</p>
     */
    private Expression buildDataScopeExpression(DataPermission dataPermission) {
        if (securityContext.isAdmin()) {
            // 超级管理员不受数据范围限制
            return null;
        }
        if (!securityContext.isLogin()) {
            // 未登录不等于不限制：被标注的语句在没有身份时必须拒绝，不能静默放行
            log.warn("数据权限过滤缺少登录身份，拒绝该查询");
            return alwaysFalse();
        }

        Set<Long> deptIds = securityContext.getDataScopeDeptIds();
        if (deptIds == null) {
            // 角色配置的是「全部数据」
            return null;
        }

        Long userId = securityContext.getUserId();
        Expression selfExpression = userId == null
                ? null
                : new EqualsTo(column(dataPermission.userAlias(), dataPermission.createByColumn()), new LongValue(userId));

        if (deptIds.isEmpty()) {
            // 「仅本人」模式
            return selfExpression == null ? alwaysFalse() : parenthesis(selfExpression);
        }

        List<Expression> deptValues = deptIds.stream()
                .filter(java.util.Objects::nonNull)
                .<Expression>map(LongValue::new)
                .toList();
        if (deptValues.isEmpty()) {
            return selfExpression == null ? alwaysFalse() : parenthesis(selfExpression);
        }

        InExpression inExpression = new InExpression(
                column(dataPermission.deptAlias(), dataPermission.deptIdColumn()),
                new ParenthesedExpressionList<>(deptValues));
        Expression combined = selfExpression == null ? inExpression : new OrExpression(inExpression, selfExpression);
        return parenthesis(combined);
    }

    /**
     * 解析语句对应的数据权限注解，Service 层声明优先。
     */
    private DataPermission resolveDataPermission(String mappedStatementId) {
        DataPermission declared = DataPermissionContextHolder.get();
        if (declared != null) {
            return declared;
        }
        if (mappedStatementId == null || mappedStatementId.isBlank()) {
            return null;
        }
        return annotationCache
                .computeIfAbsent(mappedStatementId, this::findMapperAnnotation)
                .orElse(null);
    }

    private Optional<DataPermission> findMapperAnnotation(String mappedStatementId) {
        String statementId = mappedStatementId.endsWith(COUNT_STATEMENT_SUFFIX)
                ? mappedStatementId.substring(0, mappedStatementId.length() - COUNT_STATEMENT_SUFFIX.length())
                : mappedStatementId;

        int separator = statementId.lastIndexOf('.');
        if (separator <= 0) {
            return Optional.empty();
        }
        String className = statementId.substring(0, separator);
        String methodName = statementId.substring(separator + 1);

        try {
            Class<?> mapperClass = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataPermission.class)) {
                    return Optional.of(method.getAnnotation(DataPermission.class));
                }
            }
            return Optional.ofNullable(mapperClass.getAnnotation(DataPermission.class));
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("解析数据权限注解失败，按未标注处理: statementId={}", mappedStatementId);
            return Optional.empty();
        }
    }

    private Column column(String alias, String columnName) {
        String qualified = alias == null || alias.isBlank() ? columnName : alias + '.' + columnName;
        return new Column(qualified);
    }

    private Expression parenthesis(Expression expression) {
        return new ParenthesedExpressionList<>(expression);
    }

    /**
     * 恒不成立条件，用于「有数据权限声明但没有身份」时明确拒绝。
     */
    private Expression alwaysFalse() {
        return new EqualsTo(new LongValue(1L), new LongValue(0L));
    }
}
