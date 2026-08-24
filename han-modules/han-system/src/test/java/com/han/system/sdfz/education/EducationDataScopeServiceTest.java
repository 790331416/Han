package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduUserScopePo;
import com.han.system.sdfz.education.domain.EducationScopeForms;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduUserScopeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EducationDataScopeServiceTest {
    @Mock private EduUserScopeMapper userScopeMapper;
    @Mock private EduSchoolMapper schoolMapper;
    private EducationDataScopeService service;

    @BeforeEach void setUp() {
        service = new EducationDataScopeService(userScopeMapper, schoolMapper);
    }

    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test
    void superAdminGetsTenantWideScope() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());

        assertThat(service.current().all()).isTrue();
    }

    @Test
    void tenantSuperAdminRoleGetsTenantWideScope() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2089763675040444417L).tenantId(1L).roleKeys(Set.of("admin")).build());

        assertThat(service.current().all()).isTrue();
    }

    @Test
    void organizationGrantIncludesItsSchoolsButNotAnotherSchool() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        EduUserScopePo grant = new EduUserScopePo();
        grant.setScopeType("ORG"); grant.setScopeId(10L); grant.setIncludeChildren(1); grant.setStatus(0);
        EduSchoolPo bureau = organization(10L, "EDU_BUREAU");
        EduSchoolPo school = organization(11L, "SCHOOL");
        when(userScopeMapper.selectList(any())).thenReturn(List.of(grant));
        when(schoolMapper.selectList(any())).thenReturn(List.of(bureau, school));
        when(schoolMapper.selectBatchIds(any())).thenReturn(List.of(bureau, school));

        EducationDataScopeService.Scope scope = service.current();

        assertThat(scope.all()).isFalse();
        assertThat(scope.organizationIds()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(scope.schoolIds()).containsExactly(11L);
        service.requireSchool(11L);
        assertThatThrownBy(() -> service.requireSchool(12L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("数据范围");
    }

    @Test
    void userWithoutExplicitGrantGetsNoSchoolAccess() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        when(userScopeMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.current().schoolIds()).isEmpty();
        assertThatThrownBy(() -> service.requireSchool(11L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("数据范围");
    }

    @Test
    void ordinaryTenantUserCanListScopesWhenTheMenuPermissionHasBeenGranted() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        when(userScopeMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.listForUser(3L)).isEmpty();
    }

    @Test
    void rejectsLegacyRegionGrantWhenReplacingScopes() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());

        assertThatThrownBy(() -> service.replaceForUser(new EducationScopeForms.Replace(3L,
                List.of(new EducationScopeForms.Item("REGION", 100L, true, null)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("教育局或学校");
    }

    @Test
    void replaceRevokesPreviousScopeAndWritesValidatedNewScope() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        EduUserScopePo previous = new EduUserScopePo();
        previous.setId(90L);
        when(schoolMapper.selectById(11L)).thenReturn(organization(11L, "SCHOOL"));
        when(userScopeMapper.selectList(any())).thenReturn(List.of(previous));

        int count = service.replaceForUser(new EducationScopeForms.Replace(3L,
                List.of(new EducationScopeForms.Item("ORG", 11L, true, "管理两江中学"))));

        ArgumentCaptor<EduUserScopePo> inserted = ArgumentCaptor.forClass(EduUserScopePo.class);
        verify(userScopeMapper).deleteById(90L);
        verify(userScopeMapper).insert(inserted.capture());
        assertThat(count).isEqualTo(1);
        assertThat(inserted.getValue())
                .extracting(EduUserScopePo::getUserId, EduUserScopePo::getScopeType,
                        EduUserScopePo::getScopeId, EduUserScopePo::getIncludeChildren, EduUserScopePo::getRemark)
                .containsExactly(3L, "ORG", 11L, 1, "管理两江中学");
    }

    @Test
    void emptyReplacementRevokesAllScopesWithoutCreatingANewOne() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        EduUserScopePo previous = new EduUserScopePo();
        previous.setId(90L);
        when(userScopeMapper.selectList(any())).thenReturn(List.of(previous));

        assertThat(service.replaceForUser(new EducationScopeForms.Replace(3L, List.of()))).isZero();

        verify(userScopeMapper).deleteById(90L);
        verify(userScopeMapper, never()).insert(any(EduUserScopePo.class));
    }

    private static EduSchoolPo organization(Long id, String type) {
        EduSchoolPo value = new EduSchoolPo(); value.setId(id); value.setOrgType(type); return value;
    }
}
