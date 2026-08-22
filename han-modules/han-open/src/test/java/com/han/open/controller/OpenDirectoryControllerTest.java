package com.han.open.controller;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.EducationDeviceDirectoryVO;
import com.han.api.system.domain.EducationPersonDirectoryVO;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.vo.OpenAccessTokenContext;
import com.han.open.service.IOAuth2Service;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenDirectoryControllerTest {

    private final IOAuth2Service oauth2Service = mock(IOAuth2Service.class);
    private final SystemServiceClient systemServiceClient = mock(SystemServiceClient.class);
    private final OpenDirectoryController controller =
            new OpenDirectoryController(oauth2Service, systemServiceClient);

    private static OpenAccessTokenContext context(List<Long> schoolIds) {
        return new OpenAccessTokenContext(
                1L, 99L, "client-id", Set.of("edu.teacher.read"), schoolIds, "1.0.0", "refresh-token");
    }

    @Test
    void teachersPassesTeacherScopeAndDelegatesToSystemService() {
        when(oauth2Service.requireAccessToken(anyString(), anyString(), anyString())).thenReturn(context(List.of(7L)));
        R<PageResult<EducationPersonDirectoryVO>> expected = R.ok(PageResult.empty());
        when(systemServiceClient.listOpenDirectoryPeople(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expected);

        R<PageResult<EducationPersonDirectoryVO>> response =
                controller.teachers("Bearer abc123", null, null, null, null, null);

        verify(oauth2Service).requireAccessToken("abc123", "edu.teacher.read", "directory.teachers.read");
        verify(systemServiceClient).listOpenDirectoryPeople(
                eq(99L), eq(List.of(7L)), eq("TEACHER"), any(), any(), any(), any());
        assertThat(response).isSameAs(expected);
    }

    @Test
    void studentsPassesStudentScopeAndDelegatesToSystemService() {
        when(oauth2Service.requireAccessToken(anyString(), anyString(), anyString())).thenReturn(context(List.of(7L)));
        R<PageResult<EducationPersonDirectoryVO>> expected = R.ok(PageResult.empty());
        when(systemServiceClient.listOpenDirectoryPeople(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expected);

        R<PageResult<EducationPersonDirectoryVO>> response =
                controller.students("Bearer abc123", null, null, null, null, null);

        verify(oauth2Service).requireAccessToken("abc123", "edu.student.read", "directory.students.read");
        verify(systemServiceClient).listOpenDirectoryPeople(
                eq(99L), eq(List.of(7L)), eq("STUDENT"), any(), any(), any(), any());
        assertThat(response).isSameAs(expected);
    }

    @Test
    void devicesPassesDeviceScopeAndDelegatesToSystemService() {
        when(oauth2Service.requireAccessToken(anyString(), anyString(), anyString())).thenReturn(context(List.of(7L)));
        R<PageResult<EducationDeviceDirectoryVO>> expected = R.ok(PageResult.empty());
        when(systemServiceClient.listOpenDirectoryDevices(any(), any(), any(), any(), any(), any()))
                .thenReturn(expected);

        R<PageResult<EducationDeviceDirectoryVO>> response =
                controller.devices("Bearer abc123", null, null, null, null, null);

        verify(oauth2Service).requireAccessToken("abc123", "edu.device.read", "directory.devices.read");
        verify(systemServiceClient).listOpenDirectoryDevices(
                eq(99L), eq(List.of(7L)), any(), any(), any(), any());
        assertThat(response).isSameAs(expected);
    }

    @Test
    void missingAuthorizationHeaderThrowsMissingBearerToken() {
        assertThatThrownBy(() -> controller.teachers(null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少开放平台 Bearer Token");
        verifyNoInteractions(oauth2Service, systemServiceClient);
    }

    @Test
    void nonBearerAuthorizationThrowsMissingBearerToken() {
        assertThatThrownBy(() -> controller.teachers("abc", null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少开放平台 Bearer Token");
        verifyNoInteractions(oauth2Service, systemServiceClient);
    }

    @Test
    void basicAuthorizationThrowsMissingBearerToken() {
        assertThatThrownBy(() -> controller.students("Basic dXNlcjpwYXNz", null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少开放平台 Bearer Token");
        verifyNoInteractions(oauth2Service, systemServiceClient);
    }

    @Test
    void schoolOutsideTokenGrantThrowsNotAuthorized() {
        when(oauth2Service.requireAccessToken(anyString(), anyString(), anyString())).thenReturn(context(List.of(7L)));

        assertThatThrownBy(() -> controller.teachers("Bearer abc", 99L, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用未获该学校的数据授权");
        verify(oauth2Service).requireAccessToken("abc", "edu.teacher.read", "directory.teachers.read");
        verifyNoInteractions(systemServiceClient);
    }
}
