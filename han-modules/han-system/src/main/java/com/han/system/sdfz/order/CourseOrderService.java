package com.han.system.sdfz.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.SemesterLifecycle;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.sdfz.order.domain.CourseOrderForms;
import com.han.system.sdfz.order.domain.EduCourseOrderGrantPo;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import com.han.system.sdfz.order.domain.EduCourseOrderSubjectPo;
import com.han.system.sdfz.order.domain.GrantScope;
import com.han.system.sdfz.order.domain.GrantStatus;
import com.han.system.sdfz.order.domain.OrderStatus;
import com.han.system.sdfz.order.mapper.EduCourseOrderGrantMapper;
import com.han.system.sdfz.order.mapper.EduCourseOrderMapper;
import com.han.system.sdfz.order.mapper.EduCourseOrderSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 订购单的增删改查与状态流转。
 *
 * <p>授权计算与三课堂物化在 {@link CourseGrantService}；这里只负责 Han 侧的单据本身。
 * 两者分开是因为事务边界不同：单据写入必须原子（单据和科目明细不能只落一半），
 * 物化则明确不能包在事务里。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseOrderService {

    /** 状态推进一律按东八区判定，避免容器时区不同导致边界日不一致。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final String LOCAL_SOURCE = "HAN";
    private static final String CONFLICT = "409";
    private static final String ATTEND_ROLE = "ATTEND";
    private static final String MAIN_ROLE = "MAIN";

    private final EduCourseOrderMapper orderMapper;
    private final EduCourseOrderSubjectMapper orderSubjectMapper;
    private final EduCourseOrderGrantMapper grantMapper;
    private final EduClassMapper classMapper;
    private final EduRoomMapper roomMapper;
    private final EduDeviceMapper deviceMapper;
    private final EduSemesterMapper semesterMapper;
    private final EduSubjectMapper subjectMapper;
    private final CourseGrantService grantService;

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public PageResult<EduCourseOrderPo> listOrders(Long listenSchoolId, Long listenClassId, Long lectureClassId,
                                                   Long semesterId, String status, int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduCourseOrderPo> query = new LambdaQueryWrapper<EduCourseOrderPo>()
                .eq(listenSchoolId != null, EduCourseOrderPo::getListenSchoolId, listenSchoolId)
                .eq(listenClassId != null, EduCourseOrderPo::getListenClassId, listenClassId)
                .eq(lectureClassId != null, EduCourseOrderPo::getLectureClassId, lectureClassId)
                .eq(semesterId != null, EduCourseOrderPo::getSemesterId, semesterId)
                .eq(notBlank(status), EduCourseOrderPo::getStatus, status)
                .orderByDesc(EduCourseOrderPo::getCreateTime);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<EduCourseOrderPo> result = orderMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), safePage, safeSize);
    }

    public EduCourseOrderPo requireOrder(Long id) {
        requireTenant();
        EduCourseOrderPo order = id == null ? null : orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订购单不存在或不在当前数据范围");
        }
        return order;
    }

    public List<Long> subjectIdsOf(Long orderId) {
        List<Long> ids = new ArrayList<>();
        for (EduCourseOrderSubjectPo detail : orderSubjectMapper.selectList(
                new LambdaQueryWrapper<EduCourseOrderSubjectPo>()
                        .eq(EduCourseOrderSubjectPo::getOrderId, orderId))) {
            ids.add(detail.getSubjectId());
        }
        return ids;
    }

    public PageResult<EduCourseOrderGrantPo> listGrants(Long orderId, String grantStatus,
                                                        int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduCourseOrderGrantPo> query = new LambdaQueryWrapper<EduCourseOrderGrantPo>()
                .eq(orderId != null, EduCourseOrderGrantPo::getOrderId, orderId)
                .eq(notBlank(grantStatus), EduCourseOrderGrantPo::getGrantStatus, grantStatus)
                .orderByDesc(EduCourseOrderGrantPo::getUpdateTime)
                .orderByDesc(EduCourseOrderGrantPo::getId);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<EduCourseOrderGrantPo> result = grantMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), safePage, safeSize);
    }

    // ------------------------------------------------------------------
    // 创建（ORDER-01 / ORDER-02）
    // ------------------------------------------------------------------

    /**
     * 创建订购单。
     *
     * <p>ORDER-02 的两种口径都支持：带了 {@code orderNo} 且已存在 → 幂等返回原单；
     * 没带单号但四元组撞上有效单 → 409 拒绝，错误里带上冲突单号。</p>
     *
     * <p>四元组唯一性由生成列 {@code active_flag} 上的唯一索引保证，
     * 下面的预检只是为了给出可读的错误信息，真正兜底并发的是数据库。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public EduCourseOrderPo createOrder(CourseOrderForms.CreateOrder form) {
        Long tenantId = requireTenant();

        if (notBlank(form.orderNo())) {
            EduCourseOrderPo existing = findByOrderNo(form.orderNo().trim());
            if (existing != null) {
                return existing;
            }
        }

        GrantScope scope = requireScope(form.grantScope());
        List<Long> subjectIds = normalizeSubjects(scope, form.subjectIds());

        EduClassPo listenClass = requireClass(form.listenClassId(), "听讲班");
        EduClassPo lectureClass = requireClass(form.lectureClassId(), "主讲班");
        if (Objects.equals(form.listenClassId(), form.lectureClassId())) {
            throw new BusinessException("听讲班与主讲班不能是同一个班级");
        }
        if (!ATTEND_ROLE.equalsIgnoreCase(listenClass.getClassRole())) {
            throw new BusinessException("听讲班的班级角色必须是 ATTEND");
        }
        if (!MAIN_ROLE.equalsIgnoreCase(lectureClass.getClassRole())) {
            throw new BusinessException("主讲班的班级角色必须是 MAIN");
        }

        EduSemesterPo semester = requireSemester(form.semesterId());
        if (SemesterLifecycle.FINISHED.name().equals(semester.getLifecycleStatus())) {
            throw new BusinessException("学期已结束，不能新建订购单");
        }
        requireOrderTopology(listenClass, lectureClass, semester, form.listenRoomId(), form.listenDeviceId());

        EduCourseOrderPo conflict = findActiveOrder(
                form.listenClassId(), form.lectureClassId(), form.semesterId());
        if (conflict != null) {
            throw conflictException(conflict, scope);
        }

        boolean draft = Boolean.TRUE.equals(form.draft());
        LocalDateTime effective = semester.getBeginDate().atStartOfDay();
        LocalDateTime expire = semester.getEndDate().atTime(LocalTime.of(23, 59, 59));

        EduCourseOrderPo order = new EduCourseOrderPo();
        order.setTenantId(tenantId);
        order.setOrderNo(notBlank(form.orderNo()) ? form.orderNo().trim() : generateOrderNo());
        order.setListenSchoolId(listenClass.getSchoolId());
        order.setListenClassId(form.listenClassId());
        order.setListenRoomId(form.listenRoomId());
        order.setListenDeviceId(form.listenDeviceId());
        order.setLectureSchoolId(lectureClass.getSchoolId());
        order.setLectureClassId(form.lectureClassId());
        order.setSemesterId(form.semesterId());
        order.setGrantScope(scope.name());
        order.setEffectiveTime(effective);
        order.setExpireTime(expire);
        order.setSourceSystem(LOCAL_SOURCE);
        order.setRemark(trimToNull(form.remark()));
        order.setStatus(draft ? OrderStatus.DRAFT.name() : resolveStatus(effective, expire, now()).name());

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            // 并发下预检可能漏掉：带单号的按幂等返回，没带单号的按冲突拒绝。
            if (notBlank(form.orderNo())) {
                EduCourseOrderPo existing = findByOrderNo(form.orderNo().trim());
                if (existing != null) {
                    return existing;
                }
            }
            EduCourseOrderPo raced = findActiveOrder(
                    form.listenClassId(), form.lectureClassId(), form.semesterId());
            throw raced != null ? conflictException(raced, scope)
                    : new BusinessException(CONFLICT, "ORDER_DUPLICATED: 订购单已存在");
        }
        replaceSubjects(order, subjectIds);
        return order;
    }

    /**
     * 冲突时区分两种错误码。
     *
     * <p>已有整班单再要按科目单，是「多此一举」而不是「重复」：整班已经覆盖了全部科目，
     * 给不同的码是为了让调用方知道该走升级还是该改单号。</p>
     */
    private static BusinessException conflictException(EduCourseOrderPo conflict, GrantScope requested) {
        if (conflict.scope() == GrantScope.WHOLE_CLASS && requested == GrantScope.BY_SUBJECT) {
            return new BusinessException(CONFLICT,
                    "ORDER_SCOPE_REDUNDANT: 已存在整班打包订购单 " + conflict.getOrderNo() + "，无需再按科目订购");
        }
        return new BusinessException(CONFLICT,
                "ORDER_DUPLICATED: 已存在有效订购单 " + conflict.getOrderNo());
    }

    // ------------------------------------------------------------------
    // 改粒度与科目明细（ORDER-07 / M2）
    // ------------------------------------------------------------------

    /**
     * 调整粒度与科目明细，然后按新的候选集做增量重算。
     *
     * <p>按科目升级成整班打包时<b>不新建单</b>，就地改这一张：既避免两张单，也保住单号与审计连续性。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public EduCourseOrderPo updateScope(CourseOrderForms.UpdateScope form) {
        EduCourseOrderPo order = requireOrder(form.id());
        OrderStatus status = order.orderStatus();
        if (status == null || status.isTerminal()) {
            throw new BusinessException("已过期或已取消的订购单不能改粒度");
        }
        GrantScope scope = requireScope(form.grantScope());
        List<Long> subjectIds = normalizeSubjects(scope, form.subjectIds());

        EduCourseOrderPo update = new EduCourseOrderPo();
        update.setId(order.getId());
        update.setGrantScope(scope.name());
        orderMapper.updateById(update);
        order.setGrantScope(scope.name());

        replaceSubjects(order, subjectIds);
        return order;
    }

    /**
     * 全量替换科目明细。整班打包的单子明细必须为空。
     *
     * <p>先全部置为已删除，再把这一轮要保留的复活；复活不到才新插。
     * 不能「删掉再全插」：{@code del_flag} 是逻辑删除，被移除的行还留在表里占着唯一键
     * {@code (tenant_id, order_id, subject_id)}，重新加回同一个科目会直接撞唯一约束。</p>
     */
    private void replaceSubjects(EduCourseOrderPo order, List<Long> subjectIds) {
        orderSubjectMapper.deactivateByOrder(order.getId());
        for (Long subjectId : subjectIds) {
            if (orderSubjectMapper.reactivate(order.getId(), subjectId) > 0) {
                continue;
            }
            EduCourseOrderSubjectPo detail = new EduCourseOrderSubjectPo();
            detail.setTenantId(order.getTenantId());
            detail.setOrderId(order.getId());
            detail.setSubjectId(subjectId);
            orderSubjectMapper.insert(detail);
        }
    }

    // ------------------------------------------------------------------
    // 状态流转
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public EduCourseOrderPo submit(Long id) {
        EduCourseOrderPo order = requireOrder(id);
        if (order.orderStatus() != OrderStatus.DRAFT) {
            throw new BusinessException("只有草稿状态的订购单可以提交");
        }
        EduCourseOrderPo conflict = findActiveOrder(
                order.getListenClassId(), order.getLectureClassId(), order.getSemesterId());
        if (conflict != null && !conflict.getId().equals(order.getId())) {
            throw conflictException(conflict, order.scope());
        }
        OrderStatus target = resolveStatus(order.getEffectiveTime(), order.getExpireTime(), now());
        return applyStatus(order, target, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public EduCourseOrderPo freeze(Long id, String reason) {
        EduCourseOrderPo order = requireOrder(id);
        if (order.orderStatus() != OrderStatus.ACTIVE) {
            throw new BusinessException("只有生效中的订购单可以冻结");
        }
        return applyStatus(order, OrderStatus.FROZEN, trimToNull(reason), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public EduCourseOrderPo unfreeze(Long id) {
        EduCourseOrderPo order = requireOrder(id);
        if (order.orderStatus() != OrderStatus.FROZEN) {
            throw new BusinessException("只有已冻结的订购单可以恢复");
        }
        OrderStatus target = resolveStatus(order.getEffectiveTime(), order.getExpireTime(), now());
        return applyStatus(order, target, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public EduCourseOrderPo cancel(Long id, String reason) {
        EduCourseOrderPo order = requireOrder(id);
        OrderStatus status = order.orderStatus();
        if (status == null || status.isTerminal()) {
            throw new BusinessException("订购单已处于终态，不能重复取消");
        }
        return applyStatus(order, OrderStatus.CANCELLED, null, trimToNull(reason));
    }

    private EduCourseOrderPo applyStatus(EduCourseOrderPo order, OrderStatus target,
                                         String freezeReason, String cancelReason) {
        EduCourseOrderPo update = new EduCourseOrderPo();
        update.setId(order.getId());
        update.setStatus(target.name());
        update.setFreezeReason(freezeReason);
        update.setCancelReason(cancelReason);
        orderMapper.updateById(update);
        order.setStatus(target.name());
        if (freezeReason != null) {
            order.setFreezeReason(freezeReason);
        }
        if (cancelReason != null) {
            order.setCancelReason(cancelReason);
        }
        return order;
    }

    /**
     * 按日期推进 {@code PENDING→ACTIVE} 与 {@code ACTIVE→EXPIRED}，供定时任务调用。
     *
     * <p>边界是闭区间：{@code begin_date} 当天 00:00:00 生效，{@code end_date} 当天 23:59:59 失效。
     * 幂等，重复执行不会重复变更。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int advanceStatuses(LocalDateTime now) {
        List<EduCourseOrderPo> candidates = orderMapper.selectList(new LambdaQueryWrapper<EduCourseOrderPo>()
                .in(EduCourseOrderPo::getStatus, OrderStatus.PENDING.name(), OrderStatus.ACTIVE.name()));
        int changed = 0;
        for (EduCourseOrderPo order : candidates) {
            OrderStatus target = resolveStatus(order.getEffectiveTime(), order.getExpireTime(), now);
            if (target == order.orderStatus()) {
                continue;
            }
            // 只走「向前」的两步。人工冻结/取消不在这里处理，
            // 定时任务把 FROZEN 拉回 ACTIVE 会把运维的冻结操作悄悄撤销掉。
            if (!(order.orderStatus() == OrderStatus.PENDING && target == OrderStatus.ACTIVE)
                    && !(order.orderStatus() == OrderStatus.ACTIVE && target == OrderStatus.EXPIRED)) {
                continue;
            }
            EduCourseOrderPo update = new EduCourseOrderPo();
            update.setId(order.getId());
            update.setStatus(target.name());
            orderMapper.updateById(update);
            changed++;
        }
        return changed;
    }

    /**
     * 按时间推导单据应处的状态。
     */
    public static OrderStatus resolveStatus(LocalDateTime effective, LocalDateTime expire, LocalDateTime now) {
        if (effective != null && now.isBefore(effective)) {
            return OrderStatus.PENDING;
        }
        if (expire != null && now.isAfter(expire)) {
            return OrderStatus.EXPIRED;
        }
        return OrderStatus.ACTIVE;
    }

    // ------------------------------------------------------------------
    // 与授权物化的衔接
    // ------------------------------------------------------------------

    /**
     * 冻结并挂起授权。单据状态变更走事务，物化侧的动作在事务之外，二者不共享回滚。
     */
    public EduCourseOrderPo freezeAndSuspend(Long id, String reason) {
        EduCourseOrderPo order = freeze(id, reason);
        grantService.suspendOrderGrants(order);
        return order;
    }

    public CourseGrantService.SyncResult unfreezeAndResume(Long id) {
        EduCourseOrderPo order = unfreeze(id);
        return grantService.resumeOrderGrants(order);
    }

    public EduCourseOrderPo cancelAndRevoke(Long id, String reason, boolean keepFinishedPlayback) {
        EduCourseOrderPo order = cancel(id, reason);
        grantService.revokeOrderGrants(order, now(), keepFinishedPlayback);
        return order;
    }

    public CourseGrantService.SyncResult syncGrants(Long id) {
        return grantService.syncOrder(requireOrder(id));
    }

    /**
     * 手动重试单条失败台账。
     */
    public CourseGrantService.SyncResult retryGrant(Long grantId) {
        requireTenant();
        EduCourseOrderGrantPo grant = grantId == null ? null : grantMapper.selectById(grantId);
        if (grant == null) {
            throw new BusinessException("授权台账不存在或不在当前数据范围");
        }
        if (grant.status() != GrantStatus.FAILED) {
            throw new BusinessException("只有失败状态的台账可以手动重试");
        }
        // 清掉上次尝试时间，绕过退避窗口直接重试一次。
        EduCourseOrderGrantPo reset = new EduCourseOrderGrantPo();
        reset.setId(grant.getId());
        reset.setAttemptCount(0);
        grantMapper.updateById(reset);
        return grantService.retryFailed(Integer.MAX_VALUE, 1, now());
    }

    // ------------------------------------------------------------------
    // 校验辅助
    // ------------------------------------------------------------------

    private EduCourseOrderPo findByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<EduCourseOrderPo>()
                .eq(EduCourseOrderPo::getOrderNo, orderNo)
                .last("limit 1"));
    }

    EduCourseOrderPo findActiveOrder(Long listenClassId, Long lectureClassId, Long semesterId) {
        List<String> occupying = new ArrayList<>();
        for (OrderStatus status : OrderStatus.OCCUPYING) {
            occupying.add(status.name());
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<EduCourseOrderPo>()
                .eq(EduCourseOrderPo::getListenClassId, listenClassId)
                .eq(EduCourseOrderPo::getLectureClassId, lectureClassId)
                .eq(EduCourseOrderPo::getSemesterId, semesterId)
                .in(EduCourseOrderPo::getStatus, occupying)
                .last("limit 1"));
    }

    private static GrantScope requireScope(String value) {
        GrantScope scope = GrantScope.parse(value);
        if (scope == null) {
            throw new BusinessException("授权粒度只能是 WHOLE_CLASS 或 BY_SUBJECT");
        }
        return scope;
    }

    private List<Long> normalizeSubjects(GrantScope scope, List<Long> subjectIds) {
        Set<Long> unique = new LinkedHashSet<>();
        if (subjectIds != null) {
            for (Long subjectId : subjectIds) {
                if (subjectId != null) {
                    unique.add(subjectId);
                }
            }
        }
        if (scope == GrantScope.WHOLE_CLASS) {
            if (!unique.isEmpty()) {
                throw new BusinessException("整班打包的订购单不能带科目明细");
            }
            return List.of();
        }
        if (unique.isEmpty()) {
            throw new BusinessException("按科目订购至少要选一个科目");
        }
        List<EduSubjectPo> found = subjectMapper.selectBatchIds(unique);
        if (found.size() != unique.size()) {
            throw new BusinessException("科目不存在或不在当前数据范围");
        }
        return List.copyOf(unique);
    }

    private EduClassPo requireClass(Long id, String name) {
        EduClassPo value = id == null ? null : classMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(name + "不存在或不在当前数据范围");
        }
        // 迁移前存量为 null，仍按既有平面班级兼容；迁移后只允许 CLASS 叶子参与课堂。
        if (value.getNodeType() != null && !"CLASS".equals(value.getNodeType())) {
            throw new BusinessException(name + "必须选择实际班级叶子节点");
        }
        return value;
    }

    private EduSemesterPo requireSemester(Long id) {
        EduSemesterPo value = id == null ? null : semesterMapper.selectById(id);
        if (value == null) {
            throw new BusinessException("学期不存在或不在当前数据范围");
        }
        return value;
    }

    /** 在创建单据时校验课堂参与对象，避免错误数据等到授权物化时才暴露。 */
    private void requireOrderTopology(EduClassPo listenClass, EduClassPo lectureClass, EduSemesterPo semester,
                                      Long listenRoomId, Long listenDeviceId) {
        Long semesterYearId = semester.getAcademicYearId();
        requireAcademicYear(listenClass, semesterYearId, "听讲班");
        requireAcademicYear(lectureClass, semesterYearId, "主讲班");
        EduRoomPo room = listenRoomId == null ? null : roomMapper.selectById(listenRoomId);
        if (room != null && !Objects.equals(room.getSchoolId(), listenClass.getSchoolId())) {
            throw new BusinessException("听讲场所必须属于听讲学校");
        }
        if (room != null && room.getNodeType() != null && !"PLACE".equals(room.getNodeType())) {
            throw new BusinessException("听讲场所必须选择实际场所叶子节点");
        }
        if (listenRoomId != null && room == null) {
            throw new BusinessException("听讲场所不存在或不在当前数据范围");
        }
        EduDevicePo device = listenDeviceId == null ? null : deviceMapper.selectById(listenDeviceId);
        if (listenDeviceId != null && device == null) {
            throw new BusinessException("听讲设备不存在或不在当前数据范围");
        }
        if (device != null && !Objects.equals(device.getSchoolId(), listenClass.getSchoolId())) {
            throw new BusinessException("听讲设备必须属于听讲学校");
        }
        if (device != null && room != null && !Objects.equals(device.getRoomId(), room.getId())) {
            throw new BusinessException("听讲设备必须挂在所选听讲场所");
        }
    }

    private static void requireAcademicYear(EduClassPo clazz, Long semesterYearId, String name) {
        if (semesterYearId != null && clazz.getAcademicYearId() != null
                && !Objects.equals(semesterYearId, clazz.getAcademicYearId())) {
            throw new BusinessException(name + "与订购学期不属于同一学年");
        }
    }

    private static String generateOrderNo() {
        return "CO" + now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr().substring(12);
    }

    private static Long requireTenant() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("缺少租户上下文");
        }
        return tenantId;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }
}
