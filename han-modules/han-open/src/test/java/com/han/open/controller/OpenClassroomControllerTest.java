package com.han.open.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.EducationDeviceDirectoryVO;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.vo.OpenAccessTokenContext;
import com.han.open.service.IOAuth2Service;
import com.han.open.service.OpenClassroomProxyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenClassroomControllerTest {

    private final IOAuth2Service oauth2Service = mock(IOAuth2Service.class);
    private final OpenClassroomProxyService proxyService = mock(OpenClassroomProxyService.class);
    private final SystemServiceClient systemServiceClient = mock(SystemServiceClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenClassroomController controller = new OpenClassroomController(
            oauth2Service, proxyService, systemServiceClient, objectMapper);

    @Test
    void deviceKeepsTheLegacyResultEnvelopeAndNeverCallsDigitalCampus() throws Exception {
        when(oauth2Service.requireAccessToken(anyString(), anyString(), anyString())).thenReturn(context());
        EducationDeviceDirectoryVO device = new EducationDeviceDirectoryVO(
                11L, "DEVICE-01", "测试设备", "RECORDER", List.of("CLASSROOM"),
                7L, "测试学校", 9L, "测试教室", 0, null);
        when(systemServiceClient.getOpenDirectoryDevice(eq(99L), eq(List.of(7L)), eq("DEVICE-01")))
                .thenReturn(R.ok(device));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("deviceCode", "DEVICE-01");

        ResponseEntity<String> response = controller.device("Bearer access-token", request);
        JsonNode body = objectMapper.readTree(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("code").asInt()).isEqualTo(200);
        assertThat(body.path("result").path("device_code").asText()).isEqualTo("DEVICE-01");
        assertThat(body.path("result").has("building_id")).isFalse();
        assertThat(body.path("result").has("supplier_id")).isFalse();
        verify(oauth2Service).requireAccessToken("access-token", "classroom.device.read", "classroom.device.read");
        verify(systemServiceClient).getOpenDirectoryDevice(99L, List.of(7L), "DEVICE-01");
    }

    @Test
    void deviceRejectsMissingBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("deviceCode", "DEVICE-01");

        assertThatThrownBy(() -> controller.device(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少开放平台 Bearer Token");
    }

    @Test
    void missingDeviceUsesLegacyBusinessFailureEnvelope() throws Exception {
        when(oauth2Service.requireAccessToken(anyString(), anyString(), anyString())).thenReturn(context());
        when(systemServiceClient.getOpenDirectoryDevice(eq(99L), eq(List.of(7L)), eq("MISSING")))
                .thenReturn(R.<EducationDeviceDirectoryVO>ok());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("deviceCode", "MISSING");

        JsonNode body = objectMapper.readTree(controller.device("Bearer access-token", request).getBody());

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("code").asInt()).isEqualTo(500);
        assertThat(body.path("message").asText()).isEqualTo("设备不存在或不在授权学校范围");
        assertThat(body.path("result").isNull()).isTrue();
    }

    @Test
    void courseListNarrowsAMultiSchoolApplicationToRequestedOrgan() {
        OpenAccessTokenContext multiSchool = new OpenAccessTokenContext(1L, 99L, "video-platform",
                Set.of("classroom.course.read"), List.of(7L, 8L), "1", "refresh", 123L, "PROD");
        when(oauth2Service.requireAccessToken(anyString(), anyString(), anyString())).thenReturn(multiSchool);
        when(proxyService.forward(any(), anyString(), any(), isNull(), any())).thenReturn(ResponseEntity.ok("{}"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("organId", "8");

        controller.courseList("Bearer access-token", request);

        ArgumentCaptor<OpenAccessTokenContext> context = ArgumentCaptor.forClass(OpenAccessTokenContext.class);
        verify(proxyService).forward(eq(HttpMethod.POST),
                eq("/inner/open-classroom/tb-course-info/getCourseInfoList"), any(), isNull(), context.capture());
        assertThat(context.getValue().schoolIds()).containsExactly(8L);
    }

    private static OpenAccessTokenContext context() {
        return new OpenAccessTokenContext(1L, 99L, "video-platform", Set.of("classroom.device.read"),
                List.of(7L), "1", "refresh", 123L, "SANDBOX");
    }
}
