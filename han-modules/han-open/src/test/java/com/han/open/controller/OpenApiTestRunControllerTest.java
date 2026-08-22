package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.open.domain.dto.OpenApiTestRunDTO;
import com.han.open.domain.vo.OpenApiTestRunVO;
import com.han.open.service.OpenApiTestRunService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiTestRunControllerTest {

    @Test
    void controllerExposesScopedRoutesAndDelegates() throws Exception {
        OpenApiTestRunService service = mock(OpenApiTestRunService.class);
        OpenApiTestRunController controller = new OpenApiTestRunController(service);
        OpenApiTestRunDTO request = new OpenApiTestRunDTO();
        OpenApiTestRunVO created = new OpenApiTestRunVO();
        when(service.add(request)).thenReturn(created);
        when(service.list(10L)).thenReturn(List.of(created));

        assertThat(controller.add(request).getData()).isSameAs(created);
        assertThat(controller.list(10L).getData()).containsExactly(created);
        verify(service).add(request);
        verify(service).list(10L);

        assertThat(OpenApiTestRunController.class.isAnnotationPresent(AdminAuth.class)).isTrue();
        assertThat(OpenApiTestRunController.class.getDeclaredMethod("add", OpenApiTestRunDTO.class)
                .isAnnotationPresent(RepeatSubmit.class)).isTrue();
        assertThat(OpenApiTestRunController.class.getDeclaredMethod("add", OpenApiTestRunDTO.class)
                .getAnnotation(PreAuthorize.class).value()).contains("open:grant:apply");
        assertThat(OpenApiTestRunController.class.getDeclaredMethod("list", Long.class)
                .getAnnotation(PreAuthorize.class).value()).contains("open:grant:query");
    }
}
