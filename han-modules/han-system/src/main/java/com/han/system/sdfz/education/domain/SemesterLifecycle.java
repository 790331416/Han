package com.han.system.sdfz.education.domain;

import java.time.LocalDate;

/**
 * 学期阶段。
 *
 * <p>刻意与 {@code edu_semester.status} 分开：{@code status} 在 {@code edu_*} 全部表里的语义是
 * 「0 正常 / 1 停用」，表达的是这条记录本身启不启用；再用同一列的 0 表示「未开始」必然被误读。
 * 因此另开 {@code lifecycle_status} 列，并且用字符串枚举而不是数字，从取值上就不可能和 status 混淆。</p>
 */
public enum SemesterLifecycle {

    /** 当前日期早于 begin_date。 */
    NOT_STARTED,

    /** begin_date ≤ 当前日期 ≤ end_date，闭区间。 */
    IN_PROGRESS,

    /** 当前日期晚于 end_date。 */
    FINISHED;

    /**
     * 按闭区间判定某一天落在学期的哪个阶段。
     */
    public static SemesterLifecycle of(LocalDate beginDate, LocalDate endDate, LocalDate today) {
        if (beginDate != null && today.isBefore(beginDate)) {
            return NOT_STARTED;
        }
        if (endDate != null && today.isAfter(endDate)) {
            return FINISHED;
        }
        return IN_PROGRESS;
    }
}
