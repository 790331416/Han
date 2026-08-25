package com.han.open.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.dto.OpenApiIntegrationExportDTO;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiResourceVersionPo;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiResourceVersionMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenApiIntegrationExportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenApiResourceMapper resourceMapper = mock(OpenApiResourceMapper.class);
    private final OpenApiResourceVersionMapper versionMapper = mock(OpenApiResourceVersionMapper.class);
    private final OpenApiIntegrationExportService service =
            new OpenApiIntegrationExportService(resourceMapper, versionMapper, objectMapper);

    @Test
    void exportsExecutableSecretFreeCollectionAndCompleteZip() throws Exception {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(1L);
        resource.setResourceCode("classroom.live-status.read");
        resource.setResourceName("查询直播状态");
        resource.setCategory("视频课堂");
        resource.setHttpMethod("GET");
        resource.setPath("/open/api/v1/classroom/course/deliveryClassroom/getLiveStatusByUUID");
        resource.setScopeCode("classroom.live.read");
        resource.setStatus(0);
        resource.setPublishStatus(2);

        OpenApiResourceVersionPo version = new OpenApiResourceVersionPo();
        version.setId(2L);
        version.setResourceId(1L);
        version.setStatus(1);
        version.setDelFlag(0);
        version.setOpenapiJson("{\"openapi\":\"3.0.3\",\"paths\":{\"/open/api/v1/classroom/course/deliveryClassroom/getLiveStatusByUUID\":{\"get\":{\"operationId\":\"liveStatus\",\"responses\":{\"200\":{\"description\":\"成功\"}}}}}}");
        version.setRequestExampleJson("{\"roomId\":\"room-001\",\"clientSecret\":\"must-not-leak\"}");
        version.setResponseExamplesJson("{\"200\":{\"code\":200,\"data\":{\"liveStatus\":1}}}");
        version.setErrorExamplesJson("{\"401\":{\"code\":401,\"message\":\"unauthorized\",\"accessToken\":\"must-not-leak\"}}");

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        OpenApiIntegrationExportDTO export = service.build("https://example.test/");
        JsonNode openApi = objectMapper.readTree(export.openApiJson());
        JsonNode operation = openApi.path("paths").path(resource.getPath()).path("get");
        assertThat(operation.path("parameters")).anyMatch(parameter ->
                parameter.path("name").asText().equals("roomId") && parameter.path("in").asText().equals("query"));
        assertThat(operation.path("responses").path("200").path("content").path("application/json")
                .path("examples").path("200").path("value").path("data").path("liveStatus").asInt())
                .as(operation.toPrettyString()).isEqualTo(1);

        String collection = new String(export.postmanCollectionJson(), StandardCharsets.UTF_8);
        String environment = new String(export.postmanEnvironmentJson(), StandardCharsets.UTF_8);
        assertThat(collection).contains("pm.sendRequest", "/open/oauth2/token", "{{accessToken}}", "roomId=room-001")
                .doesNotContain("must-not-leak");
        assertThat(environment).contains("\"clientSecret\"").doesNotContain("must-not-leak");

        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(export.zip()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).containsExactly("openapi.json", "lubashu-open-platform.postman_collection.json",
                "lubashu-open-platform.postman_environment.json", "README.md");
    }

    @Test
    void rejectsNonHttpBaseUrl() {
        assertThatThrownBy(() -> service.build("javascript:alert(1)"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP(S)");
    }
}
