package com.han.system.sdfz.education;

import com.han.api.system.AuthServiceClient;
import com.han.api.system.domain.SessionRevokeRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 身份变更立即撤销旧会话（任务书 13-15 失效，16-24 共享账号）。
 *
 * <p>只验证 han-system 侧对 {@link AuthServiceClient#revokeSession} 的调用时机与粒度：
 * 身份级变更只撤对应身份，账号级停用撤全部。</p>
 */
@ExtendWith(MockitoExtension.class)
class EducationPersonRevokeSessionTest {

    private static final Long SCHOOL_A = 11L;

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
                dataScopeService, accountIdentityService);
        ReflectionTestUtils.setField(service, "authServiceClient", authServiceClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    /** 解绑一个身份只撤该身份会话，不撤账号、不停账号。 */
    @Test
    void unbindingOneIdentityRevokesOnlyThatIdentitySession() {
        EduPersonPo first = person(5101L, "TEACHER", SCHOOL_A);
        first.setUserId(9101L);
        when(personMapper.selectById(5101L)).thenReturn(first);
        when(personMapper.selectCount(any())).thenReturn(2L);

        service.unbindClientUser(9101L, 5101L);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9101L);
        assertThat(request.getIdentityId()).isEqualTo(5101L);
        verify(authServiceClient, times(1)).revokeSession(any(SessionRevokeRequest.class));
        verify(userMapper, never()).updateById(any(SysUserPo.class));
    }

    /** 解绑教育入口建号的最后一个身份：身份撤销 + 账号级停用撤销全部会话。 */
    @Test
    void disablingLastClientAccountRevokesIdentityAndAccountSessions() {
        EduPersonPo person = person(5108L, "TEACHER", SCHOOL_A);
        person.setUserId(9108L);
        when(personMapper.selectById(5108L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(1L);
        SysUserPo clientAccount = new SysUserPo();
        clientAccount.setId(9108L);
        clientAccount.setUsername("u_13900000008");
        clientAccount.setStatus(0);
        clientAccount.setRemark("教育人员统一入口建号");
        when(userMapper.selectById(9108L)).thenReturn(clientAccount);

        service.unbindClientUser(9108L, 5108L);

        ArgumentCaptor<SessionRevokeRequest> captor = ArgumentCaptor.forClass(SessionRevokeRequest.class);
        verify(authServiceClient, times(2)).revokeSession(captor.capture());
        List<SessionRevokeRequest> requests = captor.getAllValues();
        assertThat(requests).anyMatch(r -> r.getUserId().equals(9108L) && r.getIdentityId() == null);
        assertThat(requests).anyMatch(r -> r.getUserId().equals(9108L) && r.getIdentityId().equals(5108L));
        assertThat(clientAccount.getStatus()).isEqualTo(1);
    }

    /** 人员停用只撤当前身份会话，不把共享账号一起停用。 */
    @Test
    void stoppingPersonRevokesIdentitySessionButKeepsAccountEnabled() {
        EduPersonPo person = person(5103L, "TEACHER", SCHOOL_A);
        person.setUserId(9102L);
        person.setStatus(0);
        person.setPersonNo("T103");
        when(personMapper.selectById(5103L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        SysUserPo user = new SysUserPo();
        user.setId(9102L);
        user.setUsername("t.wang");
        user.setPhone("13900000003");
        user.setStatus(0);
        when(userMapper.selectById(9102L)).thenReturn(user);
        stubSchool();

        EducationForms.Person form = new EducationForms.Person(5103L, SCHOOL_A, "T103", "王老师", "TEACHER",
                null, "13900000003", 1, null, null, true, null, null, null, null, null, null, null);
        service.save(form);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9102L);
        assertThat(request.getIdentityId()).isEqualTo(5103L);
        ArgumentCaptor<SysUserPo> userCaptor = ArgumentCaptor.forClass(SysUserPo.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).as("人员停用不应顺带停用共享账号").isZero();
    }

    /** 离校只撤当前身份会话。 */
    @Test
    void leavingPersonRevokesIdentitySession() {
        EduPersonPo person = person(5104L, "TEACHER", SCHOOL_A);
        person.setUserId(9104L);
        person.setStatus(0);
        person.setPersonNo("T104");
        when(personMapper.selectById(5104L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        SysUserPo user = new SysUserPo();
        user.setId(9104L);
        user.setUsername("t.li");
        user.setPhone("13900000004");
        user.setStatus(0);
        when(userMapper.selectById(9104L)).thenReturn(user);
        stubSchool();

        EducationForms.Person form = new EducationForms.Person(5104L, SCHOOL_A, "T104", "李老师", "TEACHER",
                null, "13900000004", 0, null, 1, true, null, null, null, null, null, null, null);
        service.save(form);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9104L);
        assertThat(request.getIdentityId()).isEqualTo(5104L);
    }

    /** 岗位 SCHOOL_ADMIN 降级为普通教师时撤销旧身份会话。 */
    @Test
    void demotingSchoolAdminRevokesIdentitySession() {
        EduPersonPo person = person(5105L, "TEACHER", SCHOOL_A);
        person.setUserId(9105L);
        person.setStatus(0);
        person.setDutyCode("SCHOOL_ADMIN");
        person.setPersonNo("T105");
        when(personMapper.selectById(5105L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        SysUserPo user = new SysUserPo();
        user.setId(9105L);
        user.setUsername("t.admin");
        user.setPhone("13900000005");
        user.setStatus(0);
        when(userMapper.selectById(9105L)).thenReturn(user);
        stubSchool();

        EducationForms.Person form = new EducationForms.Person(5105L, SCHOOL_A, "T105", "赵老师", "TEACHER",
                "TEACHER", "13900000005", 0, null, null, true, null, null, null, null, null, null, null);
        service.save(form);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9105L);
        assertThat(request.getIdentityId()).isEqualTo(5105L);
    }

    /** 管理角色被清空时撤销当前身份会话。 */
    @Test
    void clearingRolesRevokesIdentitySession() {
        EduPersonPo person = person(5106L, "TEACHER", SCHOOL_A);
        person.setUserId(9106L);
        person.setStatus(0);
        person.setPersonNo("T106");
        when(personMapper.selectById(5106L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(0L);
        SysUserPo user = new SysUserPo();
        user.setId(9106L);
        user.setUsername("t.role");
        user.setPhone("13900000006");
        user.setStatus(0);
        when(userMapper.selectById(9106L)).thenReturn(user);
        stubSchool();

        EducationForms.Person form = new EducationForms.Person(5106L, SCHOOL_A, "T106", "钱老师", "TEACHER",
                null, "13900000006", 0, null, null, true, null, null, null, true, null, null, null);
        service.save(form);

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9106L);
        assertThat(request.getIdentityId()).isEqualTo(5106L);
    }

    /** 删除共享账号的其中一个身份：只撤该身份会话，不停账号。 */
    @Test
    void deletingOneOfSeveralIdentitiesRevokesIdentityOnly() {
        EduPersonPo person = person(5107L, "TEACHER", SCHOOL_A);
        person.setUserId(9107L);
        when(personMapper.selectById(5107L)).thenReturn(person);
        when(personMapper.selectCount(any())).thenReturn(1L);

        service.deletePeople(List.of(5107L));

        SessionRevokeRequest request = captureRevoke();
        assertThat(request.getUserId()).isEqualTo(9107L);
        assertThat(request.getIdentityId()).isEqualTo(5107L);
        verify(userMapper, never()).updateById(any(SysUserPo.class));
    }

    // ---------------------------------------------------------------- 工具

    private SessionRevokeRequest captureRevoke() {
        ArgumentCaptor<SessionRevokeRequest> captor = ArgumentCaptor.forClass(SessionRevokeRequest.class);
        verify(authServiceClient).revokeSession(captor.capture());
        return captor.getValue();
    }

    private void stubSchool() {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(SCHOOL_A);
        school.setSourceSystem("HAN");
        when(schoolMapper.selectById(SCHOOL_A)).thenReturn(school);
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
