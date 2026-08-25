package com.han.open.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.dto.OpenApiIntegrationExportDTO;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiResourceVersionPo;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiResourceVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将已上线目录汇总为 OpenAPI 和可直接执行的 Postman 2.1 对接包。
 */
@Service
@RequiredArgsConstructor
public class OpenApiIntegrationExportService {

    private static final int RESOURCE_ENABLED = 0;
    private static final int RESOURCE_PUBLISHED = 2;
    private static final int VERSION_PUBLISHED = 1;
    private static final Pattern HTTP_STATUS = Pattern.compile("[1-5]\\d{2}");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "DELETE", "HEAD");

    private final OpenApiResourceMapper resourceMapper;
    private final OpenApiResourceVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public OpenApiIntegrationExportDTO build(String rawBaseUrl) {
        String baseUrl = normalizeBaseUrl(rawBaseUrl);
        List<ResourceDocument> documents = loadPublishedDocuments();
        ObjectNode openApi = buildOpenApi(baseUrl, documents);
        ObjectNode collection = buildPostmanCollection(baseUrl, documents, openApi);
        ObjectNode environment = buildPostmanEnvironment(baseUrl);
        byte[] openApiBytes = json(openApi);
        byte[] collectionBytes = json(collection);
        byte[] environmentBytes = json(environment);
        byte[] readmeBytes = readme(baseUrl).getBytes(StandardCharsets.UTF_8);
        return new OpenApiIntegrationExportDTO(openApiBytes, collectionBytes, environmentBytes, readmeBytes,
                zip(openApiBytes, collectionBytes, environmentBytes, readmeBytes));
    }

    String normalizeBaseUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String normalized = uri.toString();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("导出基础地址必须是合法的 HTTP(S) 地址");
        }
    }

    private List<ResourceDocument> loadPublishedDocuments() {
        List<OpenApiResourcePo> resources = resourceMapper.selectList(
                new LambdaQueryWrapper<OpenApiResourcePo>()
                        .eq(OpenApiResourcePo::getStatus, RESOURCE_ENABLED)
                        .eq(OpenApiResourcePo::getPublishStatus, RESOURCE_PUBLISHED)
                        .orderByAsc(OpenApiResourcePo::getSort)
                        .orderByAsc(OpenApiResourcePo::getId));
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        List<Long> resourceIds = resources.stream().map(OpenApiResourcePo::getId).toList();
        List<OpenApiResourceVersionPo> versions = versionMapper.selectList(
                new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                        .in(OpenApiResourceVersionPo::getResourceId, resourceIds)
                        .eq(OpenApiResourceVersionPo::getStatus, VERSION_PUBLISHED)
                        .eq(OpenApiResourceVersionPo::getDelFlag, 0)
                        .orderByDesc(OpenApiResourceVersionPo::getPublishedAt)
                        .orderByDesc(OpenApiResourceVersionPo::getId));
        Map<Long, OpenApiResourceVersionPo> currentVersions = new LinkedHashMap<>();
        if (versions != null) {
            versions.forEach(version -> currentVersions.putIfAbsent(version.getResourceId(), version));
        }
        return resources.stream()
                .filter(resource -> currentVersions.containsKey(resource.getId()))
                .map(resource -> new ResourceDocument(resource, currentVersions.get(resource.getId())))
                .toList();
    }

    private ObjectNode buildOpenApi(String baseUrl, List<ResourceDocument> documents) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.0.3");
        ObjectNode info = root.putObject("info");
        info.put("title", "鲁巴教育开放平台接口");
        info.put("description", "公开接口定义不包含应用密钥；调用前请申请接口授权并使用 client_credentials 换取 Access Token。");
        info.put("version", "1.0.0");
        root.putArray("servers").addObject().put("url", baseUrl);
        ObjectNode paths = root.putObject("paths");
        ObjectNode schemas = root.putObject("components").putObject("schemas");
        Set<String> scopes = new LinkedHashSet<>();

        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            ObjectNode sourceRoot = parseObject(document.version().getOpenapiJson());
            mergeSchemas(sourceRoot, schemas);
            String method = resource.getHttpMethod().toLowerCase(Locale.ROOT);
            ObjectNode sourcePath = sourceRoot == null ? null : object(sourceRoot.path("paths").path(resource.getPath()));
            ObjectNode sourceOperation = sourcePath == null ? null : object(sourcePath.path(method));
            ObjectNode pathItem = paths.withObject("/" + escapePointer(resource.getPath()));
            if (sourcePath != null && sourcePath.path("parameters").isArray() && !pathItem.has("parameters")) {
                pathItem.set("parameters", sanitizeExamples(sourcePath.path("parameters")));
            }
            ObjectNode operation = sourceOperation == null
                    ? objectMapper.createObjectNode() : (ObjectNode) sanitizeExamples(sourceOperation);
            pathItem.set(method, operation);
            putIfBlank(operation, "operationId", resource.getResourceCode());
            putIfBlank(operation, "summary", resource.getResourceName());
            putIfBlank(operation, "description", resource.getDescription());
            if (!operation.path("tags").isArray()) {
                operation.putArray("tags").add(defaultText(resource.getCategory(), "其他"));
            }
            operation.set("security", objectMapper.createArrayNode().add(
                    objectMapper.createObjectNode().set("oauth2", objectMapper.createArrayNode().add(resource.getScopeCode()))));
            scopes.add(resource.getScopeCode());
            JsonNode requestExample = redactPayload(parse(document.version().getRequestExampleJson()));
            applyRequestExample(resource, operation, requestExample);
            applyResponseExamples(operation, redactPayload(parse(document.version().getResponseExamplesJson())), "200", "成功");
            applyResponseExamples(operation, redactPayload(parse(document.version().getErrorExamplesJson())), "400", "失败");
            if (!operation.path("responses").isObject() || operation.path("responses").isEmpty()) {
                operation.putObject("responses").putObject("200").put("description", "成功");
            }
        }

        ObjectNode oauth2 = root.withObject("/components/securitySchemes").putObject("oauth2");
        oauth2.put("type", "oauth2");
        ObjectNode clientCredentials = oauth2.putObject("flows").putObject("clientCredentials");
        clientCredentials.put("tokenUrl", baseUrl + "/open/oauth2/token");
        ObjectNode scopeNode = clientCredentials.putObject("scopes");
        scopes.forEach(scope -> scopeNode.put(scope, scope));
        return root;
    }

    private void mergeSchemas(ObjectNode sourceRoot, ObjectNode target) {
        if (sourceRoot == null || !sourceRoot.path("components").path("schemas").isObject()) {
            return;
        }
        sourceRoot.path("components").path("schemas").fields().forEachRemaining(entry -> {
            if (!target.has(entry.getKey())) {
                target.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private void applyRequestExample(OpenApiResourcePo resource, ObjectNode operation, JsonNode requestExample) {
        if (requestExample == null || requestExample.isNull() || requestExample.isMissingNode()) {
            return;
        }
        if (usesQueryParameters(resource, operation) && requestExample.isObject()) {
            ArrayNode parameters = operation.path("parameters").isArray()
                    ? (ArrayNode) operation.path("parameters") : operation.putArray("parameters");
            Set<String> existing = new LinkedHashSet<>();
            parameters.forEach(parameter -> existing.add(parameter.path("in").asText() + ":" + parameter.path("name").asText()));
            requestExample.fields().forEachRemaining(entry -> {
                String location = resource.getPath().contains("{" + entry.getKey() + "}") ? "path" : "query";
                String key = location + ":" + entry.getKey();
                if (existing.add(key)) {
                    ObjectNode parameter = parameters.addObject();
                    parameter.put("name", entry.getKey());
                    parameter.put("in", location);
                    parameter.put("required", "path".equals(location));
                    parameter.set("schema", inferSchema(entry.getValue()));
                    parameter.set("example", entry.getValue());
                }
            });
            fillDeclaredParameterExamples(parameters, requestExample);
            return;
        }
        ObjectNode requestBody = operation.path("requestBody").isObject()
                ? (ObjectNode) operation.path("requestBody") : operation.putObject("requestBody");
        requestBody.putIfAbsent("required", objectMapper.getNodeFactory().booleanNode(true));
        ObjectNode media = requestBody.withObject("/content/application~1json");
        if (!media.has("schema")) {
            media.set("schema", inferSchema(requestExample));
        }
        if (!media.has("example")) {
            media.set("example", requestExample);
        }
    }

    private boolean usesQueryParameters(OpenApiResourcePo resource, ObjectNode operation) {
        if (SAFE_METHODS.contains(resource.getHttpMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        if (operation.path("parameters").isArray() && !operation.path("parameters").isEmpty()
                && !operation.path("requestBody").isObject()) {
            return true;
        }
        return resource.getPath().startsWith("/open/api/v1/classroom/")
                && !resource.getPath().endsWith("/tb-course-info/saveCourseInfo");
    }

    private void fillDeclaredParameterExamples(ArrayNode parameters, JsonNode requestExample) {
        parameters.forEach(parameter -> {
            if (!(parameter instanceof ObjectNode objectParameter)) {
                return;
            }
            String name = objectParameter.path("name").asText();
            JsonNode value = requestExample.path(name);
            if (!value.isMissingNode() && !objectParameter.has("example")) {
                objectParameter.set("example", sensitive(name) ? TextNode.valueOf("") : value);
            }
        });
    }

    private void applyResponseExamples(ObjectNode operation, JsonNode examples, String defaultStatus, String description) {
        if (examples == null || examples.isNull() || examples.isMissingNode()) {
            return;
        }
        if (examples.isObject() && hasOnlyStatusKeys(examples)) {
            examples.fields().forEachRemaining(entry -> addResponseExample(operation, entry.getKey(), entry.getKey(), entry.getValue(), description));
        } else if (examples.isObject() && !looksLikePayload(examples)) {
            examples.fields().forEachRemaining(entry -> addResponseExample(operation, defaultStatus, entry.getKey(), entry.getValue(), description));
        } else {
            addResponseExample(operation, defaultStatus, "example", examples, description);
        }
    }

    private void addResponseExample(ObjectNode operation, String status, String name, JsonNode value, String description) {
        ObjectNode responses = operation.path("responses").isObject()
                ? (ObjectNode) operation.path("responses") : operation.putObject("responses");
        ObjectNode response = responses.path(status).isObject()
                ? (ObjectNode) responses.path(status) : responses.putObject(status);
        putIfBlank(response, "description", description);
        ObjectNode media = response.withObject("/content/application~1json");
        if (!media.has("schema")) {
            media.set("schema", inferSchema(value));
        }
        ObjectNode examples = media.path("examples").isObject()
                ? (ObjectNode) media.path("examples") : media.putObject("examples");
        ObjectNode example = examples.path(safeExampleName(name)).isObject()
                ? (ObjectNode) examples.path(safeExampleName(name)) : examples.putObject(safeExampleName(name));
        example.set("value", value);
    }

    private ObjectNode inferSchema(JsonNode value) {
        ObjectNode schema = objectMapper.createObjectNode();
        if (value == null || value.isNull() || value.isMissingNode()) {
            schema.put("type", "string");
            schema.put("nullable", true);
        } else if (value.isObject()) {
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            value.fields().forEachRemaining(entry -> properties.set(entry.getKey(), inferSchema(entry.getValue())));
        } else if (value.isArray()) {
            schema.put("type", "array");
            schema.set("items", value.isEmpty() ? objectMapper.createObjectNode() : inferSchema(value.get(0)));
        } else if (value.isIntegralNumber()) {
            schema.put("type", "integer");
            schema.put("format", "int64");
            schema.set("example", value);
        } else if (value.isFloatingPointNumber()) {
            schema.put("type", "number");
            schema.put("format", "double");
            schema.set("example", value);
        } else if (value.isBoolean()) {
            schema.put("type", "boolean");
            schema.set("example", value);
        } else {
            schema.put("type", "string");
            schema.set("example", value);
        }
        return schema;
    }

    private ObjectNode buildPostmanCollection(String baseUrl, List<ResourceDocument> documents, ObjectNode openApi) {
        ObjectNode collection = objectMapper.createObjectNode();
        ObjectNode info = collection.putObject("info");
        info.put("_postman_id", UUID.nameUUIDFromBytes(("collection:" + baseUrl).getBytes(StandardCharsets.UTF_8)).toString());
        info.put("name", "鲁巴教育开放平台（可执行）");
        info.put("description", "导入后填写环境变量 clientId/clientSecret。集合级脚本会自动获取并缓存 Access Token。");
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.set("auth", bearerAuth());
        collection.set("event", collectionEvents());
        ArrayNode variables = collection.putArray("variable");
        addVariable(variables, "baseUrl", baseUrl, "string");
        addVariable(variables, "clientId", "", "string");
        addVariable(variables, "clientSecret", "", "string");
        addVariable(variables, "accessToken", "", "string");
        addVariable(variables, "tokenExpiresAt", "0", "string");

        Map<String, ArrayNode> groups = new LinkedHashMap<>();
        ArrayNode items = collection.putArray("item");
        for (ResourceDocument document : documents) {
            String category = defaultText(document.resource().getCategory(), "其他");
            ArrayNode group = groups.computeIfAbsent(category, ignored -> {
                ObjectNode folder = items.addObject();
                folder.put("name", category);
                return folder.putArray("item");
            });
            ObjectNode operation = object(openApi.path("paths").path(document.resource().getPath())
                    .path(document.resource().getHttpMethod().toLowerCase(Locale.ROOT)));
            group.add(buildPostmanItem(document, operation == null ? objectMapper.createObjectNode() : operation));
        }
        return collection;
    }

    private ObjectNode buildPostmanItem(ResourceDocument document, ObjectNode operation) {
        OpenApiResourcePo resource = document.resource();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", resource.getResourceName());
        ObjectNode request = item.putObject("request");
        request.put("method", resource.getHttpMethod().toUpperCase(Locale.ROOT));
        request.put("description", defaultText(resource.getDescription(), "") + "\n\nScope: " + resource.getScopeCode());
        ArrayNode headers = request.putArray("header");
        ObjectNode url = request.putObject("url");
        String path = resource.getPath();
        ArrayNode query = url.putArray("query");
        ArrayNode pathVariables = url.putArray("variable");
        if (operation.path("parameters").isArray()) {
            for (JsonNode parameter : operation.path("parameters")) {
                String name = parameter.path("name").asText();
                String location = parameter.path("in").asText();
                String value = parameterValue(parameter);
                if ("query".equals(location)) {
                    ObjectNode entry = query.addObject();
                    entry.put("key", name);
                    entry.put("value", value);
                    putIfBlank(entry, "description", parameter.path("description").asText());
                } else if ("header".equals(location) && !"authorization".equalsIgnoreCase(name)) {
                    ObjectNode entry = headers.addObject();
                    entry.put("key", name);
                    entry.put("value", value);
                    entry.put("type", "text");
                } else if ("path".equals(location)) {
                    path = path.replace("{" + name + "}", ":" + name);
                    ObjectNode entry = pathVariables.addObject();
                    entry.put("key", name);
                    entry.put("value", value);
                }
            }
        }
        url.put("raw", "{{baseUrl}}" + path + rawQuery(query));
        url.putArray("host").add("{{baseUrl}}");
        ArrayNode pathParts = url.putArray("path");
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                pathParts.add(part);
            }
        }
        applyPostmanBody(request, operation);
        item.set("event", testEvents());
        item.set("response", postmanResponses(document, request));
        return item;
    }

    private void applyPostmanBody(ObjectNode request, ObjectNode operation) {
        JsonNode content = operation.path("requestBody").path("content");
        if (!content.isObject() || content.isEmpty()) {
            return;
        }
        Iterator<String> mediaTypes = content.fieldNames();
        String mediaType = content.has("application/json") ? "application/json" : mediaTypes.next();
        JsonNode media = content.path(mediaType);
        JsonNode example = media.has("example") ? media.path("example") : exampleFromSchema(media.path("schema"));
        ObjectNode body = request.putObject("body");
        if ("application/x-www-form-urlencoded".equalsIgnoreCase(mediaType) && example.isObject()) {
            body.put("mode", "urlencoded");
            ArrayNode form = body.putArray("urlencoded");
            example.fields().forEachRemaining(entry -> {
                ObjectNode field = form.addObject();
                field.put("key", entry.getKey());
                field.put("value", text(entry.getValue()));
                field.put("type", "text");
            });
        } else {
            body.put("mode", "raw");
            body.put("raw", pretty(example));
            body.putObject("options").putObject("raw").put("language", "json");
        }
    }

    private ArrayNode postmanResponses(ResourceDocument document, ObjectNode request) {
        ArrayNode result = objectMapper.createArrayNode();
        appendPostmanResponses(result, redactPayload(parse(document.version().getResponseExamplesJson())), 200, request);
        appendPostmanResponses(result, redactPayload(parse(document.version().getErrorExamplesJson())), 400, request);
        return result;
    }

    private void appendPostmanResponses(ArrayNode target, JsonNode examples, int defaultStatus, ObjectNode request) {
        if (examples == null || examples.isNull() || examples.isMissingNode()) {
            return;
        }
        if (examples.isObject() && hasOnlyStatusKeys(examples)) {
            examples.fields().forEachRemaining(entry -> addPostmanResponse(target, entry.getKey(), entry.getValue(),
                    Integer.parseInt(entry.getKey()), request));
        } else if (examples.isObject() && !looksLikePayload(examples)) {
            examples.fields().forEachRemaining(entry -> addPostmanResponse(target, entry.getKey(), entry.getValue(), defaultStatus, request));
        } else {
            addPostmanResponse(target, defaultStatus == 200 ? "成功示例" : "错误示例", examples, defaultStatus, request);
        }
    }

    private void addPostmanResponse(ArrayNode target, String name, JsonNode value, int status, ObjectNode request) {
        ObjectNode response = target.addObject();
        response.put("name", name);
        response.set("originalRequest", request.deepCopy());
        response.put("status", statusText(status));
        response.put("code", status);
        response.putArray("header").addObject().put("key", "Content-Type").put("value", "application/json");
        response.put("_postman_previewlanguage", "json");
        response.put("body", pretty(value));
    }

    private ObjectNode buildPostmanEnvironment(String baseUrl) {
        ObjectNode environment = objectMapper.createObjectNode();
        environment.put("id", UUID.nameUUIDFromBytes(("environment:" + baseUrl).getBytes(StandardCharsets.UTF_8)).toString());
        environment.put("name", "鲁巴教育开放平台环境");
        ArrayNode values = environment.putArray("values");
        addEnvironmentValue(values, "baseUrl", baseUrl, "default");
        addEnvironmentValue(values, "clientId", "", "default");
        addEnvironmentValue(values, "clientSecret", "", "secret");
        environment.put("_postman_variable_scope", "environment");
        environment.put("_postman_exported_at", Instant.now().toString());
        environment.put("_postman_exported_using", "鲁巴教育开放平台");
        return environment;
    }

    private ArrayNode collectionEvents() {
        String script = """
                const baseUrl = (pm.environment.get('baseUrl') || pm.collectionVariables.get('baseUrl') || '').replace(/\\/$/, '');
                const clientId = pm.environment.get('clientId') || pm.collectionVariables.get('clientId');
                const clientSecret = pm.environment.get('clientSecret') || pm.collectionVariables.get('clientSecret');
                const accessToken = pm.collectionVariables.get('accessToken');
                const expiresAt = Number(pm.collectionVariables.get('tokenExpiresAt') || 0);
                if (!baseUrl || !clientId || !clientSecret) {
                  throw new Error('请先在环境中填写 baseUrl、clientId 和 clientSecret');
                }
                if (!accessToken || Date.now() + 30000 >= expiresAt) {
                  pm.sendRequest({
                    url: baseUrl + '/open/oauth2/token',
                    method: 'POST',
                    header: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: { mode: 'urlencoded', urlencoded: [
                      { key: 'grant_type', value: 'client_credentials' },
                      { key: 'client_id', value: clientId },
                      { key: 'client_secret', value: clientSecret }
                    ] }
                  }, (error, response) => {
                    if (error) throw error;
                    if (response.code < 200 || response.code >= 300) throw new Error('获取 Access Token 失败: HTTP ' + response.code);
                    const payload = response.json();
                    if (!payload.access_token) throw new Error('获取 Access Token 失败: 响应中没有 access_token');
                    pm.collectionVariables.set('accessToken', payload.access_token);
                    pm.collectionVariables.set('tokenExpiresAt', String(Date.now() + Number(payload.expires_in || 7200) * 1000));
                  });
                }
                """;
        return event("prerequest", script);
    }

    private ArrayNode testEvents() {
        return event("test", """
                pm.test('HTTP status is 2xx', () => pm.expect(pm.response.code).to.be.within(200, 299));
                let payload = null;
                try { payload = pm.response.json(); } catch (ignored) {}
                if (payload && Object.prototype.hasOwnProperty.call(payload, 'success')) {
                  pm.test('Business success is true', () => pm.expect(payload.success).to.eql(true));
                }
                if (payload && Object.prototype.hasOwnProperty.call(payload, 'code')) {
                  pm.test('Business code is 200', () => pm.expect(String(payload.code)).to.eql('200'));
                }
                """);
    }

    private ArrayNode event(String listen, String script) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("listen", listen);
        ObjectNode scriptNode = event.putObject("script");
        scriptNode.put("type", "text/javascript");
        scriptNode.set("exec", objectMapper.valueToTree(script.lines().toList()));
        return objectMapper.createArrayNode().add(event);
    }

    private ObjectNode bearerAuth() {
        ObjectNode auth = objectMapper.createObjectNode();
        auth.put("type", "bearer");
        ObjectNode token = auth.putArray("bearer").addObject();
        token.put("key", "token");
        token.put("value", "{{accessToken}}");
        token.put("type", "string");
        return auth;
    }

    private void addVariable(ArrayNode variables, String key, String value, String type) {
        ObjectNode variable = variables.addObject();
        variable.put("key", key);
        variable.put("value", value);
        variable.put("type", type);
    }

    private void addEnvironmentValue(ArrayNode values, String key, String value, String type) {
        ObjectNode variable = values.addObject();
        variable.put("key", key);
        variable.put("value", value);
        variable.put("type", type);
        variable.put("enabled", true);
    }

    private byte[] zip(byte[] openApi, byte[] collection, byte[] environment, byte[] readme) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeZipEntry(zip, "openapi.json", openApi);
            writeZipEntry(zip, "lubashu-open-platform.postman_collection.json", collection);
            writeZipEntry(zip, "lubashu-open-platform.postman_environment.json", environment);
            writeZipEntry(zip, "README.md", readme);
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("对接包生成失败", ex);
        }
    }

    private void writeZipEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private String readme(String baseUrl) {
        return """
                # 鲁巴教育开放平台对接包

                - `openapi.json`：公开 OpenAPI 3.0.3 文档，不含任何应用密钥，可导入 Apifox、ApiPost、Postman。
                - `*.postman_collection.json`：Postman Collection v2.1，包含获取和缓存 Access Token 的集合级脚本。
                - `*.postman_environment.json`：环境模板，`clientId` 和 `clientSecret` 故意留空。

                ## 直接调测

                1. 导入 Collection 和 Environment，并选中该环境。
                2. 填写同一应用、同一环境的 `clientId` / `clientSecret`。
                3. 运行已授权的接口；脚本会调用 `%s/open/oauth2/token` 换取 Bearer Token。

                公开文档不需要密钥，真实接口调用必须使用已开通对应 Scope 的应用凭证。
                """.formatted(baseUrl);
    }

    private byte[] json(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("对接文档序列化失败", ex);
        }
    }

    private ObjectNode parseObject(String json) {
        JsonNode node = parse(json);
        return object(node);
    }

    private JsonNode parse(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private ObjectNode object(JsonNode node) {
        return node instanceof ObjectNode objectNode ? objectNode : null;
    }

    private JsonNode sanitizeExamples(JsonNode node) {
        JsonNode copy = node.deepCopy();
        sanitizeExampleFields(copy);
        return copy;
    }

    private void sanitizeExampleFields(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            object.fields().forEachRemaining(fields::add);
            for (Map.Entry<String, JsonNode> field : fields) {
                if ("example".equals(field.getKey()) || "value".equals(field.getKey())) {
                    object.set(field.getKey(), redactPayload(field.getValue()));
                } else {
                    sanitizeExampleFields(field.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(this::sanitizeExampleFields);
        }
    }

    private JsonNode redactPayload(JsonNode node) {
        if (node == null) {
            return null;
        }
        JsonNode copy = node.deepCopy();
        redactPayloadInPlace(copy);
        return copy;
    }

    private void redactPayloadInPlace(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            object.fields().forEachRemaining(fields::add);
            for (Map.Entry<String, JsonNode> field : fields) {
                if (sensitive(field.getKey())) {
                    object.put(field.getKey(), "");
                } else {
                    redactPayloadInPlace(field.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(this::redactPayloadInPlace);
        }
    }

    private boolean sensitive(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.contains("secret") || normalized.contains("password")
                || normalized.contains("token") || normalized.contains("appkey");
    }

    private JsonNode exampleFromSchema(JsonNode schema) {
        if (schema == null || schema.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        if (schema.has("example")) {
            return redactPayload(schema.path("example"));
        }
        if (schema.has("default")) {
            return redactPayload(schema.path("default"));
        }
        return switch (schema.path("type").asText()) {
            case "object" -> {
                ObjectNode value = objectMapper.createObjectNode();
                schema.path("properties").fields().forEachRemaining(entry ->
                        value.set(entry.getKey(), sensitive(entry.getKey()) ? TextNode.valueOf("") : exampleFromSchema(entry.getValue())));
                yield value;
            }
            case "array" -> objectMapper.createArrayNode().add(exampleFromSchema(schema.path("items")));
            case "integer", "number" -> objectMapper.getNodeFactory().numberNode(0);
            case "boolean" -> objectMapper.getNodeFactory().booleanNode(false);
            default -> TextNode.valueOf("");
        };
    }

    private String parameterValue(JsonNode parameter) {
        String name = parameter.path("name").asText();
        if (sensitive(name)) {
            return "";
        }
        JsonNode value = parameter.has("example") ? parameter.path("example")
                : parameter.path("schema").has("example") ? parameter.path("schema").path("example")
                : parameter.path("schema").path("default");
        return value.isMissingNode() || value.isNull() ? "" : text(value);
    }

    private String rawQuery(ArrayNode query) {
        if (query.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        query.forEach(entry -> values.add(entry.path("key").asText() + "=" + entry.path("value").asText()));
        return "?" + String.join("&", values);
    }

    private boolean hasOnlyStatusKeys(JsonNode node) {
        if (!node.isObject() || node.isEmpty()) {
            return false;
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!HTTP_STATUS.matcher(names.next()).matches()) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikePayload(JsonNode node) {
        return node.has("code") || node.has("msg") || node.has("message") || node.has("data")
                || node.has("success") || node.has("result") || node.has("timestamp");
    }

    private String statusText(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            default -> "HTTP " + status;
        };
    }

    private String pretty(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return node == null ? "" : node.toString();
        }
    }

    private String text(JsonNode node) {
        return node != null && node.isValueNode() ? node.asText() : node == null ? "" : node.toString();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String safeExampleName(String value) {
        return StringUtils.hasText(value) ? value : "example";
    }

    private String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private void putIfBlank(ObjectNode target, String field, String value) {
        if ((!target.has(field) || !StringUtils.hasText(target.path(field).asText())) && StringUtils.hasText(value)) {
            target.put(field, value);
        }
    }

    private record ResourceDocument(OpenApiResourcePo resource, OpenApiResourceVersionPo version) {
    }
}
