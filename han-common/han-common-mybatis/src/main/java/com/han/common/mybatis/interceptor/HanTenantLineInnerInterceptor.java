package com.han.common.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Table;

/**
 * 支持「租户私有数据 + 平台共享数据」双语义的租户拦截器。
 *
 * <p>MyBatis-Plus 原生的 {@code TenantLineHandler.getTenantId()} 只能返回单值，
 * 因此只有「过滤」和「不过滤」两态。字典、参数、菜单、Prompt 模板这类表天然需要
 * 「平台预置一份、租户可以覆盖」的语义，过去只能靠把表塞进排除清单（丢掉隔离）
 * 或在业务侧手写条件（散落且易漏）来绕开。</p>
 *
 * <p>本拦截器在生成租户条件之后，对配置为共享表的表额外 OR 上共享条件，得到
 * {@code (t.tenant_id = 1 OR t.tenant_id IS NULL)} 这样的表达式，并用括号包住，
 * 避免与其他条件 AND 拼接时发生优先级错误。</p>
 *
 * <p>没有配置共享表时（默认），行为与原生拦截器完全一致。
 * INSERT 不受影响：写入的永远是当前租户自己的数据，不会写成共享数据。</p>
 */
public class HanTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    private final HanTenantLineHandler tenantLineHandler;

    public HanTenantLineInnerInterceptor(HanTenantLineHandler tenantLineHandler) {
        super(tenantLineHandler);
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        Expression tenantExpression = super.buildTableExpression(table, where, whereSegment);
        if (tenantExpression == null || table == null || !tenantLineHandler.isSharedTable(table.getName())) {
            return tenantExpression;
        }

        Expression sharedExpression =
                tenantLineHandler.buildSharedTenantExpression(getAliasColumn(table).getColumnName());
        if (sharedExpression == null) {
            return tenantExpression;
        }
        return new ParenthesedExpressionList<>(new OrExpression(tenantExpression, sharedExpression));
    }
}
