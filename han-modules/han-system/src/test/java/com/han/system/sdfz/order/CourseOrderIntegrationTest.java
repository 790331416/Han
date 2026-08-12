package com.han.system.sdfz.order;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.EducationCalendarService;
import com.han.system.sdfz.education.domain.SemesterLifecycle;
import com.han.system.sdfz.order.domain.CourseOrderForms;
import com.han.system.sdfz.order.domain.EduCourseOrderGrantPo;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import com.han.system.sdfz.order.domain.GrantStatus;
import com.han.system.sdfz.order.domain.OrderStatus;
import com.han.system.sdfz.order.support.FaultInjectingClassroomGateway;
import com.han.system.sdfz.order.support.OrderIntegrationTestConfig;
import com.han.system.sdfz.order.support.OrderTestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订购与授权物化的集成测试。
 *
 * <p>这些断言必须跑在真实事务管理器上：事务原子性、唯一约束、幂等、引用计数撤销，
 * 任何一条用 Mockito 打桩都只是在验证「我调了哪些方法」，验证不了数据库真实行为。
 * 上下文见 {@link OrderIntegrationTestConfig}。</p>
 */
@SpringJUnitConfig(OrderIntegrationTestConfig.class)
@DisplayName("课程订购与授权物化（真实事务）")
class CourseOrderIntegrationTest {

    // 学期窗口按当天推算，保证订购单落在 ACTIVE 上。
    // 写死日期的话，测试会在某个真实日期之后开始失败，而失败原因跟被测逻辑无关。
    private static final LocalDate SEMESTER_BEGIN = LocalDate.now(CourseOrderService.ZONE).minusMonths(1);
    private static final LocalDate SEMESTER_END = LocalDate.now(CourseOrderService.ZONE).plusMonths(4);
    private static final long SEMESTER = 6001L;
    private static final LocalDateTime IN_SEMESTER = LocalDateTime.now(CourseOrderService.ZONE).minusDays(1);

