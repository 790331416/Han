package com.han.system.sdfz.order;

import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import com.han.system.sdfz.order.domain.EduCourseOrderSubjectPo;
import com.han.system.sdfz.order.domain.GrantScope;
import com.han.system.sdfz.order.legacy.LegacyClassroomGateway;
import com.han.system.sdfz.order.legacy.LegacyCourse;
import com.han.system.sdfz.order.mapper.EduCourseOrderGrantMapper;
import com.han.system.sdfz.order.mapper.EduCourseOrderMapper;
import com.han.system.sdfz.order.mapper.EduCourseOrderSubjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 候选集判定的分支覆盖。
 *
 * <p>「哪些课落进 C(O)」是纯判定逻辑，用打桩覆盖各种脏数据分支比建库快得多；
 * 真正涉及事务、幂等、撤销的部分在 {@link CourseOrderIntegrationTest} 里用真库验证。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("候选课程集判定")
class CourseGrantCoverageTest {

    private static final LocalDateTime EFFECTIVE = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
    private static final LocalDateTime EXPIRE = LocalDateTime.of(2027, 1, 15, 23, 59, 59);

    @Mock private EduCourseOrderMapper orderMapper;
    @Mock private EduCourseOrderSubjectMapper orderSubjectMapper;
    @Mock private EduCourseOrderGrantMapper grantMapper;
    @Mock private EduSubjectMapper subjectMapper;
    @Mock private EduSchoolMapper schoolMapper;
    @Mock private EduClassMapper classMapper;
    @Mock private EduRoomMapper roomMapper;
    @Mock private EduDeviceMapper deviceMapper;
    @Mock private CourseGrantLedger ledger;
    @Mock private LegacyClassroomGateway gateway;

    private CourseGrantService service;

    @BeforeEach
    void setUp() {
        service = new CourseGrantService(orderMapper, orderSubjectMapper, grantMapper, subjectMapper,
                schoolMapper, classMapper, roomMapper, deviceMapper, ledger, gateway);
    }

    @Test
    @DisplayName("整班打包不看科目：没订过的科目照样命中")
    void wholeClassIgnoresSubject() {
        EduCourseOrderPo order = order(GrantScope.WHOLE_CLASS);

        assertThat(service.covers(order, course("2002", "WL", EFFECTIVE.plusDays(10)))).isTrue();
        assertThat(service.covers(order, course("2002", null, EFFECTIVE.plusDays(10)))).isTrue();
    }

    @Test
    @DisplayName("按科目只认明细里的科目编码")
    void bySubjectMatchesOnlyListedCodes() {
        EduCourseOrderPo order = order(GrantScope.BY_SUBJECT);
        stubSubscribedSubjects(9001L, "YW");

        assertThat(service.covers(order, course("2002", "YW", EFFECTIVE.plusDays(10)))).isTrue();
        assertThat(service.covers(order, course("2002", "SX", EFFECTIVE.plusDays(10)))).isFalse();
        assertThat(service.covers(order, course("2002", null, EFFECTIVE.plusDays(10)))).isFalse();
    }

    @Test
    @DisplayName("时间窗按闭区间，窗外的课不授权")
    void filtersByTimeWindow() {
        EduCourseOrderPo order = order(GrantScope.WHOLE_CLASS);

        assertThat(service.covers(order, course("2002", "YW", EFFECTIVE))).isTrue();
        assertThat(service.covers(order, course("2002", "YW", EXPIRE))).isTrue();
        assertThat(service.covers(order, course("2002", "YW", EFFECTIVE.minusSeconds(1)))).isFalse();
        assertThat(service.covers(order, course("2002", "YW", EXPIRE.plusSeconds(1)))).isFalse();
    }

    @Test
    @DisplayName("主讲班对不上、上课时间缺失、课程为空都不授权")
    void rejectsUnusableCourses() {
        EduCourseOrderPo order = order(GrantScope.WHOLE_CLASS);

        assertThat(service.covers(order, course("9999", "YW", EFFECTIVE.plusDays(1)))).isFalse();
        assertThat(service.covers(order, course("2002", "YW", null))).isFalse();
        assertThat(service.covers(order, null)).isFalse();
    }

    @Test
    @DisplayName("按科目但明细为空时候选集为空，不会退化成整班打包")
    void emptySubjectListGrantsNothing() {
        EduCourseOrderPo order = order(GrantScope.BY_SUBJECT);
        when(orderSubjectMapper.selectList(any())).thenReturn(List.of());
        when(gateway.listCourses(any(), any(), any()))
                .thenReturn(List.of(course("2002", "YW", EFFECTIVE.plusDays(1))));

        assertThat(service.candidateCourses(order)).isEmpty();
    }

    @Test
    @DisplayName("非生效中的单子不同步，不会误伤已有台账")
    void skipsNonGrantingOrders() {
        EduCourseOrderPo order = order(GrantScope.WHOLE_CLASS);
        order.setStatus("FROZEN");

        CourseGrantService.SyncResult result = service.syncOrder(order);

        assertThat(result.total()).isZero();
    }

    private void stubSubscribedSubjects(Long subjectId, String code) {
        EduCourseOrderSubjectPo detail = new EduCourseOrderSubjectPo();
        detail.setOrderId(1L);
        detail.setSubjectId(subjectId);
        when(orderSubjectMapper.selectList(any())).thenReturn(List.of(detail));

        EduSubjectPo subject = new EduSubjectPo();
        subject.setId(subjectId);
        subject.setSubjectCode(code);
        when(subjectMapper.selectBatchIds(any())).thenReturn(List.of(subject));
    }

    private static EduCourseOrderPo order(GrantScope scope) {
        EduCourseOrderPo order = new EduCourseOrderPo();
        order.setId(1L);
        order.setTenantId(1L);
        order.setListenClassId(2001L);
        order.setLectureClassId(2002L);
        order.setGrantScope(scope.name());
        order.setStatus("ACTIVE");
        order.setEffectiveTime(EFFECTIVE);
        order.setExpireTime(EXPIRE);
        return order;
    }

    private static LegacyCourse course(String classId, String subjectCode, LocalDateTime begin) {
        return new LegacyCourse("C1", "课程", "1", classId, "主讲一班",
                "1002", "主讲学校", subjectCode, subjectCode, begin,
                begin == null ? null : begin.plusMinutes(45));
    }
}
