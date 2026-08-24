package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.api.system.domain.UserVO;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.IAuthService;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalCampusLoginServiceTest {

    @Mock
    private DigitalCampusClient digitalCampusClient;
    @Mock
    private SystemServiceClient systemServiceClient;
    @Mock
    private IAuthService authService;

    private DigitalCampusLoginService service;

    @BeforeEach
    void setUp() {
        service = new DigitalCampusLoginService(digitalCampusClient, systemServiceClient, authService, 1L);
    }

    @Test
    void digitalCampusUsesExternalIdentityIdToResolveLocalPerson() {
        DigitalCampusProfile profile = profile(identity("identity-1"));
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        user.setUsername("dc_user");
        LoginVO login = LoginVO.builder().accessToken("han-token").expiresIn(1800).build();
        when(digitalCampusClient.fetchCurrentUser("external-token")).thenReturn(profile);
        when(systemServiceClient.syncDigitalCampusUser(any())).thenReturn(R.ok(user));
        when(systemServiceClient.getClassroomIdentityByExternal(100L, "identity-1")).thenReturn(R.ok(
                ClassroomIdentityVO.builder()
                        .identityId("900")
                        .externalIdentityId("identity-1")
                        .schoolId("77")
                        .schoolName("测试学校")
                        .personType("TEACHER")
                        .build()));
        when(authService.issueLoginForIdentity(user, ClientType.PC, false, 900L)).thenReturn(login);

        DigitalCampusLoginVO result = service.login("external-token", "identity-1");

        assertThat(result.login()).isEqualTo(login);
        assertThat(result.externalIdentity().externalUserId()).isEqualTo("external-user-1");
        assertThat(result.externalIdentity().identityId()).isEqualTo("identity-1");

        ArgumentCaptor<DigitalCampusUserSyncDTO> dtoCaptor =
                ArgumentCaptor.forClass(DigitalCampusUserSyncDTO.class);
        verify(systemServiceClient).syncDigitalCampusUser(dtoCaptor.capture());
        DigitalCampusUserSyncDTO dto = dtoCaptor.getValue();
        assertThat(dto.getTenantId()).isEqualTo(1L);
        assertThat(dto.getExternalUserId()).isEqualTo("external-user-1");
        assertThat(dto.getClasses()).extracting(DigitalCampusUserSyncDTO.ClassMembership::getBranchId)
                .containsExactly("class-1");
        // 本地身份按稳定外部身份 ID 精确解析，不再走「学校名 + 姓名」列表匹配。
        verify(systemServiceClient).getClassroomIdentityByExternal(100L, "identity-1");
        verify(systemServiceClient, never()).listClassroomIdentities(anyLong());
        verify(authService).issueLoginForIdentity(user, ClientType.PC, false, 900L);
    }

    @Test
    void requiresIdentityChoiceForMultiIdentityAccount() {
        when(digitalCampusClient.fetchCurrentUser("external-token"))
                .thenReturn(profile(identity("identity-1"), identity("identity-2")));

        assertThatThrownBy(() -> service.login("external-token", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请选择数字校园登录身份");
    }

    @Test
    void duplicateSchoolNamesDoNotAffectIdentityMapping() {
        // 两个学校同名，但外部身份 ID 不同：精确接口按 identity-2 定位到本地身份 902，不受同名干扰。
        DigitalCampusProfile profile = profile(identity("identity-2"));
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        user.setUsername("dc_user");
        LoginVO login = LoginVO.builder().accessToken("han-token").expiresIn(1800).build();
        when(digitalCampusClient.fetchCurrentUser("external-token")).thenReturn(profile);
        when(systemServiceClient.syncDigitalCampusUser(any())).thenReturn(R.ok(user));
        when(systemServiceClient.getClassroomIdentityByExternal(100L, "identity-2")).thenReturn(R.ok(
                ClassroomIdentityVO.builder()
                        .identityId("902")
                        .externalIdentityId("identity-2")
                        .schoolId("78")
                        .schoolName("测试学校")
                        .personType("TEACHER")
                        .build()));
        when(authService.issueLoginForIdentity(user, ClientType.PC, false, 902L)).thenReturn(login);

        DigitalCampusLoginVO result = service.login("external-token", "identity-2");

        assertThat(result.login()).isEqualTo(login);
        verify(systemServiceClient).getClassroomIdentityByExternal(100L, "identity-2");
        verify(authService).issueLoginForIdentity(user, ClientType.PC, false, 902L);
    }

    @Test
    void renamedSchoolStillResolvesSameIdentity() {
        // 学校改名不影响外部身份 ID 到本地身份的映射：同一 externalIdentityId 依旧解析到 900。
        DigitalCampusProfile profile = profile(identity("identity-1"));
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        user.setUsername("dc_user");
        LoginVO login = LoginVO.builder().accessToken("han-token").expiresIn(1800).build();
        when(digitalCampusClient.fetchCurrentUser("external-token")).thenReturn(profile);
        when(systemServiceClient.syncDigitalCampusUser(any())).thenReturn(R.ok(user));
        when(systemServiceClient.getClassroomIdentityByExternal(100L, "identity-1")).thenReturn(R.ok(
                ClassroomIdentityVO.builder()
                        .identityId("900")
                        .externalIdentityId("identity-1")
                        .schoolId("77")
                        .schoolName("改名后的学校")
                        .personType("TEACHER")
                        .build()));
        when(authService.issueLoginForIdentity(user, ClientType.PC, false, 900L)).thenReturn(login);

        DigitalCampusLoginVO result = service.login("external-token", "identity-1");

        assertThat(result.login()).isEqualTo(login);
        verify(systemServiceClient).getClassroomIdentityByExternal(100L, "identity-1");
        verify(authService).issueLoginForIdentity(user, ClientType.PC, false, 900L);
    }

    @Test
    void unknownExternalIdentityIsRejected() {
        DigitalCampusProfile profile = profile(identity("identity-1"));
        UserVO user = new UserVO();
        user.setUserId(100L);
        user.setStatus(0);
        user.setUsername("dc_user");
        when(digitalCampusClient.fetchCurrentUser("external-token")).thenReturn(profile);
        when(systemServiceClient.syncDigitalCampusUser(any())).thenReturn(R.ok(user));
        when(systemServiceClient.getClassroomIdentityByExternal(100L, "identity-1")).thenReturn(R.ok(null));

        assertThatThrownBy(() -> service.login("external-token", "identity-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数字校园身份与本地教育身份不匹配，请联系管理员");
    }

    @Test
    void rejectsProfileWithoutStableExternalUserId() {
        DigitalCampusProfile.Identity identity = new DigitalCampusProfile.Identity(
                "", "测试教师", "identity-1", "教师", "2", "school-1", "测试学校",
                "class-1", "一班", "2", "50010000", List.of(), List.of());
        when(digitalCampusClient.fetchCurrentUser("external-token")).thenReturn(profile(identity));

        assertThatThrownBy(() -> service.login("external-token", "identity-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数字校园未返回稳定用户标识");
    }

    private DigitalCampusProfile profile(DigitalCampusProfile.Identity... identities) {
        return new DigitalCampusProfile("138****0000", List.of(identities));
    }

    private DigitalCampusProfile.Identity identity(String identityId) {
        return new DigitalCampusProfile.Identity(
                "external-user-1", "测试教师", identityId, "教师", "2",
                "school-1", "测试学校", "class-1", "一班", "2", "50010000",
                List.of(new DigitalCampusProfile.Duty("duty-1", "9", "教师", "教师")),
                List.of(new DigitalCampusProfile.ClassMembership(
                        "class-1", "一班", "teacher", "一班", "school-1", "测试学校",
                        "3", "50010000", "edu-1", "测试教育部门", "", "", "", "", "", "")));
    }
}
