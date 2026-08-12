package com.han.system.sdfz.education;

import com.han.system.sdfz.education.domain.SemesterLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("学期三态的日期边界")
class SemesterLifecycleTest {

    private static final LocalDate BEGIN = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2027, 1, 15);

    @Test
    @DisplayName("闭区间：起止当天都算进行中")
    void treatsBothEndsAsInclusive() {
        assertThat(SemesterLifecycle.of(BEGIN, END, BEGIN.minusDays(1)))
                .isEqualTo(SemesterLifecycle.NOT_STARTED);
        assertThat(SemesterLifecycle.of(BEGIN, END, BEGIN))
                .isEqualTo(SemesterLifecycle.IN_PROGRESS);
        assertThat(SemesterLifecycle.of(BEGIN, END, END))
                .isEqualTo(SemesterLifecycle.IN_PROGRESS);
        assertThat(SemesterLifecycle.of(BEGIN, END, END.plusDays(1)))
                .isEqualTo(SemesterLifecycle.FINISHED);
    }

    @Test
    @DisplayName("三个取值都不是数字，不会和 status 的 0/1 语义混淆")
    void usesStringEnumInsteadOfNumericStatus() {
        for (SemesterLifecycle value : SemesterLifecycle.values()) {
            assertThat(value.name()).matches("[A-Z_]+");
        }
        assertThat(SemesterLifecycle.values()).hasSize(3);
    }
}
