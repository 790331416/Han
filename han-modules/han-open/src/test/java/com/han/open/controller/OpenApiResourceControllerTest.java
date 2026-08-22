package com.han.open.controller;

import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.service.OpenApiResourceService;
import com.han.open.service.ResourcePathMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiResourceControllerTest {

    private OpenApiResourceMapper resourceMapper;
    private ResourcePathMappingService pathMappingService;
    private OpenApiResourceController controller;

    @BeforeEach
    void setUp() {
        resourceMapper = mock(OpenApiResourceMapper.class);
        pathMappingService = mock(ResourcePathMappingService.class);
        controller = new OpenApiResourceController(
                resourceMapper, mock(OpenApiResourceService.class), pathMappingService);
    }

    @Test
    void successfulMutationsRefreshResourceMapping() {
        when(resourceMapper.insert(any(OpenApiResourcePo.class))).thenReturn(1);
        when(resourceMapper.updateById(any(OpenApiResourcePo.class))).thenReturn(1);
        when(resourceMapper.deleteById(7L)).thenReturn(1);

        assertThat(controller.add(resource())).isNotNull();
        OpenApiResourcePo edit = resource();
        edit.setId(7L);
        assertThat(controller.edit(edit)).isNotNull();
        assertThat(controller.changeStatus(statusUpdate(7L, 1))).isNotNull();
        assertThat(controller.remove(7L)).isNotNull();

        verify(pathMappingService, times(4)).refreshCache();
    }

    @Test
    void failedMutationDoesNotRefreshResourceMapping() {
        when(resourceMapper.insert(any(OpenApiResourcePo.class))).thenReturn(0);
        when(resourceMapper.updateById(any(OpenApiResourcePo.class))).thenReturn(0);
        when(resourceMapper.deleteById(7L)).thenReturn(0);

        controller.add(resource());
        OpenApiResourcePo edit = resource();
        edit.setId(7L);
        controller.edit(edit);
        controller.changeStatus(statusUpdate(7L, 1));
        controller.remove(7L);

        verify(pathMappingService, org.mockito.Mockito.never()).refreshCache();
    }

    @Test
    void adminListCanIncludeDisabledResources() {
        OpenApiResourcePo disabled = resource();
        disabled.setStatus(1);
        when(resourceMapper.selectList(any())).thenReturn(java.util.List.of(disabled));

        assertThat(controller.list(true).getData()).containsExactly(disabled);
        verify(resourceMapper).selectList(org.mockito.ArgumentMatchers.argThat(
                wrapper -> wrapper.getExpression().getNormal().isEmpty()));
    }

    private static OpenApiResourcePo resource() {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setResourceName("教师目录");
        resource.setResourceCode("edu.teacher.read");
        resource.setHttpMethod("get");
        resource.setPath("/open/api/teachers");
        resource.setScopeCode("edu.teacher.read");
        resource.setStatus(0);
        return resource;
    }

    private static OpenApiResourcePo statusUpdate(Long id, Integer status) {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(id);
        resource.setStatus(status);
        return resource;
    }
}
