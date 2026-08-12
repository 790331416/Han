package com.han.system.sdfz.compat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定旧系统 {@code status} 兼作软删除标志这一反直觉语义，防止被"修正"回字面含义。
 */
class LegacyStatusTest {

    @Test
    void mapsHanDelFlagRatherThanHanStatusOntoLegacyStatus() {
        assertThat(LegacyStatus.ofDelFlag(0)).isEqualTo("0");
        assertThat(LegacyStatus.ofDelFlag(1)).isEqualTo("1");
    }

    @Test
    void treatsMissingDelFlagAsPresentInsteadOfDeleted() {
        assertThat(LegacyStatus.ofDelFlag(null)).isEqualTo(LegacyStatus.PRESENT);
    }

    @Test
    void treatsAnyNonZeroDelFlagAsDeleted() {
        assertThat(LegacyStatus.ofDelFlag(2)).isEqualTo(LegacyStatus.DELETED);
    }

    @Test
    void recognizesLegacyFiltersThatAskForDeletedRows() {
        assertThat(LegacyStatus.selectsDeleted("1")).isTrue();
        assertThat(LegacyStatus.selectsDeleted(" 1 ")).isTrue();
        assertThat(LegacyStatus.selectsDeleted("0")).isFalse();
        assertThat(LegacyStatus.selectsDeleted(null)).isFalse();
    }
}
