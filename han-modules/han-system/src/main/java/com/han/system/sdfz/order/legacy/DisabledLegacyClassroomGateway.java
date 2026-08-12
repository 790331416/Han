package com.han.system.sdfz.order.legacy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 未配置物化通道时的占位实现。
 *
 * <p>订购单在 Han 侧照常创建、流转，只是同步动作会明确报错，而不是静默什么都不做。
 * 已经物化进三课堂的听课记录完全不受影响——它们存在旧库里，运行时判定不回调 Han。</p>
 */
public class DisabledLegacyClassroomGateway implements LegacyClassroomGateway {

    private static final String MESSAGE =
            "三课堂物化通道未启用，请先配置 sdfz.order.legacy.channel";

    @Override
    public List<LegacyCourse> listCourses(String lectureClassId, LocalDateTime from, LocalDateTime to) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public LegacyCourse findCourse(String courseId) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public String materializeAttend(LegacyAttendRequest request) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public void revokeAttend(String courseId, String classId) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public boolean isAttendActive(String courseId, String classId) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }
}
