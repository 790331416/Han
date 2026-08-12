package com.han.system.sdfz.order.legacy;

import java.time.LocalDateTime;

/**
 * 三课堂 {@code tb_course_info} 的一节课，只取授权计算与物化用得到的列。
 *
 * @param courseId    课程 ID
 * @param courseType  1 专递 / 2 名师 / 3 名校 / 4 视频会议 / 5 直播
 * @param classId     主讲班标识。旧库该列是快照列不是外键，切到 Han 目录后其值就是 edu_class.id
 * @param subjectCode 科目编码，对应 edu_subject.subject_code
 * @param timeBegin   上课开始时间
 */
public record LegacyCourse(
        String courseId,
        String courseName,
        String courseType,
        String classId,
        String className,
        String organId,
        String organName,
        String subjectCode,
        String subjectName,
        LocalDateTime timeBegin,
        LocalDateTime timeEnd) {
}
