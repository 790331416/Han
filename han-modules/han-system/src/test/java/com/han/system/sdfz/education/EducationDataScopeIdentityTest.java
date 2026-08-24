package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduUserScopePo;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduUserScopeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 一账号多学校身份：教育数据范围按当前身份收敛单校。 */
@ExtendWith(MockitoExtension.class)
class EducationDataScopeIdentityTest {
    @Mock private EduUserScopeMapper userScopeMapper;
    @Mock private EduSchoolMapper schoolMapper;
    private EducationDataScopeService service;

    @BeforeEach void setUp() {
        service = new EducationDataScopeService(userScopeMapper, schoolMapper);
    }

    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test
    void identitySessionReturnsSingleSchoolAndIgnoresMultiSchoolGrants() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).tenantId(1L).identityScoped(true).schoolId(11L).build());

        EducationDataScopeService.Scope scope = service.current();

        assertThat(scope.all()).isFalse();
        assertThat(scope.schoolIds()).containsExactly(11L);
        assertThat(scope.organizationIds()).containsExactly(11L);
        // 身份会话下不得再查询 edu_user_scope 的多校并集
        verify(userScopeMapper, never()).selectList(any());
        verify(schoolMapper, never()).selectBatchIds(any());
    }

    @Test
    void identitySessionRejectsOtherSchool() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).tenantId(1L).identityScoped(true).schoolId(11L).build());

        assertThatThrownBy(() -> service.requireSchool(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("身份数据范围");
    }

    @Test
    void identitySessionAllowsOwnSchool() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).tenantId(1L).identityScoped(true).schoolId(11L).build());

        assertThatCode(() -> service.requireSchool(11L)).doesNotThrowAnyException();
    }

    @Test
    void nonIdentitySessionKeepsMultiSchoolUnion() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        EduUserScopePo grant = new EduUserScopePo();
        grant.setScopeType("ORG"); grant.setScopeId(10L); grant.setIncludeChildren(1); grant.setStatus(0);
        EduSchoolPo bureau = organization(10L, "EDU_BUREAU");
        EduSchoolPo schoolA = organization(11L, "SCHOOL");
        EduSchoolPo schoolB = organization(12L, "SCHOOL");
        when(userScopeMapper.selectList(any())).thenReturn(List.of(grant));
        when(schoolMapper.selectList(any())).thenReturn(List.of(bureau, schoolA, schoolB));
        when(schoolMapper.selectBatchIds(any())).thenReturn(List.of(bureau, schoolA, schoolB));

        EducationDataScopeService.Scope scope = service.current();

        assertThat(scope.all()).isFalse();
        assertThat(scope.schoolIds()).containsExactlyInAnyOrder(11L, 12L);
        assertThat(scope.organizationIds()).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    private static EduSchoolPo organization(Long id, String type) {
        EduSchoolPo value = new EduSchoolPo(); value.setId(id); value.setOrgType(type); return value;
    }
}
