package com.han.system.sdfz.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.sdfz.order.domain.EduCourseOrderGrantPo;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import com.han.system.sdfz.order.domain.EduCourseOrderSubjectPo;
import com.han.system.sdfz.order.domain.GrantScope;
import com.han.system.sdfz.order.domain.GrantStatus;
import com.han.system.sdfz.order.domain.OrderStatus;
import com.han.system.sdfz.order.legacy.LegacyAttendRequest;
import com.han.system.sdfz.order.legacy.LegacyClassroomException;
import com.han.system.sdfz.order.legacy.LegacyClassroomGateway;
import com.han.system.sdfz.order.legacy.LegacyCourse;
import com.han.system.sdfz.order.mapper.EduCourseOrderGrantMapper;
import com.han.system.sdfz.order.mapper.EduCourseOrderMapper;
import com.han.system.sdfz.order.mapper.EduCourseOrderSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 候选课程集计算与授权物化。
 *
 * <p>整班打包与按科目<b>唯一</b>的差异在 {@link #candidateCourses}：前者不按科目过滤，主讲班新开任何科目
 * 都自动落进来；后者只收明细里列出的科目。除此之外的状态机、幂等、撤销、重试全部共用同一条路径。</p>
 *
 * <p>这里的方法<b>刻意都不加 {@code @Transactional}</b>。物化跨 Han 库与三课堂库两个数据源，
 * 本来就不可能原子；批量物化如果套一个大事务，一条失败会把已经成功的几百条一起回滚，
 * 与 ORDER-09「部分失败可重试」直接冲突。台账的每一次状态变更由 {@link CourseGrantLedger}
 * 用 {@code REQUIRES_NEW} 独立提交，最终一致靠对账任务兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseGrantService {

    /** 重试退避上限，避免长期失败的记录把重试队列刷屏。 */
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final EduCourseOrderMapper orderMapper;
    private final EduCourseOrderSubjectMapper orderSubjectMapper;
    private final EduCourseOrderGrantMapper grantMapper;
    private final EduSubjectMapper subjectMapper;
    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduRoomMapper roomMapper;
    private final EduDeviceMapper deviceMapper;
    private final CourseGrantLedger ledger;
    private final LegacyClassroomGateway gateway;

    /**
     * 同步结果。
     */
    public record SyncResult(int materialized, int alreadyMaterialized, int failed, int revoked) {

        static SyncResult empty() {
            return new SyncResult(0, 0, 0, 0);
        }

        SyncResult plus(SyncResult other) {
            return new SyncResult(
                    materialized + other.materialized,
                    alreadyMaterialized + other.alreadyMaterialized,
                    failed + other.failed,
                    revoked + other.revoked);
        }

        public int total() {
            return materialized + alreadyMaterialized + failed + revoked;
        }
    }

    // ------------------------------------------------------------------
    // 候选课程集
    // ------------------------------------------------------------------

    /**
     * 计算订购单 O 的候选课程集 C(O)。
     *
     * <pre>
     * WHOLE_CLASS: C(O) = { c | c.主讲班 = LC ∧ c.上课时间 ∈ [生效, 失效] }
     * BY_SUBJECT : C(O) = { c | 同上 ∧ c.科目 ∈ 明细 }
     * </pre>
     */
    public List<LegacyCourse> candidateCourses(EduCourseOrderPo order) {
        List<LegacyCourse> courses = gateway.listCourses(
                String.valueOf(order.getLectureClassId()),
                order.getEffectiveTime(),
                order.getExpireTime());
        if (order.scope() != GrantScope.BY_SUBJECT) {
            return courses;
        }
        Set<String> allowed = subscribedSubjectCodes(order.getId());
        if (allowed.isEmpty()) {
            return List.of();
        }
        List<LegacyCourse> filtered = new ArrayList<>();
        for (LegacyCourse course : courses) {
            if (course.subjectCode() != null && allowed.contains(course.subjectCode())) {
                filtered.add(course);
            }
        }
        return filtered;
    }

    /**
     * 判断某节课是否落在某张单的候选集里，供新课程事件使用（不需要拉全量）。
     */
    public boolean covers(EduCourseOrderPo order, LegacyCourse course) {
        if (course == null || course.classId() == null) {
            return false;
        }
        if (!course.classId().equals(String.valueOf(order.getLectureClassId()))) {
            return false;
        }
        LocalDateTime begin = course.timeBegin();
        if (begin == null) {
            return false;
        }
        if (order.getEffectiveTime() != null && begin.isBefore(order.getEffectiveTime())) {
            return false;
        }
        if (order.getExpireTime() != null && begin.isAfter(order.getExpireTime())) {
            return false;
        }
        if (order.scope() != GrantScope.BY_SUBJECT) {
            return true;
        }
        return course.subjectCode() != null
                && subscribedSubjectCodes(order.getId()).contains(course.subjectCode());
    }

    // ------------------------------------------------------------------
    // 物化
    // ------------------------------------------------------------------

    /**
     * 把一张订购单的候选集同步到三课堂：应授权未物化的补上，已物化不应授权的按引用计数撤销。
     *
     * <p>重复执行不会产生重复台账，也不会产生重复听课记录（ORDER-07 / ORDER-08）。</p>
     */
    public SyncResult syncOrder(EduCourseOrderPo order) {
        OrderStatus status = order.orderStatus();
        if (status == null || !status.isGranting()) {
            log.debug("订购单不在生效中，跳过同步: orderId={}, status={}", order.getId(), order.getStatus());
            return SyncResult.empty();
        }

        List<LegacyCourse> candidates = candidateCourses(order);
        Set<String> candidateIds = new LinkedHashSet<>();
        AttendContext context = attendContext(order);
        SyncResult result = SyncResult.empty();

        for (LegacyCourse course : candidates) {
            candidateIds.add(course.courseId());
            result = result.plus(materializeOne(order, course, context));
        }

        // 已物化但不再落在候选集里的（科目被移出明细、课程被取消），按引用计数撤销。
        for (EduCourseOrderGrantPo grant : ledger.findByOrder(order.getId())) {
            if (candidateIds.contains(grant.getCourseId())) {
                continue;
            }
            if (grant.status() == GrantStatus.REVOKED) {
                continue;
            }
            revokeGrant(grant);
            result = result.plus(new SyncResult(0, 0, 0, 1));
        }
        return result;
    }

    /**
     * 新课程创建时的自动物化（ORDER-06）。
     *
     * <p>查出命中该主讲班的全部生效中订购单，按各自粒度判定：{@code WHOLE_CLASS} 无条件命中，
     * {@code BY_SUBJECT} 需课程科目在明细里。</p>
     *
     * @return 实际新增物化的条数
     */
    public SyncResult onCourseCreated(String courseId) {
        LegacyCourse course = gateway.findCourse(courseId);
        if (course == null) {
            log.info("三课堂查无此课程，跳过自动物化: courseId={}", courseId);
            return SyncResult.empty();
        }
        if (course.classId() == null || course.classId().isBlank()) {
            return SyncResult.empty();
        }
        Long lectureClassId;
        try {
            lectureClassId = Long.valueOf(course.classId());
        } catch (NumberFormatException ignored) {
            // 旧库 class_id 是快照列，历史数据里可能残留智慧校园口径的非数字标识。
            log.info("课程主讲班标识不是 Han 班级 ID，跳过自动物化: courseId={}", courseId);
            return SyncResult.empty();
        }

        List<EduCourseOrderPo> orders = orderMapper.selectList(new LambdaQueryWrapper<EduCourseOrderPo>()
                .eq(EduCourseOrderPo::getLectureClassId, lectureClassId)
                .eq(EduCourseOrderPo::getStatus, OrderStatus.ACTIVE.name()));

        SyncResult result = SyncResult.empty();
        for (EduCourseOrderPo order : orders) {
            if (!covers(order, course)) {
                continue;
            }
            result = result.plus(materializeOne(order, course, attendContext(order)));
        }
        return result;
    }

    /**
     * 物化单节课。台账与三课堂的写入分两步，中间不共享事务——跨库做不到原子。
     *
     * <p>顺序是「先落台账 PENDING，再写三课堂，最后回填 MATERIALIZED」。任一步崩掉，
     * 台账都停在 PENDING 或 FAILED，下一轮同步/重试会再调一次
     * {@link LegacyClassroomGateway#materializeAttend}，而它本身是幂等的，所以会收敛。
     * 反过来先写三课堂再落台账则会产生查不到台账的孤儿听课记录。</p>
     */
    private SyncResult materializeOne(EduCourseOrderPo order, LegacyCourse course, AttendContext context) {
        Long subjectId = context.subjectIdByCode().get(course.subjectCode());
        EduCourseOrderGrantPo grant = ledger.upsertPending(order, course, subjectId);

        if (grant.status() == GrantStatus.MATERIALIZED && !grant.isSuspended() && grant.getAttendId() != null) {
            return new SyncResult(0, 1, 0, 0);
        }

        try {
            String attendId = gateway.materializeAttend(context.toRequest(course));
            ledger.markMaterialized(grant.getId(), attendId);
            return new SyncResult(1, 0, 0, 0);
        } catch (LegacyClassroomException ex) {
            ledger.markFailed(grant.getId(), attemptCount(grant), ex.getMessage(), ex.isRetryable());
            log.warn("授权物化失败: orderId={}, courseId={}, retryable={}, reason={}",
                    order.getId(), course.courseId(), ex.isRetryable(), ex.getMessage());
            return new SyncResult(0, 0, 1, 0);
        } catch (RuntimeException ex) {
            ledger.markFailed(grant.getId(), attemptCount(grant), ex.getClass().getSimpleName(), true);
            log.warn("授权物化异常: orderId={}, courseId={}", order.getId(), course.courseId(), ex);
            return new SyncResult(0, 0, 1, 0);
        }
    }

    // ------------------------------------------------------------------
    // 撤销（引用计数）
    // ------------------------------------------------------------------

    /**
     * 撤销一条台账。
     *
     * <p>先把台账置 {@code REVOKED}，再数这节课对这个听讲班<b>还有没有别的单</b>在授权；
     * 还有就保留听课记录，一条都不剩才真正把 {@code tb_course_attend.status} 置 1。</p>
     *
     * <p>不做引用计数会出现越权撤销：撤销 A 单把 B 单授权的同一节课也一起断掉。</p>
     */
    public void revokeGrant(EduCourseOrderGrantPo grant) {
        ledger.markRevoked(grant.getId());
        releaseAttendIfUnreferenced(grant);
    }

    /**
     * 台账已经不再引用这节课时，才动三课堂的听课记录。
     */
    private void releaseAttendIfUnreferenced(EduCourseOrderGrantPo grant) {
        long remaining = ledger.countActiveReferences(
                grant.getCourseId(), grant.getListenClassId(), grant.getOrderId());
        if (remaining > 0) {
            log.debug("仍有 {} 张单在授权该课程，保留听课记录: courseId={}, listenClassId={}",
                    remaining, grant.getCourseId(), grant.getListenClassId());
            return;
        }
        try {
            gateway.revokeAttend(grant.getCourseId(), String.valueOf(grant.getListenClassId()));
        } catch (LegacyClassroomException ex) {
            // 撤销失败不回滚台账：台账已经表达「这张单不再授权」，
            // 听课记录的清理交给对账任务，重试一次比留一条状态不一致的台账更安全。
            log.warn("撤销听课记录失败，等待对账补偿: courseId={}, reason={}",
                    grant.getCourseId(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 冻结与恢复（ORDER-04）
    // ------------------------------------------------------------------

    /**
     * 冻结：台账仍记 {@code MATERIALIZED} 但打上挂起标记，三课堂侧听课记录置为失效。
     *
     * <p>保留台账是为了恢复时知道原来授权过什么；挂起标记让这些行不再计入引用计数，
     * 于是别的单撤销时不会因为「还有人引用」而误留听课记录。</p>
     */
    public int suspendOrderGrants(EduCourseOrderPo order) {
        int affected = 0;
        for (EduCourseOrderGrantPo grant : ledger.findByOrderAndStatus(order.getId(), GrantStatus.MATERIALIZED)) {
            if (grant.isSuspended()) {
                continue;
            }
            ledger.markSuspended(grant.getId(), true);
            long remaining = ledger.countActiveReferences(
                    grant.getCourseId(), grant.getListenClassId(), grant.getOrderId());
            if (remaining == 0) {
                try {
                    gateway.revokeAttend(grant.getCourseId(), String.valueOf(grant.getListenClassId()));
                } catch (LegacyClassroomException ex) {
                    log.warn("冻结时置听课记录失效失败，等待对账补偿: courseId={}, reason={}",
                            grant.getCourseId(), ex.getMessage());
                }
            }
            affected++;
        }
        return affected;
    }

    /**
     * 恢复：重放候选集，既把挂起的记录放回来，也补齐冻结期间新增的课程。
     */
    public SyncResult resumeOrderGrants(EduCourseOrderPo order) {
        for (EduCourseOrderGrantPo grant : ledger.findByOrderAndStatus(order.getId(), GrantStatus.MATERIALIZED)) {
            if (grant.isSuspended()) {
                // 清掉挂起标记但保持 MATERIALIZED，随后的 syncOrder 会发现 attendId 还在、
                // 三课堂那边却是失效状态，由对账补回；这里直接重新物化更快。
                ledger.markSuspended(grant.getId(), false);
                ledger.markFailed(grant.getId(), attemptCount(grant), "冻结恢复待重新物化", true);
            }
        }
        return syncOrder(order);
    }

    // ------------------------------------------------------------------
    // 取消（ORDER-05）
    // ------------------------------------------------------------------

    /**
     * 取消订购单时处置台账。
     *
     * <p>默认口径（《课程订购关系管理说明》§7 第 5 项待业务方确认，本实现取文档默认值）：
     * <b>未开始的课程一律撤销，已结束的课程保留回放</b>。
     * 若将来业务改成「取消即收回回放」，把 {@code keepFinishedPlayback} 传 false 即可。</p>
     */
    public int revokeOrderGrants(EduCourseOrderPo order, LocalDateTime now, boolean keepFinishedPlayback) {
        int revoked = 0;
        for (EduCourseOrderGrantPo grant : ledger.findByOrder(order.getId())) {
            if (grant.status() == GrantStatus.REVOKED) {
                continue;
            }
            boolean finished = grant.getCourseBeginTime() != null && grant.getCourseBeginTime().isBefore(now);
            if (keepFinishedPlayback && finished) {
                continue;
            }
            revokeGrant(grant);
            revoked++;
        }
        return revoked;
    }

    // ------------------------------------------------------------------
    // 重试（ORDER-09）
    // ------------------------------------------------------------------

    /**
     * 扫描失败台账重试，指数退避，超过阈值的转人工不再自动重试。
     */
    public SyncResult retryFailed(int maxAttempts, int batchSize, LocalDateTime now) {
        List<EduCourseOrderGrantPo> failures = grantMapper.selectList(
                new LambdaQueryWrapper<EduCourseOrderGrantPo>()
                        .eq(EduCourseOrderGrantPo::getGrantStatus, GrantStatus.FAILED.name())
                        .lt(EduCourseOrderGrantPo::getAttemptCount, maxAttempts)
                        .orderByAsc(EduCourseOrderGrantPo::getLastAttemptTime)
                        .last("limit " + Math.max(1, batchSize)));

        SyncResult result = SyncResult.empty();
        Map<Long, EduCourseOrderPo> orderCache = new LinkedHashMap<>();
        Map<Long, AttendContext> contextCache = new LinkedHashMap<>();

        for (EduCourseOrderGrantPo grant : failures) {
            if (!backoffElapsed(grant, now)) {
                continue;
            }
            EduCourseOrderPo order = orderCache.computeIfAbsent(
                    grant.getOrderId(), orderMapper::selectById);
            if (order == null || order.orderStatus() == null || !order.orderStatus().isGranting()) {
                continue;
            }
            LegacyCourse course;
            try {
                course = gateway.findCourse(grant.getCourseId());
            } catch (LegacyClassroomException ex) {
                ledger.markFailed(grant.getId(), attemptCount(grant), ex.getMessage(), ex.isRetryable());
                result = result.plus(new SyncResult(0, 0, 1, 0));
                continue;
            }
            if (course == null) {
                // 课程已经不在了，重试多少次都不会好，直接撤销台账收尾。
                revokeGrant(grant);
                result = result.plus(new SyncResult(0, 0, 0, 1));
                continue;
            }
            AttendContext context = contextCache.computeIfAbsent(order.getId(), key -> attendContext(order));
            result = result.plus(materializeOne(order, course, context));
        }
        return result;
    }

    /**
     * 指数退避：第 n 次失败后至少等 2^n 分钟，上限一小时。
     */
    private static boolean backoffElapsed(EduCourseOrderGrantPo grant, LocalDateTime now) {
        if (grant.getLastAttemptTime() == null) {
            return true;
        }
        int attempts = attemptCount(grant);
        long minutes = Math.min(1L << Math.min(attempts, 16), MAX_BACKOFF.toMinutes());
        return !grant.getLastAttemptTime().plusMinutes(minutes).isAfter(now);
    }

    // ------------------------------------------------------------------
    // 对账（§6.2）
    // ------------------------------------------------------------------

    /**
     * 对账结果：三类差异各自的修复条数。
     *
     * @param supplemented 应授权未物化 → 补物化
     * @param withdrawn    已物化不应授权 → 按引用计数撤销
     * @param repaired     台账记已物化但三课堂查无对应正常行 → 重新物化
     */
    public record ReconcileResult(int supplemented, int withdrawn, int repaired) {
    }

    /**
     * 对单张订购单做一次全量比对。
     */
    public ReconcileResult reconcileOrder(EduCourseOrderPo order) {
        OrderStatus status = order.orderStatus();
        if (status == null || !status.isGranting()) {
            return new ReconcileResult(0, 0, 0);
        }
        SyncResult sync = syncOrder(order);

        int repaired = 0;
        AttendContext context = attendContext(order);
        for (EduCourseOrderGrantPo grant : ledger.findByOrderAndStatus(order.getId(), GrantStatus.MATERIALIZED)) {
            if (grant.isSuspended()) {
                continue;
            }
            try {
                if (gateway.isAttendActive(grant.getCourseId(), String.valueOf(grant.getListenClassId()))) {
                    continue;
                }
                // 教师在旧前端改课会走 deleteByCourseId 把整门课的听课行清掉，
                // 连带 Han 写的那行；这里把它补回来。
                LegacyCourse course = gateway.findCourse(grant.getCourseId());
                if (course == null) {
                    revokeGrant(grant);
                    continue;
                }
                String attendId = gateway.materializeAttend(context.toRequest(course));
                ledger.markMaterialized(grant.getId(), attendId);
                repaired++;
            } catch (LegacyClassroomException ex) {
                ledger.markFailed(grant.getId(), attemptCount(grant), ex.getMessage(), ex.isRetryable());
            }
        }
        return new ReconcileResult(sync.materialized(), sync.revoked(), repaired);
    }

    // ------------------------------------------------------------------
    // 物化上下文
    // ------------------------------------------------------------------

    /**
     * 一张订购单物化时用到的全部快照值，按单算一次，避免每节课都回查主数据。
     */
    record AttendContext(
            String organId,
            String organName,
            String classId,
            String className,
            String provinceCode,
            String cityCode,
            String countyCode,
            String placeId,
            String placeName,
            String memberId,
            String memberName,
            Map<String, Long> subjectIdByCode) {

        LegacyAttendRequest toRequest(LegacyCourse course) {
            return new LegacyAttendRequest(
                    course.courseId(),
                    course.courseType(),
                    organId, organName,
                    classId, className,
                    provinceCode, "",
                    cityCode, "",
                    countyCode, "",
                    placeId, placeName,
                    // room_id 是旧系统在开课时分配的运行时房间，属于它自己的概念，Han 不能编造。
                    "", "",
                    memberId, memberName);
        }
    }

    AttendContext attendContext(EduCourseOrderPo order) {
        EduClassPo listenClass = classMapper.selectById(order.getListenClassId());
        EduSchoolPo listenSchool = schoolMapper.selectById(order.getListenSchoolId());
        EduRoomPo room = order.getListenRoomId() == null ? null : roomMapper.selectById(order.getListenRoomId());
        EduDevicePo device = order.getListenDeviceId() == null
                ? null : deviceMapper.selectById(order.getListenDeviceId());
        String areaCode = listenSchool == null ? null : listenSchool.getAreaCode();

        return new AttendContext(
                String.valueOf(order.getListenSchoolId()),
                listenSchool == null ? "" : nullToEmpty(listenSchool.getSchoolName()),
                String.valueOf(order.getListenClassId()),
                listenClass == null ? "" : nullToEmpty(listenClass.getClassName()),
                areaSegment(areaCode, 2),
                areaSegment(areaCode, 4),
                areaSegment(areaCode, 6),
                room == null ? "" : String.valueOf(room.getId()),
                room == null ? "" : nullToEmpty(room.getRoomName()),
                // 旧系统「加入课堂」按 (fk_course_id, member_id) 反查听课行，
                // 这一列存的是设备编码而不是人。
                device == null ? "" : nullToEmpty(device.getDeviceCode()),
                device == null ? "" : nullToEmpty(device.getDeviceName()),
                subjectCodeIndex());
    }

    /**
     * 由学校区划码推出省/市/县三级编码。Han 没有区划名称表，名称留空，旧系统只用它们做展示与筛选。
     */
    private static String areaSegment(String areaCode, int significantDigits) {
        if (areaCode == null || areaCode.length() < 6) {
            return "";
        }
        return areaCode.substring(0, significantDigits) + "0".repeat(6 - significantDigits);
    }

    private Map<String, Long> subjectCodeIndex() {
        Map<String, Long> index = new LinkedHashMap<>();
        for (EduSubjectPo subject : subjectMapper.selectList(new LambdaQueryWrapper<>())) {
            if (subject.getSubjectCode() != null) {
                index.put(subject.getSubjectCode(), subject.getId());
            }
        }
        return index;
    }

    /**
     * 订购明细里的 Han 科目 ID 换成三课堂课程上的科目编码。
     */
    Set<String> subscribedSubjectCodes(Long orderId) {
        List<EduCourseOrderSubjectPo> details = orderSubjectMapper.selectList(
                new LambdaQueryWrapper<EduCourseOrderSubjectPo>()
                        .eq(EduCourseOrderSubjectPo::getOrderId, orderId));
        if (details.isEmpty()) {
            return Set.of();
        }
        Set<Long> subjectIds = new HashSet<>();
        for (EduCourseOrderSubjectPo detail : details) {
            subjectIds.add(detail.getSubjectId());
        }
        return subjectCodes(subjectIds);
    }

    private Set<String> subjectCodes(Collection<Long> subjectIds) {
        if (subjectIds.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (EduSubjectPo subject : subjectMapper.selectBatchIds(subjectIds)) {
            if (subject.getSubjectCode() != null) {
                codes.add(subject.getSubjectCode());
            }
        }
        return codes;
    }

    private static int attemptCount(EduCourseOrderGrantPo grant) {
        return Objects.requireNonNullElse(grant.getAttemptCount(), 0);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
