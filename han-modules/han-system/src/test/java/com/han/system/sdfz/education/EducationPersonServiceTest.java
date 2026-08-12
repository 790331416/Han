package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationPersonServiceTest {

    private static final Long SCHOOL_ID = 11L;
    private static final Long TEACHER_ROLE_ID = 202608120101L;

    @Mock
    private EduPersonMapper personMapper;
    @Mock
    private EduPersonClassMapper personClassMapper;
    @Mock
    private EduPersonSubjectMapper personSubjectMapper;
    @Mock
    private EduSchoolMapper schoolMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduSubjectMapper subjectMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;

    private EducationPersonService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        service = new EducationPersonService(personMapper, personClassMapper, personSubjectMapper,
                schoolMapper, classMapper, subjectMapper, userMapper, userRoleMapper, roleMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsAccountAndPersonInOneWriteAndFillsUserIdOnServer() {
        stubSchool();
        stubNoDuplicatePersonNo();
        when(userMapper.checkUsernameUnique("t.zhang", 1L, null)).thenReturn(0);
        when(roleMapper.selectById(TEACHER_ROLE_ID)).thenReturn(role("teacher"));
        doAnswer(invocation -> {
            SysUserPo value = invocation.getArgument(0);
            value.setId(9001L);
            return 1;
        }).when(userMapper).insert(any(SysUserPo.class));
        doAnswer(invocation -> {
            EduPersonPo value = invocation.getArgument(0);
            value.setId(5001L);
            return 1;
        }).when(personMapper).insert(any(EduPersonPo.class));

        EducationForms.PersonResult result = service.save(teacherForm("t.zhang", "Teacher@2026"));

        ArgumentCaptor<EduPersonPo> personCaptor = ArgumentCaptor.forClass(EduPersonPo.class);
        verify(personMapper).insert(personCaptor.capture());
        verify(userRoleMapper).insert(new SysUserRolePo(9001L, TEACHER_ROLE_ID));

        assertThat(personCaptor.getValue().getUserId()).isEqualTo(9001L);
        assertThat(personCaptor.getValue().getSourceSystem()).isEqualTo("HAN");
        assertThat(result.personId()).isEqualTo(5001L);
        assertThat(result.userId()).isEqualTo(9001L);
        assertThat(result.initialPassword()).isNull();
    }

    @Test
    void generatesInitialPasswordAndForcesChangeWhenCallerOmitsIt() {
        stubSchool();
        stubNoDuplicatePersonNo();
        when(userMapper.checkUsernameUnique("t.li", 1L, null)).thenReturn(0);
        doAnswer(invocation -> {
            SysUserPo value = invocation.getArgument(0);
            value.setId(9002L);
            return 1;
        }).when(userMapper).insert(any(SysUserPo.class));
        doAnswer(invocation -> {
            EduPersonPo value = invocation.getArgument(0);
            value.setId(5002L);
            return 1;
        }).when(personMapper).insert(any(EduPersonPo.class));

        EducationForms.PersonResult result = service.save(teacherForm("t.li", null));

        ArgumentCaptor<SysUserPo> userCaptor = ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(result.initialPassword()).isNotBlank();
        assertThat(userCaptor.getValue().getPwdResetFlag()).isEqualTo(1);
        assertThat(PasswordUtil.matches(result.initialPassword(), userCaptor.getValue().getPassword())).isTrue();
    }

    @Test
    void rejectsDuplicateLoginNameBeforeWritingPerson() {
        stubSchool();
        stubNoDuplicatePersonNo();
        when(userMapper.checkUsernameUnique("t.zhang", 1L, null)).thenReturn(1);

        assertThatThrownBy(() -> service.save(teacherForm("t.zhang", "Teacher@2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("登录名");
        verify(personMapper, never()).insert(any(EduPersonPo.class));
        verify(userMapper, never()).insert(any(SysUserPo.class));
    }

    @Test
    void rejectsDuplicatePersonNoInsideSameSchool() {
        stubSchool();
        when(personMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.save(teacherForm("t.zhang", "Teacher@2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("人员编号");
        verify(personMapper, never()).insert(any(EduPersonPo.class));
    }

    @Test
    void rejectsAssigningSuperAdminRoleThroughPersonEntry() {
        stubSchool();
        stubNoDuplicatePersonNo();
        when(userMapper.checkUsernameUnique("t.zhang", 1L, null)).thenReturn(0);
        when(roleMapper.selectById(1L)).thenReturn(role("admin"));

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_ID, "T001", "张老师", "TEACHER",
                null, 0, null, null, true, "t.zhang", "Teacher@2026", List.of(1L), null, null, null, null);

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");
        verify(userMapper, never()).insert(any(SysUserPo.class));
    }

    @Test
    void createsPersonWithoutAccountWhenLoginDisabled() {
        stubSchool();
        stubNoDuplicatePersonNo();
        doAnswer(invocation -> {
            EduPersonPo value = invocation.getArgument(0);
            value.setId(5003L);
            return 1;
        }).when(personMapper).insert(any(EduPersonPo.class));

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_ID, "T002", "李老师", "TEACHER",
                null, 0, null, null, false, null, null, null, null, null, null, null);

        EducationForms.PersonResult result = service.save(form);

        assertThat(result.userId()).isNull();
        assertThat(result.username()).isNull();
        verify(userMapper, never()).insert(any(SysUserPo.class));
    }

    /** D-1 回归护栏：编辑时前端漏回填角色（空数组或缺省）不得清空既有角色。 */
    @Test
    void keepsExistingRolesWhenEditPayloadOmitsThem() {
        EduPersonPo person = localPerson(5010L, "TEACHER");
        person.setUserId(9010L);
        when(personMapper.selectById(5010L)).thenReturn(person);
        stubSchool();
        stubNoDuplicatePersonNo();
        SysUserPo user = new SysUserPo();
        user.setId(9010L);
        user.setUsername("t.zhang");
        when(userMapper.selectById(9010L)).thenReturn(user);

        EducationForms.Person emptyArray = new EducationForms.Person(5010L, SCHOOL_ID, "T001", "张老师",
                "TEACHER", "13900000001", 0, null, null, true, "t.zhang", null, List.of(), null, null, null, null);
        service.save(emptyArray);

        EducationForms.Person omitted = new EducationForms.Person(5010L, SCHOOL_ID, "T001", "张老师",
                "TEACHER", "13900000001", 0, null, null, true, "t.zhang", null, null, null, null, null, null);
        service.save(omitted);

        verify(userRoleMapper, never()).delete(any());
        verify(userRoleMapper, never()).insert(any(SysUserRolePo.class));
    }

    /** 需要清空角色时必须显式声明，此时才允许改写。 */
    @Test
    void clearsRolesOnlyWhenExplicitlyRequested() {
        EduPersonPo person = localPerson(5011L, "TEACHER");
        person.setUserId(9011L);
        when(personMapper.selectById(5011L)).thenReturn(person);
        stubSchool();
        stubNoDuplicatePersonNo();
        SysUserPo user = new SysUserPo();
        user.setId(9011L);
        user.setUsername("t.zhang");
        when(userMapper.selectById(9011L)).thenReturn(user);

        EducationForms.Person form = new EducationForms.Person(5011L, SCHOOL_ID, "T001", "张老师", "TEACHER",
                null, 0, null, null, true, "t.zhang", null, List.of(), true, null, null, null);
        service.save(form);

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, never()).insert(any(SysUserRolePo.class));
    }

    @Test
    void rejectsStudentBelongingToTwoClasses() {
        stubSchool();
        stubNoDuplicatePersonNo();
        doAnswer(invocation -> {
            EduPersonPo value = invocation.getArgument(0);
            value.setId(5004L);
            return 1;
        }).when(personMapper).insert(any(EduPersonPo.class));

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_ID, "S001", "王同学", "STUDENT",
                null, 0, null, null, false, null, null, null, null, List.of(21L, 22L), null, null);

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("一个有效行政班");
        verify(personClassMapper, never()).insert(any(EduPersonClassPo.class));
    }

    @Test
    void rejectsClassFromAnotherSchool() {
        stubSchool();
        stubNoDuplicatePersonNo();
        doAnswer(invocation -> {
            EduPersonPo value = invocation.getArgument(0);
            value.setId(5005L);
            return 1;
        }).when(personMapper).insert(any(EduPersonPo.class));
        EduClassPo other = new EduClassPo();
        other.setId(21L);
        other.setSchoolId(99L);
        other.setClassName("外校班");
        when(classMapper.selectById(21L)).thenReturn(other);

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_ID, "S002", "赵同学", "STUDENT",
                null, 0, null, null, false, null, null, null, null, List.of(21L), null, null);

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于人员所在学校");
        verify(personClassMapper, never()).insert(any(EduPersonClassPo.class));
    }

    @Test
    void rejectsTeachingSubjectsForStudent() {
        EduPersonPo person = localPerson(5012L, "STUDENT");
        when(personMapper.selectById(5012L)).thenReturn(person);

        assertThatThrownBy(() -> service.replaceAssignments(
                new EducationForms.TeachingAssignment(5012L, List.of(41L), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生不能配置任教科目");
    }

    @Test
    void convergesMembershipsAndDropsStaleClass() {
        EduPersonPo person = localPerson(5006L, "STUDENT");
        when(personMapper.selectById(5006L)).thenReturn(person);
        EduClassPo target = new EduClassPo();
        target.setId(22L);
        target.setSchoolId(SCHOOL_ID);
        when(classMapper.selectById(22L)).thenReturn(target);
        EduPersonClassPo stale = new EduPersonClassPo();
        stale.setId(700L);
        stale.setPersonId(5006L);
        stale.setClassId(21L);
        stale.setMembershipRole("STUDENT");
        when(personClassMapper.selectList(any())).thenReturn(List.of(stale));

        int created = service.replaceMemberships(
                new EducationForms.Membership(5006L, List.of(22L), null));

        verify(personClassMapper).deleteById(700L);
        ArgumentCaptor<EduPersonClassPo> captor = ArgumentCaptor.forClass(EduPersonClassPo.class);
        verify(personClassMapper).insert(captor.capture());
        assertThat(created).isEqualTo(1);
        assertThat(captor.getValue().getClassId()).isEqualTo(22L);
        assertThat(captor.getValue().getSourceSystem()).isEqualTo("HAN");
    }

    @Test
    void disablesLinkedAccountAndKeepsPersonNoOnDelete() {
        EduPersonPo person = localPerson(5007L, "TEACHER");
        person.setUserId(9007L);
        person.setPersonNo("T001");
        when(personMapper.selectById(5007L)).thenReturn(person);
        SysUserPo user = new SysUserPo();
        user.setId(9007L);
        user.setStatus(0);
        when(userMapper.selectById(9007L)).thenReturn(user);
        when(personMapper.deleteById(5007L)).thenReturn(1);

        int removed = service.deletePeople(List.of(5007L));

        ArgumentCaptor<SysUserPo> userCaptor = ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).updateById(userCaptor.capture());

        assertThat(removed).isEqualTo(1);
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(1);
        // 工号是对账主键，删除时保持原值；唯一索引已按 del_flag 生成列排除墓碑行
        assertThat(person.getPersonNo()).isEqualTo("T001");
        verify(personMapper, never()).updateById(any(EduPersonPo.class));
    }

    @Test
    void skipsMissingPersonSoDeleteStaysIdempotent() {
        when(personMapper.selectById(5099L)).thenReturn(null);

        int removed = service.deletePeople(List.of(5099L));

        assertThat(removed).isZero();
        verify(personMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void rejectsDeletingDigitalCampusOwnedPerson() {
        EduPersonPo person = localPerson(5008L, "TEACHER");
        person.setSourceSystem("DIGITAL_CAMPUS");
        when(personMapper.selectById(5008L)).thenReturn(person);

        assertThatThrownBy(() -> service.deletePeople(List.of(5008L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数字校园");
        verify(personMapper, never()).deleteById(any(Long.class));
    }

    /** 离校是教育身份，账号停用是登录能力，两者必须能独立变化（§4.13 FLOW-09）。 */
    @Test
    void marksPersonAsLeftWithoutTouchingAccountStatus() {
        EduPersonPo person = localPerson(5014L, "STUDENT");
        person.setUserId(9014L);
        when(personMapper.selectById(5014L)).thenReturn(person);
        stubSchool();
        stubNoDuplicatePersonNo();
        SysUserPo user = new SysUserPo();
        user.setId(9014L);
        user.setUsername("s.wang");
        user.setStatus(0);
        when(userMapper.selectById(9014L)).thenReturn(user);

        EducationForms.Person form = new EducationForms.Person(5014L, SCHOOL_ID, "S001", "王同学", "STUDENT",
                null, 0, null, 1, true, "s.wang", null, null, null, null, null, null);
        service.save(form);

        assertThat(person.getLeaveFlag()).isEqualTo(1);
        assertThat(person.getLeaveTime()).as("离校时间应在置为离校时写入").isNotNull();
        ArgumentCaptor<SysUserPo> userCaptor = ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).as("人员离校不应顺带改动账号启用状态").isZero();
    }

    /** 恢复在校时清空离校时间，重复提交同一状态不刷新时间。 */
    @Test
    void clearsLeaveTimeWhenPersonReturns() {
        EduPersonPo person = localPerson(5015L, "STUDENT");
        person.setLeaveFlag(1);
        person.setLeaveTime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(personMapper.selectById(5015L)).thenReturn(person);
        stubSchool();
        stubNoDuplicatePersonNo();

        service.save(new EducationForms.Person(5015L, SCHOOL_ID, "S002", "李同学", "STUDENT",
                null, 0, null, 0, false, null, null, null, null, null, null, null));

        assertThat(person.getLeaveFlag()).isZero();
        assertThat(person.getLeaveTime()).isNull();
    }

    @Test
    void readsBackRoleIdsForEditForm() {
        EduPersonPo person = localPerson(5013L, "TEACHER");
        person.setUserId(9013L);
        when(personMapper.selectById(5013L)).thenReturn(person);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(new SysUserRolePo(9013L, TEACHER_ROLE_ID)));

        assertThat(service.listRoleIds(5013L)).containsExactly(TEACHER_ROLE_ID);
    }

    private void stubSchool() {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(SCHOOL_ID);
        school.setSourceSystem("HAN");
        when(schoolMapper.selectById(SCHOOL_ID)).thenReturn(school);
    }

    private void stubNoDuplicatePersonNo() {
        when(personMapper.selectCount(any())).thenReturn(0L);
    }

    private static EducationForms.Person teacherForm(String username, String password) {
        return new EducationForms.Person(null, SCHOOL_ID, "T001", "张老师", "TEACHER", null, 0, null, null,
                true, username, password, password == null ? null : List.of(TEACHER_ROLE_ID), null,
                null, null, null);
    }

    private static EduPersonPo localPerson(Long id, String personType) {
        EduPersonPo person = new EduPersonPo();
        person.setId(id);
        person.setTenantId(1L);
        person.setSchoolId(SCHOOL_ID);
        person.setPersonType(personType);
        person.setSourceSystem("HAN");
        return person;
    }

    private static SysRolePo role(String roleKey) {
        SysRolePo role = new SysRolePo();
        role.setRoleKey(roleKey);
        role.setRoleName(roleKey);
        role.setStatus(0);
        return role;
    }
}
