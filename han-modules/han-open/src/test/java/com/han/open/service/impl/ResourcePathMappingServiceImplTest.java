package com.han.open.service.impl;

import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.mapper.OpenApiResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourcePathMappingServiceImplTest {

    private OpenApiResourceMapper resourceMapper;
    private ResourcePathMappingServiceImpl service;

    @BeforeEach
    void setUp() {
        resourceMapper = mock(OpenApiResourceMapper.class);
        service = new ResourcePathMappingServiceImpl(resourceMapper);
    }

    @Test
    void exactPathWinsOverAntPatternsAndVariablePatternWinsOverCatchAll() {
        OpenApiResourcePo catchAll = resource(1L, "GET", "/open/api/users/**", 0);
        OpenApiResourcePo variable = resource(2L, "GET", "/open/api/users/{id}", 0);
        OpenApiResourcePo exact = resource(3L, "GET", "/open/api/users/me", 0);
        when(resourceMapper.selectList(any())).thenReturn(List.of(catchAll, variable, exact));

        service.refreshCache();

        assertThat(service.matchResource("get", "/open/api/users/me").getId()).isEqualTo(3L);
        assertThat(service.matchResource("GET", "/open/api/users/42").getId()).isEqualTo(2L);
        assertThat(service.matchResource("GET", "/open/api/users/42/profile").getId()).isEqualTo(1L);
    }

    @Test
    void methodMustMatchBeforePathMatching() {
        OpenApiResourcePo get = resource(1L, "GET", "/open/api/users/{id}", 0);
        OpenApiResourcePo post = resource(2L, "POST", "/open/api/users/{id}", 0);
        when(resourceMapper.selectList(any())).thenReturn(List.of(get, post));

        service.refreshCache();

        assertThat(service.matchResource("GET", "/open/api/users/42").getId()).isEqualTo(1L);
        assertThat(service.matchResource("post", "/open/api/users/42").getId()).isEqualTo(2L);
        assertThat(service.matchResource("DELETE", "/open/api/users/42")).isNull();
    }

    @Test
    void refreshRemovesDisabledAndDeletedResourcesFromSnapshot() {
        OpenApiResourcePo active = resource(1L, "GET", "/open/api/users", 0);
        when(resourceMapper.selectList(any())).thenReturn(List.of(active));
        service.refreshCache();
        assertThat(service.matchResource("GET", "/open/api/users")).isSameAs(active);

        OpenApiResourcePo disabled = resource(1L, "GET", "/open/api/users", 1);
        when(resourceMapper.selectList(any())).thenReturn(List.of(disabled));
        service.refreshCache();
        assertThat(service.matchResource("GET", "/open/api/users")).isNull();

        when(resourceMapper.selectList(any())).thenReturn(List.of());
        service.refreshCache();
        assertThat(service.matchResource("GET", "/open/api/users")).isNull();
    }

    @Test
    void failedRefreshKeepsLastCompleteSnapshot() {
        OpenApiResourcePo active = resource(1L, "GET", "/open/api/users", 0);
        when(resourceMapper.selectList(any())).thenReturn(List.of(active));
        service.refreshCache();

        when(resourceMapper.selectList(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(service::refreshCache)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        assertThat(service.matchResource("GET", "/open/api/users")).isSameAs(active);
    }

    private static OpenApiResourcePo resource(Long id, String method, String path, Integer status) {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(id);
        resource.setHttpMethod(method);
        resource.setPath(path);
        resource.setStatus(status);
        return resource;
    }
}