    @Autowired
    private CourseOrderService orderService;
    @Autowired
    private CourseGrantService grantService;
    @Autowired
    private CourseGrantLedger ledger;
    @Autowired
    private EducationCalendarService calendarService;
    @Autowired
    private OrderTestFixtures fixtures;
    @Autowired
    private FaultInjectingClassroomGateway gateway;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(
                LoginUser.builder().userId(9L).tenantId(OrderTestFixtures.TENANT).build());
        fixtures.reset();
        gateway.reset();
        fixtures.seedMasterData();
        fixtures.seedSemester(SEMESTER, "2026-2027-1", SEMESTER_BEGIN, SEMESTER_END,
                SemesterLifecycle.IN_PROGRESS.name());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ------------------------------------------------------------------
    // ORDER-01 / ORDER-02
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ORDER-01 创建订购单，生效时间取学期起止的闭区间")
    void createsOrderWithSemesterWindow() {
        EduCourseOrderPo order = createWholeClassOrder(null);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getListenSchoolId()).isEqualTo(OrderTestFixtures.LISTEN_SCHOOL);
        assertThat(order.getLectureSchoolId()).isEqualTo(OrderTestFixtures.LECTURE_SCHOOL);
        assertThat(order.getEffectiveTime()).isEqualTo(SEMESTER_BEGIN.atStartOfDay());
        assertThat(order.getExpireTime()).isEqualTo(SEMESTER_END.atTime(23, 59, 59));
        assertThat(fixtures.countOrders()).isEqualTo(1);
    }

    @Test
    @DisplayName("ORDER-01 听讲班角色不是 ATTEND 时拒绝")
    void rejectsWrongClassRole() {
        CourseOrderForms.CreateOrder form = new CourseOrderForms.CreateOrder(
                null, OrderTestFixtures.LECTURE_CLASS, null, null, OrderTestFixtures.LECTURE_CLASS,
                SEMESTER, "WHOLE_CLASS", null, false, null);

        assertThatThrownBy(() -> orderService.createOrder(form))
                .isInstanceOf(BusinessException.class);
        assertThat(fixtures.countOrders()).isZero();
    }

    @Test
    @DisplayName("ORDER-02 同一四元组第二张有效单被唯一约束拒绝，错误里带冲突单号")
    void rejectsDuplicateActiveOrder() {
        EduCourseOrderPo first = createWholeClassOrder("ORD-1");

        CourseOrderForms.CreateOrder second = new CourseOrderForms.CreateOrder(
                null, OrderTestFixtures.LISTEN_CLASS, null, null, OrderTestFixtures.LECTURE_CLASS,
                SEMESTER, "WHOLE_CLASS", null, false, null);

        assertThatThrownBy(() -> orderService.createOrder(second))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ORDER_DUPLICATED")
                .hasMessageContaining(first.getOrderNo());
        assertThat(fixtures.countOrders()).isEqualTo(1);
    }

    @Test
    @DisplayName("ORDER-02 携带相同单号时幂等返回原单，不新增")
    void returnsSameOrderForRepeatedOrderNo() {
        EduCourseOrderPo first = createWholeClassOrder("ORD-IDEMPOTENT");
        EduCourseOrderPo again = createWholeClassOrder("ORD-IDEMPOTENT");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(fixtures.countOrders()).isEqualTo(1);
    }

    @Test
    @DisplayName("ORDER-02 已有整班单再要按科目单，报的是 SCOPE_REDUNDANT 而不是 DUPLICATED")
    void rejectsRedundantSubjectOrder() {
        createWholeClassOrder("ORD-WHOLE");

        CourseOrderForms.CreateOrder bySubject = new CourseOrderForms.CreateOrder(
                null, OrderTestFixtures.LISTEN_CLASS, null, null, OrderTestFixtures.LECTURE_CLASS,
                SEMESTER, "BY_SUBJECT", List.of(OrderTestFixtures.SUBJECT_CHINESE), false, null);

        assertThatThrownBy(() -> orderService.createOrder(bySubject))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ORDER_SCOPE_REDUNDANT");
    }

    @Test
    @DisplayName("取消后同一四元组可以再建新单：生成列让历史单不再占用槽位")
    void allowsNewOrderAfterCancel() {
        EduCourseOrderPo first = createWholeClassOrder("ORD-1");
        orderService.cancel(first.getId(), "业务调整");

        EduCourseOrderPo second = createWholeClassOrder("ORD-2");

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(fixtures.countOrders()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // 事务原子性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("科目明细写入失败时整张单回滚，不留下没有明细的孤儿单")
    void rollsBackOrderWhenSubjectDetailFails() {
        CourseOrderForms.CreateOrder form = new CourseOrderForms.CreateOrder(
                null, OrderTestFixtures.LISTEN_CLASS, null, null, OrderTestFixtures.LECTURE_CLASS,
                SEMESTER, "BY_SUBJECT", List.of(OrderTestFixtures.SUBJECT_CHINESE), false, null);

        // 把明细表的唯一键提前占掉，让 replaceSubjects 的 insert 撞主键。
        // createOrder 是 @Transactional 的，单据必须跟着一起回滚。
        long collidingId = 777001L;
        fixtures.han().update(
                "INSERT INTO edu_course_order_subject (id, tenant_id, order_id, subject_id) VALUES (?, ?, ?, ?)",
                collidingId, OrderTestFixtures.TENANT, 1L, OrderTestFixtures.SUBJECT_MATH);
        fixtures.han().update("ALTER TABLE edu_course_order_subject ADD CONSTRAINT tmp_subject_check"
                + " CHECK (subject_id <> " + OrderTestFixtures.SUBJECT_CHINESE + ")");

        try {
            assertThatThrownBy(() -> orderService.createOrder(form)).isInstanceOf(RuntimeException.class);
            assertThat(fixtures.countOrders())
                    .as("单据必须随明细一起回滚")
                    .isZero();
        } finally {
            fixtures.han().update("ALTER TABLE edu_course_order_subject DROP CONSTRAINT tmp_subject_check");
        }
    }

    @Test
    @DisplayName("批量物化不共享事务：一节课失败不会回滚已经成功的那几节")
    void partialFailureKeepsSuccessfulGrants() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        fixtures.seedCourse("C2", "语文第二课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER.plusDays(1));
        fixtures.seedCourse("C3", "数学第一课", OrderTestFixtures.LECTURE_CLASS, "SX", IN_SEMESTER.plusDays(2));
        EduCourseOrderPo order = createWholeClassOrder("ORD-PARTIAL");
        gateway.failCourse("C2");

        CourseGrantService.SyncResult result = grantService.syncOrder(order);

        assertThat(result.materialized()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(fixtures.countGrants(GrantStatus.MATERIALIZED.name())).isEqualTo(2);
        assertThat(fixtures.countGrants(GrantStatus.FAILED.name())).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C2", OrderTestFixtures.LISTEN_CLASS)).isZero();
        assertThat(fixtures.countActiveAttend("C3", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // ORDER-06 / ORDER-07 / ORDER-08
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ORDER-07 整班打包回溯授权学期内已有课程，听课记录字段按听讲侧填写")
    void materializesExistingCoursesForWholeClass() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        fixtures.seedCourse("C2", "数学第一课", OrderTestFixtures.LECTURE_CLASS, "SX", IN_SEMESTER.plusDays(1));
        EduCourseOrderPo order = createWholeClassOrder("ORD-WHOLE");

        CourseGrantService.SyncResult result = grantService.syncOrder(order);

        assertThat(result.materialized()).isEqualTo(2);
        Map<String, Object> attend = fixtures.attendRow("C1", OrderTestFixtures.LISTEN_CLASS);
        assertThat(attend).containsEntry("organ_id", String.valueOf(OrderTestFixtures.LISTEN_SCHOOL));
        assertThat(attend).containsEntry("class_id", String.valueOf(OrderTestFixtures.LISTEN_CLASS));
        assertThat(attend).containsEntry("class_name", "听讲一班");
        assertThat(attend).containsEntry("status", "0");
        assertThat(attend).containsEntry("province_code", "500000");
        assertThat(attend).containsEntry("city_code", "500100");
        assertThat(attend).containsEntry("county_code", "500103");
        assertThat(attend).containsEntry("place_id", String.valueOf(OrderTestFixtures.ROOM));
        assertThat(attend).containsEntry("member_id", "DEV-01");
    }

    @Test
    @DisplayName("按科目只授权明细里的科目；补科目后重算能把漏掉的课补上")
    void bySubjectGrantsOnlyListedSubjects() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        fixtures.seedCourse("C2", "数学第一课", OrderTestFixtures.LECTURE_CLASS, "SX", IN_SEMESTER.plusDays(1));
        EduCourseOrderPo order = orderService.createOrder(new CourseOrderForms.CreateOrder(
                "ORD-SUBJECT", OrderTestFixtures.LISTEN_CLASS, OrderTestFixtures.ROOM, OrderTestFixtures.DEVICE,
                OrderTestFixtures.LECTURE_CLASS, SEMESTER, "BY_SUBJECT",
                List.of(OrderTestFixtures.SUBJECT_CHINESE), false, null));

        grantService.syncOrder(order);

        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C2", OrderTestFixtures.LISTEN_CLASS)).isZero();

        orderService.updateScope(new CourseOrderForms.UpdateScope(order.getId(), "BY_SUBJECT",
                List.of(OrderTestFixtures.SUBJECT_CHINESE, OrderTestFixtures.SUBJECT_MATH)));
        grantService.syncOrder(orderService.requireOrder(order.getId()));

        assertThat(fixtures.countActiveAttend("C2", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
    }

    @Test
    @DisplayName("按科目移除科目后，该科目课程按引用计数撤销")
    void bySubjectRevokesRemovedSubject() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = orderService.createOrder(new CourseOrderForms.CreateOrder(
                "ORD-SUBJECT", OrderTestFixtures.LISTEN_CLASS, OrderTestFixtures.ROOM, OrderTestFixtures.DEVICE,
                OrderTestFixtures.LECTURE_CLASS, SEMESTER, "BY_SUBJECT",
                List.of(OrderTestFixtures.SUBJECT_CHINESE), false, null));
        grantService.syncOrder(order);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);

        orderService.updateScope(new CourseOrderForms.UpdateScope(order.getId(), "BY_SUBJECT",
                List.of(OrderTestFixtures.SUBJECT_MATH)));
        grantService.syncOrder(orderService.requireOrder(order.getId()));

        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isZero();
        assertThat(fixtures.countGrants(GrantStatus.REVOKED.name())).isEqualTo(1);
    }

    @Test
    @DisplayName("ORDER-06 新建课程后推事件，整班单自动物化，未订科目的按科目单不动")
    void newCourseIsMaterializedForMatchingOrders() {
        EduCourseOrderPo wholeClass = createWholeClassOrder("ORD-WHOLE");
        EduCourseOrderPo bySubject = orderService.createOrder(new CourseOrderForms.CreateOrder(
                "ORD-SUBJECT", OrderTestFixtures.LISTEN_CLASS_B, OrderTestFixtures.ROOM, OrderTestFixtures.DEVICE,
                OrderTestFixtures.LECTURE_CLASS, SEMESTER, "BY_SUBJECT",
                List.of(OrderTestFixtures.SUBJECT_MATH), false, null));

        fixtures.seedCourse("C9", "语文新课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        CourseGrantService.SyncResult result = grantService.onCourseCreated("C9");

        assertThat(result.materialized()).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C9", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C9", OrderTestFixtures.LISTEN_CLASS_B)).isZero();
        assertThat(ledger.find(wholeClass.getId(), "C9")).isNotNull();
        assertThat(ledger.find(bySubject.getId(), "C9")).isNull();
    }

    @Test
    @DisplayName("ORDER-08 重复同步不产生重复听课记录，台账也只有一行")
    void repeatedSyncIsIdempotent() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-IDEM");

        grantService.syncOrder(order);
        grantService.syncOrder(order);
        grantService.syncOrder(order);
        // 新课程事件走的是另一条通道，也必须落在同一套幂等逻辑上。
        grantService.onCourseCreated("C1");

        assertThat(fixtures.countAttend("C1", OrderTestFixtures.LISTEN_CLASS))
                .as("tb_course_attend 没有唯一索引，重复只能靠写入方查重挡住")
                .isEqualTo(1);
        assertThat(fixtures.countAllGrants()).isEqualTo(1);
    }

    @Test
    @DisplayName("ORDER-08 教师已经把听讲班选进课程时复用既有听课记录，不新建")
    void reusesAttendRowCreatedByTeacher() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        fixtures.legacy().update("INSERT INTO tb_course_attend (attend_id, fk_course_id, class_id, status)"
                + " VALUES ('EXISTING-1', 'C1', ?, '0')", String.valueOf(OrderTestFixtures.LISTEN_CLASS));
        EduCourseOrderPo order = createWholeClassOrder("ORD-REUSE");

        grantService.syncOrder(order);

        assertThat(fixtures.countAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
        EduCourseOrderGrantPo grant = ledger.find(order.getId(), "C1");
        assertThat(grant).isNotNull();
        assertThat(grant.getAttendId()).isEqualTo("EXISTING-1");
    }

    // ------------------------------------------------------------------
    // 引用计数撤销（M3）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一节课被两张单授权给不同听讲班时，撤销一张不影响另一张")
    void revokingOneOrderKeepsOtherClassListening() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo orderA = createWholeClassOrder("ORD-A");
        EduCourseOrderPo orderB = orderService.createOrder(new CourseOrderForms.CreateOrder(
                "ORD-B", OrderTestFixtures.LISTEN_CLASS_B, OrderTestFixtures.ROOM, OrderTestFixtures.DEVICE,
                OrderTestFixtures.LECTURE_CLASS, SEMESTER, "WHOLE_CLASS", null, false, null));
        grantService.syncOrder(orderA);
        grantService.syncOrder(orderB);

        orderService.cancelAndRevoke(orderA.getId(), "退订", false);

        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isZero();
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS_B))
                .as("B 单授权的听课记录不能被 A 单的撤销带走")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("同一听讲班被两张单授权同一节课时，撤销一张仍保留听课记录，撤销最后一张才真正失效")
    void keepsAttendUntilLastReferenceRevoked() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        // 直接造第二张单：唯一约束不允许同一对班级有两张有效单，
        // 这里模拟的是「放宽一个听讲班只能订一个主讲班」之后的混用场景。
        EduCourseOrderPo orderA = createWholeClassOrder("ORD-A");
        long orderBId = 990001L;
        fixtures.han().update("INSERT INTO edu_course_order (id, tenant_id, order_no, listen_school_id,"
                        + " listen_class_id, listen_room_id, listen_device_id, lecture_school_id, lecture_class_id,"
                        + " semester_id, grant_scope, status, effective_time, expire_time)"
                        + " VALUES (?, ?, 'ORD-B', ?, ?, ?, ?, ?, ?, ?, 'WHOLE_CLASS', 'ACTIVE', ?, ?)",
                orderBId, OrderTestFixtures.TENANT, OrderTestFixtures.LISTEN_SCHOOL,
                OrderTestFixtures.LISTEN_CLASS, OrderTestFixtures.ROOM, OrderTestFixtures.DEVICE,
                OrderTestFixtures.LECTURE_SCHOOL, 2099L, SEMESTER,
                SEMESTER_BEGIN.atStartOfDay(), SEMESTER_END.atTime(23, 59, 59));

        grantService.syncOrder(orderA);
        EduCourseOrderPo orderB = orderService.requireOrder(orderBId);
        grantService.syncOrder(orderService.requireOrder(orderA.getId()));
        // B 单的主讲班没有课程，手工补一条指向同一节课的台账来构造双引用。
        fixtures.han().update("INSERT INTO edu_course_order_grant (id, tenant_id, order_id, course_id,"
                        + " listen_class_id, grant_status, suspended_flag, attempt_count, attend_id)"
                        + " VALUES (?, ?, ?, 'C1', ?, 'MATERIALIZED', 0, 0, 'SHARED')",
                990002L, OrderTestFixtures.TENANT, orderB.getId(), OrderTestFixtures.LISTEN_CLASS);

        orderService.cancelAndRevoke(orderA.getId(), "退订", false);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS))
                .as("还有一张单在授权，听课记录必须保留")
                .isEqualTo(1);

        grantService.revokeGrant(ledger.find(orderB.getId(), "C1"));
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS))
                .as("最后一条引用撤销后才真正失效")
                .isZero();
    }

    // ------------------------------------------------------------------
    // ORDER-04 冻结与恢复
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ORDER-04 冻结让听课记录失效，恢复后补齐冻结期间新增的课程")
    void freezeAndResume() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-FREEZE");
        grantService.syncOrder(order);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);

        EduCourseOrderPo frozen = orderService.freezeAndSuspend(order.getId(), "主讲班停用");
        assertThat(frozen.getStatus()).isEqualTo(OrderStatus.FROZEN.name());
        assertThat(frozen.getFreezeReason()).isEqualTo("主讲班停用");
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isZero();

        fixtures.seedCourse("C2", "冻结期间新开的课", OrderTestFixtures.LECTURE_CLASS, "SX",
                IN_SEMESTER.plusDays(3));
        orderService.unfreezeAndResume(order.getId());

        assertThat(orderService.requireOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.ACTIVE.name());
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C2", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
        assertThat(fixtures.countAttend("C1", OrderTestFixtures.LISTEN_CLASS))
                .as("恢复复用原来的听课行，不新建")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // ORDER-05 取消
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ORDER-05 取消时未开始的课撤销，已结束的课保留回放")
    void cancelKeepsFinishedPlayback() {
        LocalDateTime now = LocalDateTime.now(CourseOrderService.ZONE);
        fixtures.seedCourse("PAST", "已上完的课", OrderTestFixtures.LECTURE_CLASS, "YW", now.minusDays(5));
        fixtures.seedCourse("FUTURE", "还没上的课", OrderTestFixtures.LECTURE_CLASS, "YW", now.plusDays(5));
        EduCourseOrderPo order = createWholeClassOrder("ORD-CANCEL");
        grantService.syncOrder(order);

        grantService.revokeOrderGrants(order, now, true);

        assertThat(fixtures.countActiveAttend("PAST", OrderTestFixtures.LISTEN_CLASS))
                .as("已结束课程保留回放权限")
                .isEqualTo(1);
        assertThat(fixtures.countActiveAttend("FUTURE", OrderTestFixtures.LISTEN_CLASS))
                .as("未开始课程撤销授权")
                .isZero();
    }

    // ------------------------------------------------------------------
    // ORDER-09 重试
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ORDER-09 失败台账可追踪，故障恢复后重试成功")
    void failedGrantIsRetriedAfterRecovery() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-RETRY");
        gateway.failCourse("C1");
        grantService.syncOrder(order);

        EduCourseOrderGrantPo failed = ledger.find(order.getId(), "C1");
        assertThat(failed.getGrantStatus()).isEqualTo(GrantStatus.FAILED.name());
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("注入的可重试故障");
        assertThat(failed.getLastAttemptTime()).isNotNull();

        gateway.recoverCourse("C1");
        // 退避窗口已过：把上次尝试时间往前挪，然后按当前时间重试。
        CourseGrantService.SyncResult result =
                grantService.retryFailed(6, 10, failed.getLastAttemptTime().plusHours(2));

        assertThat(result.materialized()).isEqualTo(1);
        assertThat(ledger.find(order.getId(), "C1").getGrantStatus())
                .isEqualTo(GrantStatus.MATERIALIZED.name());
        assertThat(ledger.find(order.getId(), "C1").getLastError()).isNull();
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
    }

    @Test
    @DisplayName("ORDER-09 退避窗口未到时不重试")
    void retryRespectsBackoff() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-BACKOFF");
        gateway.failCourse("C1");
        grantService.syncOrder(order);
        gateway.recoverCourse("C1");

        EduCourseOrderGrantPo failed = ledger.find(order.getId(), "C1");
        CourseGrantService.SyncResult result =
                grantService.retryFailed(6, 10, failed.getLastAttemptTime().plusSeconds(1));

        assertThat(result.total()).isZero();
        assertThat(ledger.find(order.getId(), "C1").getGrantStatus()).isEqualTo(GrantStatus.FAILED.name());
    }

    @Test
    @DisplayName("ORDER-09 不可重试的失败不消耗重试次数，避免把真正的原因推过阈值")
    void permanentFailureDoesNotConsumeAttempts() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-PERM");
        gateway.failCourse("C1");
        gateway.setFailuresRetryable(false);

        grantService.syncOrder(order);

        EduCourseOrderGrantPo failed = ledger.find(order.getId(), "C1");
        assertThat(failed.getGrantStatus()).isEqualTo(GrantStatus.FAILED.name());
        assertThat(failed.getAttemptCount()).isZero();
    }

    // ------------------------------------------------------------------
    // ORDER-10 三课堂不回调 Han
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ORDER-10 Han 侧通道不可用时，已物化的听课记录仍然完好")
    void materializedAttendSurvivesHanOutage() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-OUTAGE");
        grantService.syncOrder(order);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);

        gateway.setOffline(true);
        fixtures.seedCourse("C2", "停机期间新开的课", OrderTestFixtures.LECTURE_CLASS, "SX",
                IN_SEMESTER.plusDays(1));

        // 停机期间同步会失败，但这只影响「新课程授权」。
        assertThatThrownBy(() -> grantService.syncOrder(orderService.requireOrder(order.getId())))
                .isInstanceOf(RuntimeException.class);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS))
                .as("已物化的听课记录存在三课堂自己的库里，运行时判定不回调 Han")
                .isEqualTo(1);

        gateway.setOffline(false);
        grantService.syncOrder(orderService.requireOrder(order.getId()));
        assertThat(fixtures.countActiveAttend("C2", OrderTestFixtures.LISTEN_CLASS))
                .as("恢复后补齐停机期间遗漏的物化")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 对账
    // ------------------------------------------------------------------

    @Test
    @DisplayName("教师改课把听课行连带删掉后，对账能把它补回来")
    void reconcileRepairsAttendDeletedByLegacyCourseEdit() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-RECONCILE");
        grantService.syncOrder(order);

        fixtures.simulateLegacyCourseEdit("C1");
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isZero();

        CourseGrantService.ReconcileResult result =
                grantService.reconcileOrder(orderService.requireOrder(order.getId()));

        assertThat(result.repaired()).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isEqualTo(1);
    }

    @Test
    @DisplayName("课程被取消后，台账撤销且听课记录失效")
    void syncRevokesCancelledCourse() {
        fixtures.seedCourse("C1", "语文第一课", OrderTestFixtures.LECTURE_CLASS, "YW", IN_SEMESTER);
        EduCourseOrderPo order = createWholeClassOrder("ORD-COURSE-CANCEL");
        grantService.syncOrder(order);

        fixtures.deleteCourse("C1");
        CourseGrantService.SyncResult result = grantService.syncOrder(orderService.requireOrder(order.getId()));

        assertThat(result.revoked()).isEqualTo(1);
        assertThat(fixtures.countActiveAttend("C1", OrderTestFixtures.LISTEN_CLASS)).isZero();
    }

    // ------------------------------------------------------------------
    // ORDER-03 日期边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ORDER-03 学期三态按闭区间推进，且不动表示启用状态的 status")
    void semesterLifecycleAdvancesOnClosedInterval() {
        assertThat(calendarService.advanceSemesterLifecycle(SEMESTER_BEGIN.minusDays(1))).isEqualTo(1);
        assertThat(lifecycleOf(SEMESTER)).isEqualTo(SemesterLifecycle.NOT_STARTED.name());

        assertThat(calendarService.advanceSemesterLifecycle(SEMESTER_BEGIN)).isEqualTo(1);
        assertThat(lifecycleOf(SEMESTER))
                .as("begin_date 当天就算进行中，闭区间")
                .isEqualTo(SemesterLifecycle.IN_PROGRESS.name());

        assertThat(calendarService.advanceSemesterLifecycle(SEMESTER_END)).isZero();
        assertThat(lifecycleOf(SEMESTER))
                .as("end_date 当天仍在进行中")
                .isEqualTo(SemesterLifecycle.IN_PROGRESS.name());

        assertThat(calendarService.advanceSemesterLifecycle(SEMESTER_END.plusDays(1))).isEqualTo(1);
        assertThat(lifecycleOf(SEMESTER)).isEqualTo(SemesterLifecycle.FINISHED.name());
        assertThat(statusOf(SEMESTER))
                .as("status 仍然是「正常」，学期阶段变化不能影响它")
                .isZero();
    }

    @Test
    @DisplayName("ORDER-03 订购单按生效/失效时间推进，闭区间边界包含首尾")
    void orderStatusAdvancesOnDateBoundary() {
        EduCourseOrderPo order = createWholeClassOrder("ORD-DATE");
        setOrderStatus(order.getId(), OrderStatus.PENDING);

        assertThat(orderService.advanceStatuses(SEMESTER_BEGIN.atStartOfDay().minusSeconds(1))).isZero();
        assertThat(statusOfOrder(order.getId())).isEqualTo(OrderStatus.PENDING.name());

        assertThat(orderService.advanceStatuses(SEMESTER_BEGIN.atStartOfDay())).isEqualTo(1);
        assertThat(statusOfOrder(order.getId())).isEqualTo(OrderStatus.ACTIVE.name());

        assertThat(orderService.advanceStatuses(SEMESTER_END.atTime(23, 59, 59))).isZero();
        assertThat(statusOfOrder(order.getId())).isEqualTo(OrderStatus.ACTIVE.name());

        assertThat(orderService.advanceStatuses(SEMESTER_END.plusDays(1).atStartOfDay())).isEqualTo(1);
        assertThat(statusOfOrder(order.getId())).isEqualTo(OrderStatus.EXPIRED.name());
    }

    @Test
    @DisplayName("定时任务不会把人工冻结的单子拉回生效中")
    void advanceDoesNotResurrectFrozenOrders() {
        EduCourseOrderPo order = createWholeClassOrder("ORD-FROZEN");
        setOrderStatus(order.getId(), OrderStatus.FROZEN);

        assertThat(orderService.advanceStatuses(SEMESTER_BEGIN.atStartOfDay().plusDays(1))).isZero();
        assertThat(statusOfOrder(order.getId())).isEqualTo(OrderStatus.FROZEN.name());
    }

    // ------------------------------------------------------------------
    // 租户隔离
    // ------------------------------------------------------------------

    @Test
    @DisplayName("换一个租户就看不到别人的订购单")
    void ordersAreIsolatedPerTenant() {
        createWholeClassOrder("ORD-TENANT");

        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(9L).tenantId(99L).build());
        assertThat(orderService.listOrders(null, null, null, null, null, 1, 20).getTotal()).isZero();

        SecurityContextHolder.setLoginUser(
                LoginUser.builder().userId(9L).tenantId(OrderTestFixtures.TENANT).build());
        assertThat(orderService.listOrders(null, null, null, null, null, 1, 20).getTotal()).isEqualTo(1);
    }

    // ------------------------------------------------------------------

    private EduCourseOrderPo createWholeClassOrder(String orderNo) {
        return orderService.createOrder(new CourseOrderForms.CreateOrder(
                orderNo, OrderTestFixtures.LISTEN_CLASS, OrderTestFixtures.ROOM, OrderTestFixtures.DEVICE,
                OrderTestFixtures.LECTURE_CLASS, SEMESTER, "WHOLE_CLASS", null, false, null));
    }

    private String lifecycleOf(long semesterId) {
        return fixtures.han().queryForObject(
                "SELECT lifecycle_status FROM edu_semester WHERE id = ?", String.class, semesterId);
    }

    private int statusOf(long semesterId) {
        Integer status = fixtures.han().queryForObject(
                "SELECT status FROM edu_semester WHERE id = ?", Integer.class, semesterId);
        return status == null ? -1 : status;
    }

    private String statusOfOrder(long orderId) {
        return fixtures.han().queryForObject(
                "SELECT status FROM edu_course_order WHERE id = ?", String.class, orderId);
    }

    private void setOrderStatus(long orderId, OrderStatus status) {
        fixtures.han().update("UPDATE edu_course_order SET status = ? WHERE id = ?", status.name(), orderId);
    }
}
