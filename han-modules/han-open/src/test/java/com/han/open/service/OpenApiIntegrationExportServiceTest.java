package com.han.open.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.dto.OpenApiIntegrationExportDTO;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiResourceVersionPo;
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.mapper.OpenAppResourceGrantMapper;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiResourceVersionMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final OpenAppResourceGrantMapper grantMapper = mock(OpenAppResourceGrantMapper.class);
    private final OpenApiIntegrationExportService service =
            new OpenApiIntegrationExportService(resourceMapper, versionMapper, grantMapper, objectMapper);

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
        version.setResponseExamplesJson("{\"200\":{\"code\":200,\"data\":{\"liveStatus\":1,\"recordUrl\":null}}}");
        version.setErrorExamplesJson("{\"401\":{\"code\":401,\"message\":\"unauthorized\",\"accessToken\":\"must-not-leak\"}}");
        OpenApiResourceVersionPo obsolete = new OpenApiResourceVersionPo();
        obsolete.setId(1L);
        obsolete.setResourceId(1L);
        obsolete.setStatus(1);
        obsolete.setDelFlag(0);
        obsolete.setVersion("obsolete");
        obsolete.setOpenapiJson("{\"openapi\":\"3.0.3\",\"paths\":{\"/obsolete\":{\"get\":{\"responses\":{\"200\":{\"description\":\"old\"}}}}}}");

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(versionMapper.selectList(any())).thenReturn(List.of(version, obsolete));

        OpenApiIntegrationExportDTO export = service.build("https://example.test/");
        JsonNode openApi = objectMapper.readTree(export.openApiJson());
        JsonNode operation = openApi.path("paths").path(resource.getPath()).path("get");
        assertThat(operation.path("parameters")).anyMatch(parameter ->
                parameter.path("name").asText().equals("roomId") && parameter.path("in").asText().equals("query"));
        assertThat(operation.path("responses").path("200").path("content").path("application/json")
                .path("examples").path("200").path("value").path("data").path("liveStatus").asInt())
                .as(operation.toPrettyString()).isEqualTo(1);
        assertThat(operation.path("responses").path("200").path("content").path("application/json")
                .path("schema").path("properties").path("data").path("properties").path("recordUrl")
                .path("type").asText()).isEqualTo("string");

        String collection = new String(export.postmanCollectionJson(), StandardCharsets.UTF_8);
        String environment = new String(export.postmanEnvironmentJson(), StandardCharsets.UTF_8);
        assertThat(collection).contains("pm.sendRequest", "/open/oauth2/token", "{{accessToken}}",
                        "roomId=room-001", "Business code is 200")
                .doesNotContain("must-not-leak");
        assertThat(environment).contains("\"clientSecret\"").doesNotContain("must-not-leak");

        List<String> entries = new ArrayList<>();
        Map<String, String> contents = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(export.zip()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                contents.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        String root = "鲁巴开放平台通用对接/";
        assertThat(entries).contains(root + "README.md", root + "API导入文件/openapi.json",
                root + "API导入文件/postman_collection.json", root + "API导入文件/postman_environment.json",
                root + "文档/02-鉴权与Scope说明.md", root + "文档/04-完整接口参考.md", root + "文档/03-接口清单.csv",
                root + "示例项目/cURL/调用全部已授权接口.sh", root + "示例项目/Python/src/open_platform_demo.py",
                root + "示例项目/Node.js/src/open-platform-demo.mjs",
                root + "示例项目/Java/pom.xml",
                root + "示例项目/Java/src/main/java/com/lubashu/openplatform/demo/OpenPlatformDemo.java",
                root + "示例项目/Go/go.mod", root + "示例项目/Go/cmd/demo/main.go",
                root + "文档/接口示例/classroom.live-status.read.请求.json");
        assertThat(contents.get(root + "README.md")).contains("通用目录", "当前已启用、已发布接口", "示例项目", "Java");
        assertThat(contents.get(root + "文档/02-鉴权与Scope说明.md"))
                .contains("多个 Scope 使用空格分隔", "scope=classroom.live.read",
                        "\\\n", "-H 'Content-Type: application/x-www-form-urlencoded'");
        assertThat(contents.get(root + "文档/04-完整接口参考.md")).contains(resource.getPath(), resource.getScopeCode());
        assertThat(contents.get(root + "示例项目/Python/src/open_platform_demo.py"))
                .contains("OPEN_PLATFORM_CLIENT_SECRET", "/open/oauth2/token", "classroom_live_status_read");
        assertThat(contents.get(root + "示例项目/cURL/调用全部已授权接口.sh"))
                .contains("\\\n", "Content-Type: $CONTENT_TYPE", "classroom_live_status_read");
        assertThat(contents.get(root + "示例项目/Java/src/main/java/com/lubashu/openplatform/demo/OpenPlatformDemo.java"))
                .contains("static final String CLIENT_ID = \"\"", "classroomLiveStatusRead");
        assertThat(contents.values()).allMatch(value -> !value.contains("must-not-leak"));
        assertThat(contents.get(root + "API导入文件/openapi.json")).doesNotContain("/obsolete");
        Files.write(Path.of("target", "generated-integration-package.zip"), export.zip());
    }

    @Test
    void applicationPackageContainsOnlyEffectiveAuthorizedResources() throws Exception {
        OpenApiResourcePo authorized = resource(1L, "classroom.app-upgrade.read", "查询应用升级信息", "classroom.app.read");
        OpenApiResourcePo unauthorized = resource(2L, "directory.teachers", "教师目录", "edu.teacher.read");
        OpenApiResourceVersionPo authorizedVersion = version(11L, 1L, authorized.getPath());
        OpenApiResourceVersionPo unauthorizedVersion = version(12L, 2L, unauthorized.getPath());
        OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
        grant.setResourceId(1L);
        grant.setScopes("classroom.app.read");
        when(grantMapper.selectList(any())).thenReturn(List.of(grant));
        when(resourceMapper.selectList(any())).thenReturn(List.of(authorized, unauthorized));
        when(versionMapper.selectList(any())).thenReturn(List.of(authorizedVersion, unauthorizedVersion));
        OpenAppVO app = new OpenAppVO();
        app.setAppId(201L);
        app.setTenantId(99L);
        app.setAppName("视频平台");
        app.setScopes(List.of("classroom.app.read", "edu.teacher.read"));

        OpenApiIntegrationExportDTO export = service.buildForApp("https://example.test", app, "PROD");

        assertThat(export.filenameBase()).isEqualTo("视频平台应用对接-生产");
        String openApi = new String(export.openApiJson(), StandardCharsets.UTF_8);
        assertThat(openApi).contains(authorized.getPath()).doesNotContain(unauthorized.getPath());
        assertThat(new String(export.postmanEnvironmentJson(), StandardCharsets.UTF_8))
                .contains("classroom.app.read").doesNotContain("edu.teacher.read");
    }

    @Test
    void rejectsNonHttpBaseUrl() {
        assertThatThrownBy(() -> service.build("javascript:alert(1)"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP(S)");
    }

    private static OpenApiResourcePo resource(long id, String code, String name, String scope) {
        OpenApiResourcePo resource = new OpenApiResourcePo();
        resource.setId(id);
        resource.setResourceCode(code);
        resource.setResourceName(name);
        resource.setCategory("测试");
        resource.setHttpMethod("GET");
        resource.setPath("/open/api/v1/" + code.replace('.', '/'));
        resource.setScopeCode(scope);
        resource.setStatus(0);
        resource.setPublishStatus(2);
        return resource;
    }

    private static OpenApiResourceVersionPo version(long id, long resourceId, String path) {
        OpenApiResourceVersionPo version = new OpenApiResourceVersionPo();
        version.setId(id);
        version.setResourceId(resourceId);
        version.setStatus(1);
        version.setDelFlag(0);
        version.setVersion("v1");
        version.setOpenapiJson("{\"openapi\":\"3.0.3\",\"paths\":{\"" + path
                + "\":{\"get\":{\"responses\":{\"200\":{\"description\":\"成功\"}}}}}}");
        version.setRequestExampleJson("{}");
        version.setResponseExamplesJson("{\"code\":200}");
        return version;
    }
}
