package com.han.system.sdfz.order;

import com.han.system.sdfz.order.domain.GrantScope;
import com.han.system.sdfz.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("订购单状态推导")
class CourseOrderStatusTest {

    private static final LocalDateTime EFFECTIVE = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
    private static final LocalDateTime EXPIRE = LocalDateTime.of(2027, 1, 15, 23, 59, 59);

    @Test
    @DisplayName("ORDER-03 边界是闭区间，生效当刻已生效，失效当刻仍生效")
    void resolvesStatusOnClosedInterval() {
        assertThat(CourseOrderService.resolveStatus(EFFECTIVE, EXPIRE, EFFECTIVE.minusSeconds(1)))
                .isEqualTo(OrderStatus.PENDING);
        assertThat(CourseOrderService.resolveStatus(EFFECTIVE, EXPIRE, EFFECTIVE))
                .isEqualTo(OrderStatus.ACTIVE);
        assertThat(CourseOrderService.resolveStatus(EFFECTIVE, EXPIRE, EXPIRE))
                .isEqualTo(OrderStatus.ACTIVE);
        assertThat(CourseOrderService.resolveStatus(EFFECTIVE, EXPIRE, EXPIRE.plusSeconds(1)))
                .isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("只有 ACTIVE 会往三课堂授权新课程")
    void onlyActiveGrants() {
        assertThat(OrderStatus.ACTIVE.isGranting()).isTrue();
        for (OrderStatus status : OrderStatus.values()) {
            if (status != OrderStatus.ACTIVE) {
                assertThat(status.isGranting()).as("%s 不应授权新课程", status).isFalse();
            }
        }
    }

    @Test
    @DisplayName("占用唯一槽位的正好是 PENDING / ACTIVE / FROZEN")
    void occupyingStatusesMatchGeneratedColumn() {
        assertThat(OrderStatus.OCCUPYING)
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.ACTIVE, OrderStatus.FROZEN);
        assertThat(OrderStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.DRAFT.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("枚举解析大小写不敏感，非法值返回 null 而不是抛异常")
    void parsesLeniently() {
        assertThat(GrantScope.parse("whole_class")).isEqualTo(GrantScope.WHOLE_CLASS);
        assertThat(GrantScope.parse("BY_SUBJECT")).isEqualTo(GrantScope.BY_SUBJECT);
        assertThat(GrantScope.parse("SOMETHING")).isNull();
        assertThat(OrderStatus.parse(null)).isNull();
    }
}
