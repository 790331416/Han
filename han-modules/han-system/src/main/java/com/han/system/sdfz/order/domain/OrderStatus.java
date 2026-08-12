package com.han.system.sdfz.order.domain;

import java.util.Set;

/**
 * 订购单状态。
 *
 * <pre>
 *   DRAFT ──提交──► PENDING ──到达生效时间──► ACTIVE ◄──恢复──► FROZEN
 *                                              │
 *                              超过失效时间      ▼
 *                                           EXPIRED
 *   DRAFT / PENDING / ACTIVE / FROZEN ──取消──► CANCELLED
 * </pre>
 *
 * <p>{@code PENDING→ACTIVE} 与 {@code ACTIVE→EXPIRED} 由定时任务按日期推进，不依赖人工。</p>
 */
public enum OrderStatus {

    DRAFT(false),
    PENDING(false),
    ACTIVE(true),
    FROZEN(false),
    EXPIRED(false),
    CANCELLED(false);

    /** 处于这三个状态的单子占用「同一听讲班+主讲班+学期只能有一张有效单」的槽位。 */
    public static final Set<OrderStatus> OCCUPYING = Set.of(PENDING, ACTIVE, FROZEN);

    private final boolean granting;

    OrderStatus(boolean granting) {
        this.granting = granting;
    }

    /** 是否处在「会往三课堂授权新课程」的状态。 */
    public boolean isGranting() {
        return granting;
    }

    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELLED;
    }

    public static OrderStatus parse(String value) {
        for (OrderStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
