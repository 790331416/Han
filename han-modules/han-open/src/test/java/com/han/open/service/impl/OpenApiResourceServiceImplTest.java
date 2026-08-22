package com.han.open.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiResourceVersionPo;
import com.han.open.domain.vo.OpenApiResourceVersionVO;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiResourceVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiResourceServiceImplTest {

    private OpenApiResourceMapper resourceMapper;
    private OpenApiResourceVersionMapper versionMapper;
    private OpenApiResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        resourceMapper = mock(OpenApiResourceMapper.class);
        versionMapper = mock(OpenApiResourceVersionMapper.class);
        service = new OpenApiResourceServiceImpl(versionMapper, new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", resourceMapper);
    }

    @Test
    void createDraftValidatesOpenApiPathMethodAndExamples() {
        when(resourceMapper.selectById(7L)).thenReturn(resource());
        when(versionMapper.selectCount(any())).thenReturn(0L);
        when(versionMapper.insert(any(OpenApiResourceVersionPo.class))).thenReturn(1);

        OpenApiResourceVersionVO result = service.createDraftVersion(7L, version("v1"));

        assertThat(result.getStatus()).isEqualTo(0);
        assertThat(result.getOpenapiSchema()).containsEntry("openapi", "3.0.3");
        verify(versionMapper).insert(any(OpenApiResourceVersionPo.class));
    }

    @Test
    void createDraftRejectsSchemaWithoutResourceOperation() {
        when(resourceMapper.selectById(7L)).thenReturn(resource());
        when(versionMapper.selectCount(any())).thenReturn(0L);
        OpenApiResourceVersionVO version = version("v1");
        version.setOpenapiSchema(Map.of(
                "openapi", "3.0.3",
                "paths", Map.of("/other", Map.of("get", Map.of("responses", Map.of())))));

        assertThatThrownBy(() -> service.createDraftVersion(7L, version))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OpenAPI Schema必须包含资源路径及对应HTTP方法");
    }

    @Test
    void publishedVersionCannotBeEdited() {
        OpenApiResourceVersionPo published = new OpenApiResourceVersionPo();
        published.setId(11L);
        published.setStatus(1);
        when(versionMapper.selectById(11L)).thenReturn(published);

        assertThatThrownBy(() -> service.updateDraftVersion(11L, version("v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已发布或已废弃版本不可编辑");
    }

    @Test
    void publishRetiresPreviousVersionAndMarksResourcePublished() {
        OpenApiResourcePo resource = resource();
        OpenApiResourceVersionPo target = storedVersion(11L, 7L, "v2", 0);
        OpenApiResourceVersionPo current = storedVersion(10L, 7L, "v1", 1);
        when(versionMapper.selectById(11L)).thenReturn(target);
        when(resourceMapper.selectById(7L)).thenReturn(resource);
        when(versionMapper.selectList(any())).thenReturn(List.of(current));
        when(versionMapper.update(any(OpenApiResourceVersionPo.class), any())).thenReturn(1, 1);
        when(resourceMapper.updateById(any(OpenApiResourcePo.class))).thenReturn(1);

        OpenApiResourceVersionVO result = service.publishVersion(11L);

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getPublishedAt()).isNotNull();
        org.mockito.ArgumentCaptor<OpenApiResourcePo> captor =
                org.mockito.ArgumentCaptor.forClass(OpenApiResourcePo.class);
        verify(resourceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPublishStatus()).isEqualTo(2);
    }

    private static OpenApiResourcePo resource() {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(7L);
        resource.setPath("/open/api/teachers");
        resource.setHttpMethod("GET");
        return resource;
    }

    private static OpenApiResourceVersionPo storedVersion(Long id, Long resourceId, String version, int status) {
        OpenApiResourceVersionPo po = new OpenApiResourceVersionPo();
        po.setId(id);
        po.setResourceId(resourceId);
        po.setVersion(version);
        po.setStatus(status);
        po.setOpenapiJson(new ObjectMapper().valueToTree(openapi()).toString());
        return po;
    }

    private static OpenApiResourceVersionVO version(String number) {
        OpenApiResourceVersionVO version = new OpenApiResourceVersionVO();
        version.setVersion(number);
        version.setOpenapiSchema(openapi());
        version.setRequestExample(Map.of("teacherId", 1));
        version.setResponseExamples(Map.of("200", Map.of("code", 200)));
        version.setErrorExamples(Map.of("400", Map.of("code", 400)));
        return version;
    }

    private static Map<String, Object> openapi() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("responses", Map.of("200", Map.of("description", "ok")));
        Map<String, Object> path = new LinkedHashMap<>();
        path.put("get", operation);
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/open/api/teachers", path);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("openapi", "3.0.3");
        schema.put("info", Map.of("title", "Teachers", "version", "1.0.0"));
        schema.put("paths", paths);
        return schema;
    }
}
