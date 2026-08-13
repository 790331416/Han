package com.han.common.tenant.enums;

/**
 * 无租户上下文时的处置策略。
 *
 * <p>三态开关，由配置项 {@code tenant.missing-context} 驱动，用于把「拿不到租户时怎么办」
 * 从散落在代码里的隐式行为收敛成一个可灰度、可秒级回退的显式开关。</p>
 *
 * <ul>
 *   <li>{@link #IGNORE}：跳过租户过滤（fail-open）。当前默认值，与历史行为一致。</li>
 *   <li>{@link #FILTER}：注入恒不成立的租户条件（{@code tenant_id = NULL}），静默返回空集。</li>
 *   <li>{@link #REJECT}：直接拒绝，抛出 {@code MissingTenantContextException}。</li>
 * </ul>
 *
 * <p>切换到 {@link #FILTER} 或 {@link #REJECT} 之前，必须先保证所有合法的无租户场景都已经
 * 显式标注（{@code @IgnoreTenant} 或 {@code TenantHelper.ignore}）或已补齐上下文，
 * 否则内部调用与定时任务会立刻查不到数据。</p>
 */
public enum MissingTenantContextStrategy {

    /** 跳过租户过滤，保持历史的 fail-open 行为 */
    IGNORE,

    /** 注入恒不成立的租户条件，静默返回空集 */
    FILTER,

    /** 拒绝执行并抛出异常 */
    REJECT
}
