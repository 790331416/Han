package com.han.system.sdfz.order.legacy;

/**
 * 要写进三课堂 {@code tb_course_attend} 的一行听课记录。
 *
 * <p>字段全部按《Han 与三课堂实体 ID 映射结论》的口径填：
 * {@code organId} / {@code classId} / {@code memberId} 在旧库都是快照列，不是外键，
 * 服务端原样落库、不查表不校验，因此直接写 Han 的原生 ID，不做任何换算。</p>
 */
public record LegacyAttendRequest(
        String courseId,
        String courseType,
        String organId,
        String organName,
        String classId,
        String className,
        String provinceCode,
        String provinceName,
        String cityCode,
        String cityName,
        String countyCode,
        String countyName,
        String placeId,
        String placeName,
        String roomId,
        String roomName,
        String memberId,
        String memberName) {
}
