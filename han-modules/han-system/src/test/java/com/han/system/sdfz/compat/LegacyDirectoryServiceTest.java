package com.han.system.sdfz.compat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.sdfz.education.domain.EduAcademicYearPo;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.mapper.EduAcademicYearMapper;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyDirectoryServiceTest {

    @Mock
    private EduSchoolMapper schoolMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduAcademicYearMapper academicYearMapper;
    @Mock
    private EduSemesterMapper semesterMapper;
    @Mock
    private EduPersonMapper personMapper;
    @Mock
    private EduPersonClassMapper personClassMapper;
    @Mock
    private EduDeviceMapper deviceMapper;
    @Mock
    private EduRoomMapper roomMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysDictDataMapper dictDataMapper;
    @Mock
    private EduSubjectMapper subjectMapper;

    private LegacyCompatProperties properties;
    private LegacyDirectoryService service;

    @BeforeAll
    static void bootstrapEntityMetadata() {
        LegacyTableInfoBootstrap.init();
    }

    @BeforeEach
    void setUp() {
        properties = new LegacyCompatProperties();
        properties.setEnabled(true);
        properties.setTenantId(1L);
        service = new LegacyDirectoryService(properties, schoolMapper, classMapper, academicYearMapper, semesterMapper, personMapper,
                personClassMapper, deviceMapper, roomMapper, userMapper, dictDataMapper, subjectMapper);
        when(semesterMapper.selectList(any())).thenReturn(List.of(currentSemester()));
        when(academicYearMapper.selectById(2026L)).thenReturn(activeYear());
    }

    // ------------------------------------------------------------ 身份

    @Test
    void identityCarriesTheThreeFieldsTheLegacyLiveListReads() {
        when(personMapper.selectOne(any())).thenReturn(teacher(11L, 100L, 7L, 0));
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", "620100"));
        when(personClassMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> identity = asMap(service.identity(request(Map.of("pkId", "11"))).value());

        assertThat(identity)
                .containsEntry("orgId", "7")
                .containsEntry("orgName", "附中")
                .containsEntry("userId", "100")
                .containsEntry("roleType", "2");
    }

    @Test
    void fallsBackToTheConfiguredAreaCodeBecauseTheLegacyFrontendCrashesOnAnEmptyOne() {
        properties.setDefaultAreaCode("620100");
        when(personMapper.selectOne(any())).thenReturn(teacher(11L, 100L, 7L, 0));
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", null));
        when(personClassMapper.selectList(any())).thenReturn(List.of());

        assertThat(asMap(service.identity(request(Map.of("pkId", "11"))).value()))
                .containsEntry("areaCode", "620100");
    }

    @Test
    void returnsEmptyObjectRatherThanFailingWhenTheIdentityIsUnknown() {
        when(personMapper.selectOne(any())).thenReturn(null);

        assertThat(asMap(service.identity(request(Map.of("pkId", "404"))).value())).isEmpty();
    }

    @Test
    void keepsStudentsVisibleInTheDirectoryWithTheirOwnRoleType() {
        EduPersonPo student = teacher(21L, 200L, 7L, 0);
        student.setPersonType(LegacyDirectoryService.STUDENT);
        student.setPersonName("李同学");
        when(personMapper.selectOne(any())).thenReturn(student);
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", "620100"));
        when(personClassMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> identity = asMap(service.identity(request(Map.of("pkId", "21"))).value());

        assertThat(identity)
                .containsEntry("roleType", "4")
                .containsEntry("identityName", "学生")
                .containsEntry("userName", "李同学");
    }

    // ------------------------------------------------------------ 岗位维度

    /**
     * 身份类型与岗位是两套编码，同名不同义。
     *
     * <p>顶层 roleType 必须留 2：旧前端 store/user.ts 的登录过滤只放行 2 或 5，改了教师登不进去。
     * dutyType[].roleType 才是菜单授权用的岗位码，普通教师是 3，拼出 2-3，不命中任何校级菜单。
     */
    @Test
    void plainTeacherGetsTheTeacherDutyCodeWhileIdentityTypeStaysTwo() {
        Map<String, Object> role = service.roleOf(teacher(11L, 100L, 7L, 0));

        assertThat(role).containsEntry("roleType", 2);
        assertThat(firstDuty(role))
                .containsEntry("roleType", "3")
                .containsEntry("positionName", "普通教师");
    }

    /** 显式授予校级管理岗后才拿到 2-1，也就是课程预约那批菜单要的角色串。 */
    @Test
    void schoolAdminDutyIsTheOneThatUnlocksSchoolLevelMenus() {
        EduPersonPo admin = teacher(12L, 101L, 7L, 0);
        admin.setDutyCode("SCHOOL_ADMIN");

        Map<String, Object> role = service.roleOf(admin);

        assertThat(role).containsEntry("roleType", 2).containsEntry("isSchool", "2");
        assertThat(firstDuty(role))
                .containsEntry("roleType", "1")
                .containsEntry("positionName", "管理员");
    }

    /**
     * 存量人员的 duty_code 是空的，必须落在普通教师上。
     *
     * <p>回落成空串会拼出 {@code 2-}（谁也不匹配，但排查时看不出是没配还是配错），
     * 回落成管理岗则是把校级权限默认发给全校，两者都不行。
     */
    @Test
    void personWithoutDutyCodeFallsBackToPlainTeacherNotToAdmin() {
        EduPersonPo legacy = teacher(13L, 102L, 7L, 0);
        legacy.setDutyCode(null);

        assertThat(firstDuty(service.roleOf(legacy))).containsEntry("roleType", "3");
    }

    /** 岗位码是对端契约，配置改了就跟着改，不写死在代码里。 */
    @Test
    void dutyCodeMappingComesFromConfiguration() {
        properties.getDutyType().put("SCHOOL_ADMIN", "9");
        EduPersonPo admin = teacher(14L, 103L, 7L, 0);
        admin.setDutyCode("SCHOOL_ADMIN");

        assertThat(firstDuty(service.roleOf(admin))).containsEntry("roleType", "9");
    }

    /** dutyType 恒为非空数组：旧 api 有 getDutyType().get(0)，前端有 dutyType.some(...)。 */
    @Test
    void dutyTypeIsNeverAnEmptyArray() {
        EduPersonPo unknown = teacher(15L, 104L, 7L, 0);
        unknown.setDutyCode("NOT_A_DUTY");

        assertThat((List<?>) service.roleOf(unknown).get("dutyType")).hasSize(1);
    }

    // ------------------------------------------------------------ status 语义

    @Test
    void reportsHanDeletedRecordsUsingTheLegacyDeletedFlagNotHanStatus() {
        EduPersonPo deleted = teacher(11L, 100L, 7L, 0);
        deleted.setDelFlag(1);
        when(personMapper.selectOne(any())).thenReturn(deleted);
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", "620100"));
        when(personClassMapper.selectList(any())).thenReturn(List.of());

        assertThat(asMap(service.identity(request(Map.of("pkId", "11"))).value()))
                .containsEntry("status", LegacyStatus.DELETED);
    }

    @Test
    void doesNotLeakHanDisabledStateIntoTheLegacySoftDeleteField() {
        EduPersonPo disabled = teacher(11L, 100L, 7L, 1);
        when(personMapper.selectOne(any())).thenReturn(disabled);
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", "620100"));
        when(personClassMapper.selectList(any())).thenReturn(List.of());

        assertThat(asMap(service.identity(request(Map.of("pkId", "11"))).value()))
                .containsEntry("status", LegacyStatus.PRESENT);
    }

    @Test
    void reportsDeletedSchoolsUsingTheLegacyDeletedFlag() {
        EduSchoolPo deleted = school(7L, "已删除学校", "620100");
        deleted.setDelFlag(1);
        when(schoolMapper.selectOne(any())).thenReturn(deleted);

        assertThat(asMap(service.org(request(Map.of("orgId", "7"))).value()))
                .containsEntry("status", LegacyStatus.DELETED);
    }

    @Test
    void answersLegacyDeletedFiltersWithAnEmptyPageInsteadOfQueryingHanStatus() {
        LegacyPayload payload = service.teachers(request(Map.of("state", "1")));

        assertThat(asMap(payload.value())).containsEntry("total", 0L);
        verify(personMapper, never()).selectPage(any(), any());
    }

    // ------------------------------------------------------------ 列表形态

    @Test
    void teacherListUsesTheTeacherNameFieldTheDropdownBindsTo() {
        Page<EduPersonPo> page = new Page<>(1, 20);
        page.setRecords(List.of(teacher(11L, 100L, 7L, 0)));
        page.setTotal(1);
        when(personMapper.selectPage(any(), any())).thenReturn(page);
        when(schoolMapper.selectList(any())).thenReturn(List.of(school(7L, "附中", "620100")));

        Map<String, Object> result = asMap(service.teachers(request(Map.of("orgId", "7"))).value());
        Map<String, Object> record = asMap(((List<?>) result.get("records")).getFirst());

        assertThat(result).containsEntry("total", 1L);
        assertThat(record)
                .containsEntry("teacherName", "张老师")
                .containsEntry("userId", "100");
    }

    @Test
    void deviceListIsAnArrayBecauseTheSelectorAssignsResultDirectly() {
        when(deviceMapper.selectList(any())).thenReturn(List.of(device(31L, "DEV-1", "主讲设备")));

        LegacyPayload payload = service.devices(request(Map.of("orgId", "7")));

        assertThat(payload.value()).isInstanceOf(List.class);
        assertThat(asMap(((List<?>) payload.value()).getFirst()))
                .containsEntry("deviceCode", "DEV-1")
                .containsEntry("deviceName", "主讲设备");
    }

    @Test
    void listeningDeviceListUsesTheSelectedSchoolWithinTheTenant() {
        when(schoolMapper.selectOne(any())).thenReturn(school(8L, "听讲学校", "500100"));
        when(deviceMapper.selectList(any())).thenReturn(List.of(device(31L, "DEV-1", "听讲设备")));

        service.devices(request(Map.of("orgId", "8")));

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<EduDevicePo>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deviceMapper).selectList(captor.capture());
        captor.getValue().getSqlSegment();
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(8L);
    }

    @Test
    void listeningDeviceListRejectsAnUnknownSchool() {
        when(schoolMapper.selectOne(any())).thenReturn(null);

        assertThat((List<?>) service.devices(request(Map.of("orgId", "8"))).value()).isEmpty();
        verify(deviceMapper, never()).selectList(any());
    }

    /**
     * DEF-004：离校教师必须从排课下拉里消失。
     *
     * <p>原实现只按 personType + status 过滤。status 管的是账号启停，
     * leave_flag 管的才是教育身份是否在校，两者独立（EduPersonPo#leaveFlag）。
     * 只看 status 的话，「账号还开着但人已经离校」的教师照样能被排进新课程。
     */
    @SuppressWarnings("unchecked")
    @Test
    void teacherSelectorExcludesPeopleWhoHaveLeftTheSchool() {
        Page<EduPersonPo> page = new Page<>(1, 20);
        page.setRecords(List.of(teacher(11L, 100L, 7L, 0)));
        page.setTotal(1);
        when(personMapper.selectPage(any(), any())).thenReturn(page);
        when(schoolMapper.selectList(any())).thenReturn(List.of(school(7L, "附中", "620100")));

        service.teachers(request(Map.of("orgId", "7")));

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<EduPersonPo>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(personMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .as("教师选择器必须带 leave_flag 条件，否则离校教师仍出现在排课下拉里")
                .contains("leave_flag");
    }

    /** 与上一条成对：按 ID 查身份不能过滤离校，否则历史课程显示不出授课教师姓名。 */
    @Test
    void identityLookupStillResolvesTeachersWhoHaveLeftSoHistoryKeepsTheirName() {
        EduPersonPo left = teacher(11L, 100L, 7L, 0);
        left.setLeaveFlag(1);
        when(personMapper.selectOne(any())).thenReturn(left);
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", "620100"));
        when(personClassMapper.selectList(any())).thenReturn(List.of());

        assertThat(asMap(service.identity(request(Map.of("pkId", "11"))).value()))
                .as("离校教师仍要能按 ID 查到，历史课程要显示他的姓名")
                .containsEntry("userId", "100");
    }

    @Test
    void teacherListStaysAPagedObjectBecauseTheFrontendReadsRecordsAndTotal() {
        Page<EduPersonPo> page = new Page<>(1, 20);
        page.setRecords(List.of(teacher(11L, 100L, 7L, 0)));
        page.setTotal(1);
        when(personMapper.selectPage(any(), any())).thenReturn(page);
        when(schoolMapper.selectList(any())).thenReturn(List.of(school(7L, "附中", "620100")));

        assertThat(asMap(service.teachers(request(Map.of("orgId", "7"))).value()))
                .containsKeys("records", "total");
    }

    @Test
    void unmappedApplicationTypeDoesNotFilterUnlessStrictModeIsOn() {
        when(deviceMapper.selectList(any())).thenReturn(List.of(device(31L, "DEV-1", "主讲设备")));

        assertThat((List<?>) service.devices(request(Map.of("applicationType", "SBLX-UNKNOWN"))).value())
                .hasSize(1);

        properties.setDeviceApplicationTypeStrict(true);
        assertThat((List<?>) service.devices(request(Map.of("applicationType", "SBLX-UNKNOWN"))).value())
                .isEmpty();
    }

    @Test
    void gradeTreeExposesBranchCodeAndStandardNameTheFrontendFiltersOn() {
        EduClassPo first = classOf(41L, "G7", "七年级一班");
        EduClassPo second = classOf(42L, "G7", "七年级二班");
        EduPersonPo student = teacher(21L, 200L, 7L, 0);
        student.setPersonType(LegacyDirectoryService.STUDENT);
        when(classMapper.selectList(any())).thenReturn(List.of(first, second));
        when(personMapper.selectList(any())).thenReturn(List.of(student));
        when(personClassMapper.selectList(any())).thenReturn(List.of(
                membership(61L, 21L, 41L), membership(62L, 21L, 42L)));
        properties.getGradeName().put("G7", "七年级");

        List<?> grades = (List<?>) service.orgBranchTree(request(Map.of("orgId", "7"))).value();
        Map<String, Object> grade = asMap(grades.getFirst());

        assertThat(grade)
                .containsEntry("branchCode", "G7")
                .containsEntry("standardName", "七年级");
        assertThat((List<?>) grade.get("children")).hasSize(2);
    }

    @Test
    void gradeTreeDoesNotRequireTheCurrentAcademicYear() {
        when(semesterMapper.selectList(any())).thenReturn(List.of());
        when(classMapper.selectList(any())).thenReturn(List.of(classOf(41L, "G7", "七年级一班")));
        EduPersonPo student = teacher(21L, 200L, 7L, 0);
        student.setPersonType(LegacyDirectoryService.STUDENT);
        when(personMapper.selectList(any())).thenReturn(List.of(student));
        when(personClassMapper.selectList(any())).thenReturn(List.of(membership(61L, 21L, 41L)));

        assertThat((List<?>) service.orgBranchTree(request(Map.of("orgId", "7"))).value()).hasSize(1);
        verify(classMapper).selectList(any());
    }

    @Test
    void gradeTreeQueriesAllEnabledClassesForTheSchool() {
        when(classMapper.selectList(any())).thenReturn(List.of(classOf(41L, "G7", "七年级一班")));
        EduPersonPo student = teacher(21L, 200L, 7L, 0);
        student.setPersonType(LegacyDirectoryService.STUDENT);
        when(personMapper.selectList(any())).thenReturn(List.of(student));
        when(personClassMapper.selectList(any())).thenReturn(List.of(membership(61L, 21L, 41L)));

        service.orgBranchTree(request(Map.of("orgId", "8")));

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<EduClassPo>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).doesNotContain("academic_year_id");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(7L);
    }

    @Test
    void placesIgnoreBuildingAndFloorFiltersThatHanHasNoColumnsFor() {
        when(roomMapper.selectList(any())).thenReturn(List.of(room(51L, "R-1", "录播教室")));

        List<?> places = (List<?>) service.places(
                request(Map.of("orgId", "7", "buildingId", "B1", "floorId", "3"))).value();

        assertThat(asMap(places.getFirst()))
                .containsEntry("placeCode", "R-1")
                .containsEntry("placeName", "录播教室");
    }

    @Test
    void dictItemsReturnValueAndTextPairs() {
        SysDictDataPo item = new SysDictDataPo();
        item.setDictValue("1");
        item.setDictLabel("启用");
        when(dictDataMapper.selectList(any())).thenReturn(List.of(item));

        List<?> items = (List<?>) service.dictItems(request(Map.of()), "course_status").value();

        assertThat(asMap(items.getFirst()))
                .containsEntry("value", "1")
                .containsEntry("text", "启用");
    }

    @Test
    void unknownDictCodeYieldsAnEmptyListRatherThanAnError() {
        assertThat((List<?>) service.dictItems(request(Map.of()), null).value()).isEmpty();
    }

    @Test
    void courseDictItemsReadEnabledSubjectsFromTheSchool() {
        EduSubjectPo item = new EduSubjectPo();
        item.setSubjectCode("MATH");
        item.setSubjectName("数学");
        when(subjectMapper.selectList(any())).thenReturn(List.of(item));

        LegacyPayload payload = service.dictItems(request(Map.of()), "course");
        List<?> items = (List<?>) payload.value();

        assertThat(asMap(items.getFirst()))
                .containsEntry("value", "MATH")
                .containsEntry("text", "数学");
        assertThat(payload.uiCode()).isZero();
        verify(subjectMapper).selectList(any());
    }

    @Test
    void listeningSchoolSearchReturnsEnabledSchoolsAcrossTheTenant() {
        when(schoolMapper.selectList(any())).thenReturn(List.of(
                school(7L, "主讲学校", "500100"), school(8L, "听讲学校", "500100")));

        List<?> schools = (List<?>) service.orgSearch(request(Map.of())).value();

        assertThat(schools).hasSize(2);
        assertThat(asMap(schools.get(1)))
                .containsEntry("key", "8")
                .containsEntry("value", "听讲学校");
    }

    @Test
    void gradeTreeOnlyReturnsGradesThatHaveActiveStudents() {
        EduPersonPo student = teacher(21L, 200L, 7L, 0);
        student.setPersonType(LegacyDirectoryService.STUDENT);
        EduClassPo populated = classOf(41L, "G004", "一年级一班");
        EduClassPo empty = classOf(42L, "G005", "二年级一班");
        when(personMapper.selectList(any())).thenReturn(List.of(student));
        when(personClassMapper.selectList(any())).thenReturn(List.of(membership(61L, 21L, 41L)));
        when(classMapper.selectList(any())).thenReturn(List.of(populated, empty));

        List<?> grades = (List<?>) service.orgBranchTree(request(Map.of())).value();

        assertThat(grades).hasSize(1);
        assertThat(asMap(grades.getFirst()))
                .containsEntry("branchCode", "G004")
                .containsEntry("standardName", "一年级");
    }

    @Test
    void lazyOrgTreeTreatsTheTokenSchoolAsTheOnlyVisibleRoot() {
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", "620100"));

        List<?> nodes = (List<?>) service.lazyOrgTree(request(Map.of("orgName", "999999"))).value();

        assertThat(nodes).hasSize(1);
        assertThat(asMap(nodes.getFirst())).containsEntry("orgId", "7");
    }

    @Test
    void collapsesDuplicateClassMembershipRowsIntoOneClass() {
        when(personMapper.selectOne(any())).thenReturn(teacher(11L, 100L, 7L, 0));
        when(schoolMapper.selectOne(any())).thenReturn(school(7L, "附中", "620100"));
        when(personClassMapper.selectList(any())).thenReturn(List.of(
                membership(61L, 11L, 41L), membership(62L, 11L, 41L)));
        when(classMapper.selectList(any())).thenReturn(List.of(classOf(41L, "G7", "七年级一班")));

        assertThat(service.classesOf(11L)).hasSize(1);
    }

    @Test
    void identityClassLookupDoesNotRequireACurrentAcademicYear() {
        when(semesterMapper.selectList(any())).thenReturn(List.of());
        when(personClassMapper.selectList(any())).thenReturn(List.of(membership(61L, 11L, 41L)));
        when(classMapper.selectList(any())).thenReturn(List.of(classOf(41L, "G7", "七年级一班")));

        assertThat(service.classesOf(11L)).hasSize(1);
        verify(semesterMapper, never()).selectList(any());

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<EduPersonClassPo>> membershipQuery =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(personClassMapper).selectList(membershipQuery.capture());
        assertThat(membershipQuery.getValue().getSqlSegment()).doesNotContain("academic_year_id");

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<EduClassPo>> classQuery =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classMapper).selectList(classQuery.capture());
        assertThat(classQuery.getValue().getSqlSegment()).doesNotContain("academic_year_id");
    }

    @Test
    void directoryQueriesIgnoreTheRequestedSchoolAndUseTheTokenSchool() {
        Page<EduPersonPo> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(personMapper.selectPage(any(), any())).thenReturn(page);

        service.teachers(request(Map.of("orgId", "8", "areaCode", "999999")));

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<EduPersonPo>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(personMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("school_id");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(7L).doesNotContain(8L);
    }

    @Test
    void identityFromAnotherSchoolIsReturnedAsAnEmptyObject() {
        when(personMapper.selectOne(any())).thenReturn(teacher(12L, 200L, 8L, 0));

        assertThat(asMap(service.identity(request(Map.of("pkId", "12"))).value())).isEmpty();
    }

    // ------------------------------------------------------------ 夹具

    private static LegacyRequest request(Map<String, String> params) {
        return new LegacyRequest(LegacyProtocol.Consumer.LEGACY_API, null, "test", params,
                new LegacyRequest.Scope(1L, 7L, 11L, 100L));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> firstDuty(Map<String, Object> role) {
        return asMap(((List<?>) role.get("dutyType")).getFirst());
    }

    private static EduPersonPo teacher(Long id, Long userId, Long schoolId, Integer status) {
        EduPersonPo person = new EduPersonPo();
        person.setId(id);
        person.setUserId(userId);
        person.setSchoolId(schoolId);
        person.setPersonName("张老师");
        person.setPersonNo("T001");
        person.setPersonType(LegacyDirectoryService.TEACHER);
        person.setStatus(status);
        person.setDelFlag(0);
        return person;
    }

    private static EduSchoolPo school(Long id, String name, String areaCode) {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(id);
        school.setSchoolName(name);
        school.setSchoolCode("S001");
        school.setAreaCode(areaCode);
        school.setStatus(0);
        school.setDelFlag(0);
        return school;
    }

    private static EduClassPo classOf(Long id, String gradeCode, String name) {
        EduClassPo item = new EduClassPo();
        item.setId(id);
        item.setSchoolId(7L);
        item.setAcademicYearId(2026L);
        item.setNodeType("CLASS");
        item.setGradeCode(gradeCode);
        item.setClassCode("C" + id);
        item.setClassName(name);
        item.setStatus(0);
        item.setDelFlag(0);
        return item;
    }

    private static EduDevicePo device(Long id, String code, String name) {
        EduDevicePo item = new EduDevicePo();
        item.setId(id);
        item.setSchoolId(7L);
        item.setDeviceCode(code);
        item.setDeviceName(name);
        item.setDeviceType("CLASSROOM");
        item.setStatus(0);
        item.setDelFlag(0);
        return item;
    }

    private static EduRoomPo room(Long id, String code, String name) {
        EduRoomPo item = new EduRoomPo();
        item.setId(id);
        item.setSchoolId(7L);
        item.setRoomCode(code);
        item.setRoomName(name);
        item.setRoomType("RECORD");
        item.setStatus(0);
        item.setDelFlag(0);
        return item;
    }

    private static EduPersonClassPo membership(Long id, Long personId, Long classId) {
        EduPersonClassPo item = new EduPersonClassPo();
        item.setId(id);
        item.setPersonId(personId);
        item.setClassId(classId);
        item.setAcademicYearId(2026L);
        item.setMembershipRole("MEMBER");
        item.setMembershipStatus("ACTIVE");
        item.setDelFlag(0);
        return item;
    }

    private static EduSemesterPo currentSemester() {
        EduSemesterPo semester = new EduSemesterPo();
        semester.setId(2025L);
        semester.setAcademicYearId(2026L);
        semester.setCurrentFlag(1);
        semester.setStatus(0);
        semester.setDelFlag(0);
        return semester;
    }

    private static EduAcademicYearPo activeYear() {
        EduAcademicYearPo year = new EduAcademicYearPo();
        year.setId(2026L);
        year.setTenantId(1L);
        year.setStatus("ACTIVE");
        year.setDelFlag(0);
        return year;
    }
}
