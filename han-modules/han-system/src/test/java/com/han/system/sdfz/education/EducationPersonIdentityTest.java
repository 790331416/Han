package com.han.system.sdfz.education;

import com.han.api.system.AuthServiceClient;
import com.han.api.system.domain.SessionRevokeRequest;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserRoleMapper;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一账号多学校身份的绑定/解绑/角色清理/密码按身份处理（任务书 12 节）。
 *
 * <p>与 {@link EducationPersonServiceTest} 的差别：这里验证的是「身份粒度」边界——
 * 同一账号多身份时，解绑、编辑、重置密码不得波及同账号的其他身份。</p>
 */
@ExtendWith(MockitoExtension.class)
class EducationPersonIdentityTest {

    private static final Long SCHOOL_A = 11L;
    private static final Long SCHOOL_B = 12L;

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
    @Mock
    private SysDictDataMapper dictDataMapper;
    @Mock
    private EducationDataScopeService dataScopeService;
    @Mock
    private EducationAccountIdentityService accountIdentityService;
    @Mock
    private AuthServiceClient authServiceClient;

    private EducationPersonService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        lenient().when(dataScopeService.current()).thenReturn(EducationDataScopeService.Scope.tenantWide());
        service = new EducationPersonService(personMapper, personClassMapper, personSubjectMapper,
                schoolMapper, classMapper, subjectMapper, userMapper, userRoleMapper, roleMapper, dictDataMapper,
                dataScopeService, accountIdentityService, authServiceClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    /** 解绑只解绑当前身份：同账号另一身份不受影响，账号不停用、角色不清、人员不删。 */
    @Test
    void unbindingOneIdentityKeepsOtherIdentityAndAccountIntact() {
        EduPersonPo first = person(5101L, "TEACHER", SCHOOL_A);
        first.setUserId(9101L);
        EduPersonPo second = person(5102L, "TEACHER", SCHOOL_B);
        second.setUserId(9101L);
        second.setStatus(0);
        when(personMapper.selectById(5101L)).thenReturn(first);
        when(personMapper.selectList(any())).thenReturn(List.of(second));
        stubSchool(SCHOOL_B);

        service.unbindClientUser(9101L, 5101L);

        assertThat(first.getUserId()).isNull();
        assertThat(second.getUserId()).as("同账号其他身份不得被解绑").isEqualTo(9101L);
        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(userRoleMapper, never()).delete(any());
        verify(personMapper, never()).deleteById(any(Long.class));
        verify(personClassMapper, never()).delete(any());
    }

    /** 解绑最后一个身份且账号为独立系统账号：不停用账号、不清角色。 */
    @Test
    void unbindingLastIdentityFromSystemAccountDoesNotDisableIt() {
        EduPersonPo person = person(5106L, "TEACHER", SCHOOL_A);
        person.setUserId(9106L);
        when(personMapper.selectById(5106L)).thenReturn(person);
        when(personMapper.selectList(any())).thenReturn(List.of());
        SysUserPo systemAccount = new SysUserPo();
        systemAccount.setId(9106L);
        systemAccount.setUsername("admin");
        systemAccount.setStatus(0);
        systemAccount.setRemark("系统账号");
        when(userMapper.selectById(9106L)).thenReturn(systemAccount);

        service.unbindClientUser(9106L, 5106L);

        assertThat(person.getUserId()).isNull();
        assertThat(systemAccount.getStatus()).as("独立系统账号解绑最后一个身份不得停用").isZero();
        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(userRoleMapper, never()).delete(any());
    }

    /** 新增第二身份（关联已有账号）：不建新号、不重置密码、不停用/启用、不清角色，只新增绑定。 */
    @Test
    void linkingSecondIdentityToExistingAccountCreatesNoAccountAndKeepsRoles() {
        stubSchool(SCHOOL_A);
        when(personMapper.selectCount(any())).thenReturn(0L);
        SysUserPo existing = new SysUserPo();
        existing.setId(9100L);
        existing.setTenantId(1L);
        existing.setUsername("u_13900000001");
        existing.setNickname("张三");
        existing.setPhone("13900000001");
        existing.setStatus(0);
        existing.setPassword("encoded-password");
        when(userMapper.selectOne(any())).thenReturn(null, existing);
        doAnswer(invocation -> {
            ((EduPersonPo) invocation.getArgument(0)).setId(5100L);
            return 1;
        }).when(personMapper).insert(any(EduPersonPo.class));

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_A, "T100", "张三", "TEACHER",
                null, "13900000001", 0, null, null, true, null, null, null, null, null, null, null);

        EducationForms.PersonResult result = service.save(form);

        assertThat(result.userId()).isEqualTo(9100L);
        assertThat(result.username()).isEqualTo("u_13900000001");
        assertThat(result.initialPassword()).as("关联已有账号不回传初始密码").isNull();
        assertThat(existing.getPassword()).as("不得重置原账号密码").isEqualTo("encoded-password");
        assertThat(existing.getStatus()).as("不得停用/启用原账号").isZero();
        verify(userMapper, never()).insert(any(SysUserPo.class));
        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(userRoleMapper, never()).delete(any());
        verify(accountIdentityService).syncFromAccount(existing);
        ArgumentCaptor<EduPersonPo> captor = ArgumentCaptor.forClass(EduPersonPo.class);
        verify(personMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(9100L);
    }

    /** 同校同有效身份不得重复：关联已有账号时学校已有有效身份则拒绝。 */
    @Test
    void rejectsSecondIdentityInSameSchool() {
        stubSchool(SCHOOL_A);
        // 依次：人员编号生成候选查重 → 人员编号可用性 → 同校有效身份查重
        when(personMapper.selectCount(any())).thenReturn(0L, 0L, 1L);
        SysUserPo existing = new SysUserPo();
        existing.setId(9105L);
        existing.setTenantId(1L);
        existing.setUsername("u_13900000005");
        existing.setPhone("13900000005");
        existing.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(null, existing);

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_A, "T105", "张三", "TEACHER",
                null, "13900000005", 0, null, null, true, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有有效身份");
        verify(personMapper, never()).insert(any(EduPersonPo.class));
    }

    /** 学生身份编辑不得清空账号为其他学校管理员身份保留的角色。 */
    @Test
    void editingStudentIdentityKeepsRolesKeptForOtherSchoolAdmin() {
        EduPersonPo student = person(5103L, "STUDENT", SCHOOL_A);
        student.setUserId(9102L);
        student.setPersonNo("S001");
        EduPersonPo admin = person(5190L, "TEACHER", SCHOOL_B);
        admin.setUserId(9102L);
        admin.setDutyCode("SCHOOL_ADMIN");
        admin.setStatus(0);
        when(personMapper.selectById(5103L)).thenReturn(student);
        when(personMapper.selectCount(any())).thenReturn(0L);
        when(personMapper.selectList(any())).thenReturn(List.of(admin));
        SysUserPo user = new SysUserPo();
        user.setId(9102L);
        user.setUsername("s.wang");
        user.setPhone("13900000003");
        user.setStatus(0);
        when(userMapper.selectById(9102L)).thenReturn(user);
        stubSchool(SCHOOL_A);
        stubSchool(SCHOOL_B);

        EducationForms.Person form = new EducationForms.Person(5103L, SCHOOL_A, "S001", "王同学", "STUDENT",
                null, "13900000003", 0, null, null, true, null, null, null, null, null, null, null);

        service.save(form);

        verify(userRoleMapper, never()).delete(any());
    }

    /** 未绑定登录账号的人员禁用重置密码，且不得为重置偷偷建新号。 */
    @Test
    void unboundPersonCannotResetPassword() {
        EduPersonPo person = person(5104L, "TEACHER", SCHOOL_A);
        when(personMapper.selectById(5104L)).thenReturn(person);

        assertThatThrownBy(() -> service.resetAccountPassword(5104L, "Teacher@2026"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未绑定登录账号");
        verify(userMapper, never()).insert(any(SysUserPo.class));
        verify(userMapper, never()).updateById(any(SysUserPo.class));
    }

    // ---------------------------------------------------------------- 工具

    /** CREATE 模式：手机号已存在则冲突，不得静默改成关联已有账号。 */
    @Test
    void createModeConflictsWhenPhoneAlreadyUsedInsteadOfLinking() {
        stubSchool(SCHOOL_A);
        when(personMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.checkUsernameUnique("u_13900000001", 1L, null)).thenReturn(0);
        when(userMapper.checkPhoneUnique("13900000001", 1L, null)).thenReturn(1);

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_A, "T110", "张三", "TEACHER",
                null, "13900000001", 0, null, null, true, null, null, null, null, null, null, null,
                "CREATE", null);

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("手机号");
        verify(personMapper, never()).insert(any(EduPersonPo.class));
    }

    /** LINK 模式：必须传 linkUserId，缺了直接报错，不自动建号。 */
    @Test
    void linkModeRequiresLinkUserId() {
        stubSchool(SCHOOL_A);
        when(personMapper.selectCount(any())).thenReturn(0L);

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_A, "T111", "张三", "TEACHER",
                null, "13900000001", 0, null, null, true, null, null, null, null, null, null, null,
                "LINK", null);

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须传 linkUserId");
        verify(personMapper, never()).insert(any(EduPersonPo.class));
    }

    /** LINK 模式：只新增绑定，不建新号、不重置密码、不改状态、不清角色。 */
    @Test
    void linkModeBindsExistingAccountWithoutResetting() {
        stubSchool(SCHOOL_A);
        when(personMapper.selectCount(any())).thenReturn(0L);
        SysUserPo target = new SysUserPo();
        target.setId(9120L);
        target.setTenantId(1L);
        target.setUsername("u_13900000020");
        target.setNickname("张三");
        target.setPhone("13900000020");
        target.setStatus(0);
        when(userMapper.selectById(9120L)).thenReturn(target);
        doAnswer(invocation -> {
            ((EduPersonPo) invocation.getArgument(0)).setId(5120L);
            return 1;
        }).when(personMapper).insert(any(EduPersonPo.class));

        EducationForms.Person form = new EducationForms.Person(null, SCHOOL_A, "T112", "张三", "TEACHER",
                null, "13900000020", 0, null, null, true, null, null, null, null, null, null, null,
                "LINK", 9120L);

        EducationForms.PersonResult result = service.save(form);

        assertThat(result.userId()).isEqualTo(9120L);
        assertThat(result.username()).isEqualTo("u_13900000020");
        assertThat(target.getPassword()).as("不得重置原账号密码").isNull();
        verify(userMapper, never()).insert(any(SysUserPo.class));
        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(accountIdentityService).syncFromAccount(target);
    }

    /** DISABLED 模式：编辑已绑定人员按当前身份解绑，独立系统账号不停用。 */
    @Test
    void disabledModeUnbindsBoundIdentity() {
        EduPersonPo person = person(5121L, "TEACHER", SCHOOL_A);
        person.setUserId(9121L);
        person.setPersonNo("T113");
        when(personMapper.selectById(5121L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        when(personMapper.selectList(any())).thenReturn(List.of());
        SysUserPo systemAccount = new SysUserPo();
        systemAccount.setId(9121L);
        systemAccount.setUsername("admin");
        systemAccount.setStatus(0);
        systemAccount.setRemark("系统账号");
        when(userMapper.selectById(9121L)).thenReturn(systemAccount);
        stubSchool(SCHOOL_A);

        EducationForms.Person form = new EducationForms.Person(5121L, SCHOOL_A, "T113", "张三", "TEACHER",
                null, "13900000001", 0, null, null, true, null, null, null, null, null, null, null,
                "DISABLED", null);

        service.save(form);

        assertThat(person.getUserId()).isNull();
        assertThat(systemAccount.getStatus()).as("独立系统账号解绑不得停用").isZero();
        verify(userMapper, never()).updateById(any(SysUserPo.class));
    }

    /** 任务书 24：已绑定独立系统账号同样可以重置密码，不再要求是教育入口建号。 */
    @Test
    void systemAccountPasswordCanBeReset() {
        EduPersonPo person = person(5122L, "TEACHER", SCHOOL_A);
        person.setUserId(9122L);
        when(personMapper.selectById(5122L)).thenReturn(person);
        SysUserPo systemAccount = new SysUserPo();
        systemAccount.setId(9122L);
        systemAccount.setTenantId(1L);
        systemAccount.setUsername("admin");
        systemAccount.setStatus(0);
        systemAccount.setRemark("系统账号");
        when(userMapper.selectById(9122L)).thenReturn(systemAccount);

        service.resetAccountPassword(5122L, "Teacher@2026");

        assertThat(PasswordUtil.matches("Teacher@2026", systemAccount.getPassword())).isTrue();
        verify(userMapper).updateById(systemAccount);
    }

    /** 排除当前正在修改的人员：本人是唯一管理员时降级为普通教师也应清空其管理角色。 */
    @Test
    void demotingLastSchoolAdminClearsRolesBecauseSelfIsExcluded() {
        EduPersonPo person = person(5123L, "TEACHER", SCHOOL_A);
        person.setUserId(9123L);
        person.setDutyCode("SCHOOL_ADMIN");
        person.setPersonNo("T114");
        when(personMapper.selectById(5123L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        when(personMapper.selectList(any())).thenReturn(List.of());
        SysUserPo user = new SysUserPo();
        user.setId(9123L);
        user.setUsername("t.admin");
        user.setPhone("13900000023");
        user.setStatus(0);
        when(userMapper.selectById(9123L)).thenReturn(user);
        stubSchool(SCHOOL_A);

        EducationForms.Person form = new EducationForms.Person(5123L, SCHOOL_A, "T114", "张三", "TEACHER",
                "TEACHER", "13900000023", 0, null, null, true, null, null, null, true, null, null, null);

        service.save(form);

        verify(userRoleMapper).delete(any());
    }

    /** 关联账号精确匹配：只返回一条脱敏信息，不暴露完整邮箱/手机号。 */
    @Test
    void linkableAccountReturnsSingleMaskedMatch() {
        SysUserPo user = new SysUserPo();
        user.setId(9130L);
        user.setNickname("张三");
        user.setPhone("13900000030");
        user.setEmail("zhangsan@example.com");
        when(userMapper.selectOne(any())).thenReturn(user);

        EducationForms.LinkableAccount result = service.linkableAccount("13900000030");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(9130L);
        assertThat(result.phone()).isEqualTo("139****0030");
        assertThat(result.email()).isEqualTo("z***@example.com");
        assertThat(result.phone()).doesNotContain("13900000030");
    }

    /** 关联账号精确匹配：手机号非法直接报错，不允许模糊遍历。 */
    @Test
    void linkableAccountRejectsInvalidPhone() {
        assertThatThrownBy(() -> service.linkableAccount("123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号格式不正确");
        verify(userMapper, never()).selectOne(any());
    }

    // ------------------------------------------------------------ 删除最后身份 / 账号处置

    /** 删除独立系统账号的最后一个教育身份：不停用账号、不清角色，只撤被删除身份会话。 */
    @Test
    void deletingLastIdentityOfSystemAccountKeepsAccountEnabled() {
        EduPersonPo person = boundPerson(5151L, SCHOOL_A, 9151L);
        when(personMapper.selectById(5151L)).thenReturn(person);
        when(personMapper.selectList(any())).thenReturn(List.of());
        SysUserPo systemAccount = account(9151L, "系统账号");
        when(userMapper.selectById(9151L)).thenReturn(systemAccount);

        service.deletePeople(List.of(5151L));

        assertThat(systemAccount.getStatus()).as("独立系统账号删除最后身份不得停用").isZero();
        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(userRoleMapper, never()).delete(any());
        SessionRevokeRequest request = captureSingleRevoke();
        assertThat(request.getUserId()).isEqualTo(9151L);
        assertThat(request.getIdentityId()).isEqualTo(5151L);
    }

    /** 删除教育入口账号的最后一个身份：停用账号、清角色、身份级 + 账号级两次撤销。 */
    @Test
    void deletingLastIdentityOfEducationAccountDisablesAccount() {
        EduPersonPo person = boundPerson(5152L, SCHOOL_A, 9152L);
        when(personMapper.selectById(5152L)).thenReturn(person);
        when(personMapper.selectList(any())).thenReturn(List.of());
        SysUserPo clientAccount = account(9152L, "教育人员统一入口建号");
        when(userMapper.selectById(9152L)).thenReturn(clientAccount);

        service.deletePeople(List.of(5152L));

        assertThat(clientAccount.getStatus()).isEqualTo(1);
        verify(userMapper).updateById(clientAccount);
        verify(userRoleMapper).delete(any());
        List<SessionRevokeRequest> revokes = captureAllRevokes();
        assertThat(revokes).anyMatch(r -> r.getUserId().equals(9152L) && r.getIdentityId().equals(5152L));
        assertThat(revokes).anyMatch(r -> r.getUserId().equals(9152L) && r.getIdentityId() == null);
    }

    /** 删除共享账号的其中一个身份：其他有效身份仍在，账号不停用。 */
    @Test
    void deletingOneIdentityKeepsSharedAccountEnabled() {
        EduPersonPo person = boundPerson(5153L, SCHOOL_A, 9153L);
        when(personMapper.selectById(5153L)).thenReturn(person);
        EduPersonPo other = validOther(5154L, 9153L, SCHOOL_B);
        when(personMapper.selectList(any())).thenReturn(List.of(other));
        stubSchool(SCHOOL_B);

        service.deletePeople(List.of(5153L));

        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(userRoleMapper, never()).delete(any());
        SessionRevokeRequest request = captureSingleRevoke();
        assertThat(request.getIdentityId()).isEqualTo(5153L);
    }

    /** 离校标记为空按在校处理：另一身份 leave_flag=null 仍算有效，账号不停用。 */
    @Test
    void nullLeaveFlagCountsAsActive() {
        EduPersonPo person = boundPerson(5155L, SCHOOL_A, 9155L);
        when(personMapper.selectById(5155L)).thenReturn(person);
        EduPersonPo other = validOther(5156L, 9155L, SCHOOL_B);
        other.setLeaveFlag(null);
        when(personMapper.selectList(any())).thenReturn(List.of(other));
        stubSchool(SCHOOL_B);

        service.deletePeople(List.of(5155L));

        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(userRoleMapper, never()).delete(any());
    }

    /** 已离校身份不算有效：删除最后一个在校身份时，离校身份挡不住账号停用。 */
    @Test
    void leftIdentityDoesNotPreventLastAccountDisable() {
        EduPersonPo person = boundPerson(5157L, SCHOOL_A, 9157L);
        when(personMapper.selectById(5157L)).thenReturn(person);
        EduPersonPo left = validOther(5158L, 9157L, SCHOOL_B);
        left.setLeaveFlag(1);
        when(personMapper.selectList(any())).thenReturn(List.of(left));
        SysUserPo clientAccount = account(9157L, "教育人员统一入口建号");
        when(userMapper.selectById(9157L)).thenReturn(clientAccount);

        service.deletePeople(List.of(5157L));

        assertThat(clientAccount.getStatus()).isEqualTo(1);
        verify(userRoleMapper).delete(any());
    }

    /** 停用学校下的身份不算有效：删除最后一个有效身份时账号停用。 */
    @Test
    void disabledSchoolIdentityDoesNotCountAsValid() {
        EduPersonPo person = boundPerson(5159L, SCHOOL_A, 9159L);
        when(personMapper.selectById(5159L)).thenReturn(person);
        EduPersonPo other = validOther(5160L, 9159L, SCHOOL_B);
        when(personMapper.selectList(any())).thenReturn(List.of(other));
        EduSchoolPo disabled = school(SCHOOL_B, 1, 1L);
        when(schoolMapper.selectById(SCHOOL_B)).thenReturn(disabled);
        SysUserPo clientAccount = account(9159L, "教育人员统一入口建号");
        when(userMapper.selectById(9159L)).thenReturn(clientAccount);

        service.deletePeople(List.of(5159L));

        assertThat(clientAccount.getStatus()).isEqualTo(1);
        verify(userRoleMapper).delete(any());
    }

    /** 学校租户与人员租户不一致的身份不算有效：删除最后一个有效身份时账号停用。 */
    @Test
    void tenantMismatchedSchoolIdentityDoesNotCountAsValid() {
        EduPersonPo person = boundPerson(5161L, SCHOOL_A, 9161L);
        when(personMapper.selectById(5161L)).thenReturn(person);
        EduPersonPo other = validOther(5162L, 9161L, SCHOOL_B);
        when(personMapper.selectList(any())).thenReturn(List.of(other));
        when(schoolMapper.selectById(SCHOOL_B)).thenReturn(school(SCHOOL_B, 0, 999L));
        SysUserPo clientAccount = account(9161L, "教育人员统一入口建号");
        when(userMapper.selectById(9161L)).thenReturn(clientAccount);

        service.deletePeople(List.of(5161L));

        assertThat(clientAccount.getStatus()).isEqualTo(1);
        verify(userRoleMapper).delete(any());
    }

    // ------------------------------------------------------------ LINK 切绑旧账号

    /** LINK 切绑：旧教育入口账号失去最后身份 → 停旧账号、清旧角色、撤旧账号全部会话。 */
    @Test
    void linkingToNewAccountDisablesOldEducationAccountWhenLastIdentity() {
        EduPersonPo person = boundPerson(5171L, SCHOOL_A, 9171L);
        person.setPersonNo("T171");
        when(personMapper.selectById(5171L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        when(personMapper.selectList(any())).thenReturn(List.of());
        stubSchool(SCHOOL_A);
        SysUserPo oldAccount = account(9171L, "教育人员统一入口建号");
        when(userMapper.selectById(9171L)).thenReturn(oldAccount);
        SysUserPo newAccount = new SysUserPo();
        newAccount.setId(9172L);
        newAccount.setTenantId(1L);
        newAccount.setUsername("t.new");
        newAccount.setPhone("13900000071");
        newAccount.setStatus(0);
        when(userMapper.selectById(9172L)).thenReturn(newAccount);

        service.save(linkForm(5171L, 9172L, "13900000071"));

        assertThat(oldAccount.getStatus()).isEqualTo(1);
        assertThat(newAccount.getStatus()).as("不得改动新账号状态").isZero();
        assertThat(newAccount.getPassword()).as("不得改动新账号口令").isNull();
        verify(userMapper).updateById(oldAccount);
        verify(userRoleMapper).delete(any());
        List<SessionRevokeRequest> revokes = captureAllRevokes();
        assertThat(revokes).anyMatch(r -> r.getUserId().equals(9171L) && r.getIdentityId().equals(5171L));
        assertThat(revokes).anyMatch(r -> r.getUserId().equals(9171L) && r.getIdentityId() == null);
    }

    /** LINK 切绑：旧账号是独立系统账号 → 不停旧账号、不清旧角色，只撤旧身份会话。 */
    @Test
    void linkingToNewAccountKeepsOldSystemAccountEnabled() {
        EduPersonPo person = boundPerson(5173L, SCHOOL_A, 9173L);
        person.setPersonNo("T173");
        when(personMapper.selectById(5173L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        when(personMapper.selectList(any())).thenReturn(List.of());
        stubSchool(SCHOOL_A);
        SysUserPo oldAccount = account(9173L, "系统账号");
        when(userMapper.selectById(9173L)).thenReturn(oldAccount);
        SysUserPo newAccount = new SysUserPo();
        newAccount.setId(9174L);
        newAccount.setTenantId(1L);
        newAccount.setUsername("t.new");
        newAccount.setPhone("13900000073");
        newAccount.setStatus(0);
        when(userMapper.selectById(9174L)).thenReturn(newAccount);

        service.save(linkForm(5173L, 9174L, "13900000073"));

        assertThat(oldAccount.getStatus()).isZero();
        verify(userMapper, never()).updateById(oldAccount);
        verify(userRoleMapper, never()).delete(any());
        SessionRevokeRequest request = captureSingleRevoke();
        assertThat(request.getUserId()).isEqualTo(9173L);
        assertThat(request.getIdentityId()).isEqualTo(5173L);
    }

    /** LINK 切绑：旧账号还有其他有效身份 → 不停旧账号，只撤旧身份会话。 */
    @Test
    void linkingToNewAccountKeepsOldAccountWhenOtherIdentityExists() {
        EduPersonPo person = boundPerson(5175L, SCHOOL_A, 9175L);
        person.setPersonNo("T175");
        when(personMapper.selectById(5175L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        EduPersonPo other = validOther(5176L, 9175L, SCHOOL_B);
        when(personMapper.selectList(any())).thenReturn(List.of(other));
        stubSchool(SCHOOL_A);
        stubSchool(SCHOOL_B);
        SysUserPo newAccount = new SysUserPo();
        newAccount.setId(9177L);
        newAccount.setTenantId(1L);
        newAccount.setUsername("t.new");
        newAccount.setPhone("13900000075");
        newAccount.setStatus(0);
        when(userMapper.selectById(9177L)).thenReturn(newAccount);

        service.save(linkForm(5175L, 9177L, "13900000075"));

        verify(userMapper, never()).updateById(any(SysUserPo.class));
        verify(userRoleMapper, never()).delete(any());
        SessionRevokeRequest request = captureSingleRevoke();
        assertThat(request.getUserId()).isEqualTo(9175L);
        assertThat(request.getIdentityId()).isEqualTo(5175L);
    }

    // ---------------------------------------------------------------- 工具

    private static EducationForms.Person linkForm(Long personId, Long linkUserId, String phone) {
        return new EducationForms.Person(personId, SCHOOL_A, null, "张三", "TEACHER",
                null, phone, 0, null, null, null, null, null, null, null, null, null, null,
                "LINK", linkUserId);
    }

    private EduPersonPo boundPerson(Long id, Long schoolId, Long userId) {
        EduPersonPo value = person(id, "TEACHER", schoolId);
        value.setUserId(userId);
        value.setStatus(0);
        value.setLeaveFlag(0);
        return value;
    }

    private EduPersonPo validOther(Long id, Long userId, Long schoolId) {
        return boundPerson(id, schoolId, userId);
    }

    private static SysUserPo account(Long id, String remark) {
        SysUserPo value = new SysUserPo();
        value.setId(id);
        value.setUsername("u_" + id);
        value.setStatus(0);
        value.setRemark(remark);
        return value;
    }

    private SessionRevokeRequest captureSingleRevoke() {
        ArgumentCaptor<SessionRevokeRequest> captor = ArgumentCaptor.forClass(SessionRevokeRequest.class);
        verify(authServiceClient).revokeSession(captor.capture());
        return captor.getValue();
    }

    private List<SessionRevokeRequest> captureAllRevokes() {
        ArgumentCaptor<SessionRevokeRequest> captor = ArgumentCaptor.forClass(SessionRevokeRequest.class);
        verify(authServiceClient, times(2)).revokeSession(captor.capture());
        return captor.getAllValues();
    }

    private EduSchoolPo school(Long schoolId, Integer status, Long tenantId) {
        EduSchoolPo value = new EduSchoolPo();
        value.setId(schoolId);
        value.setTenantId(tenantId);
        value.setStatus(status);
        value.setSourceSystem("HAN");
        return value;
    }

    private void stubSchool(Long schoolId) {
        when(schoolMapper.selectById(schoolId)).thenReturn(school(schoolId, 0, 1L));
    }

    private static EduPersonPo person(Long id, String personType, Long schoolId) {
        EduPersonPo value = new EduPersonPo();
        value.setId(id);
        value.setTenantId(1L);
        value.setSchoolId(schoolId);
        value.setPersonType(personType);
        value.setSourceSystem("HAN");
        return value;
    }
}
