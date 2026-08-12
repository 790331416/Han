package com.han.system.sdfz.order.legacy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Han 与三课堂之间的授权物化端口。
 *
 * <p>之所以做成端口而不是直接写 SQL：《Han 与三课堂实体 ID 映射结论》§6 D1 拍板「走旧 api 内部接口，
 * 不直连旧库」，理由是避免出现两个写入方。但核查旧 api 后发现该接口<b>目前并不存在</b>——
 * {@code TbCourseAttendController} 是空壳，唯一能改动听课记录的入口是
 * {@code /tb-course-info/updateCourseInfo}，它的语义是「整门课全量覆盖」：
 * {@code updateById(course)} + {@code deleteByCourseId(...)} 硬删 + 全表重插，
 * 而且整个方法没有 {@code @Transactional}。用它来加一行听课记录，等价于让 Han 对不属于自己的课程做
 * 读-改-写，中途失败会把别人已有的听课记录物理删掉且无法回滚。详见实现类上的说明。</p>
 *
 * <p>因此这里保留端口，给出两个实现：{@link LegacyClassroomJdbcGateway}（当前可用）与
 * {@link LegacyClassroomHttpGateway}（等旧 api 补齐内部接口后切换，只改配置不改代码）。</p>
 */
public interface LegacyClassroomGateway {

    /**
     * 查主讲班在时间窗内的全部有效课程，用于计算候选课程集。
     *
     * @param lectureClassId 主讲班标识，即 {@code tb_course_info.class_id}
     */
    List<LegacyCourse> listCourses(String lectureClassId, LocalDateTime from, LocalDateTime to);

    /**
     * 按课程 ID 查单节课，供新课程事件与对账使用。
     */
    LegacyCourse findCourse(String courseId);

    /**
     * 幂等地把一行听课记录写进 {@code tb_course_attend} 并返回 {@code attend_id}。
     *
     * <p>{@code tb_course_attend} 没有唯一索引，幂等必须由调用方自己保证：
     * 实现要先按 {@code (fk_course_id, class_id)} 查重，命中就复用已有 {@code attend_id}
     * 并把状态恢复为正常，查不到才新建。这是 ORDER-08 的第二道保险。</p>
     */
    String materializeAttend(LegacyAttendRequest request);

    /**
     * 把一行听课记录置为删除（{@code status = '1'}）。
     *
     * <p>只有引用计数归零时才会被调用。记录不存在时静默返回，重复调用无副作用。</p>
     */
    void revokeAttend(String courseId, String classId);

    /**
     * 查听课记录当前是否处在正常状态，对账用。
     */
    boolean isAttendActive(String courseId, String classId);
}
