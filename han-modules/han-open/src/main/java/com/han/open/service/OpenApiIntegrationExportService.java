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
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.mapper.OpenAppResourceGrantMapper;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiResourceVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
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
    private final OpenAppResourceGrantMapper grantMapper;
    private final ObjectMapper objectMapper;

    public OpenApiIntegrationExportDTO build(String rawBaseUrl) {
        String baseUrl = normalizeBaseUrl(rawBaseUrl);
        List<ResourceDocument> documents = loadPublishedDocuments(null);
        return buildExport(baseUrl, documents, "鲁巴开放平台通用对接", "通用目录", scopes(documents));
    }

    public OpenApiIntegrationExportDTO buildForApp(String rawBaseUrl, OpenAppVO app, String rawEnvironment) {
        if (app == null || app.getAppId() == null) {
            throw new BusinessException("导出应用不能为空");
        }
        if (grantMapper == null) {
            throw new BusinessException("应用授权查询未配置");
        }
        String environment = normalizeEnvironment(rawEnvironment);
        List<OpenAppResourceGrantPo> grants = grantMapper.selectList(
                new LambdaQueryWrapper<OpenAppResourceGrantPo>()
                        .eq(app.getTenantId() != null, OpenAppResourceGrantPo::getTenantId, app.getTenantId())
                        .eq(OpenAppResourceGrantPo::getAppId, app.getAppId())
                        .eq(OpenAppResourceGrantPo::getEnvironment, environment)
                        .eq(OpenAppResourceGrantPo::getStatus, 1)
                        .eq(OpenAppResourceGrantPo::getDelFlag, 0)
                        .and(value -> value.isNull(OpenAppResourceGrantPo::getExpiresAt)
                                .or().gt(OpenAppResourceGrantPo::getExpiresAt, LocalDateTime.now())));
        Map<Long, Set<String>> grantScopes = new LinkedHashMap<>();
        if (grants != null) {
            grants.forEach(grant -> grantScopes.put(grant.getResourceId(), splitScopes(grant.getScopes())));
        }
        Set<String> appScopes = app.getScopes() == null ? Set.of() : new LinkedHashSet<>(app.getScopes());
        List<ResourceDocument> documents = loadPublishedDocuments(grantScopes.keySet()).stream()
                .filter(document -> appScopes.contains(document.resource().getScopeCode()))
                .filter(document -> grantScopes.getOrDefault(document.resource().getId(), Set.of())
                        .contains(document.resource().getScopeCode()))
                .toList();
        if (documents.isEmpty()) {
            throw new BusinessException("当前应用在所选环境没有有效授权接口");
        }
        String environmentName = "PROD".equals(environment) ? "生产" : "沙箱";
        String filename = safeExportName(app.getAppName()) + "应用对接-" + environmentName;
        return buildExport(normalizeBaseUrl(rawBaseUrl), documents, filename,
                app.getAppName() + " / " + environmentName, scopes(documents));
    }

    private OpenApiIntegrationExportDTO buildExport(String baseUrl, List<ResourceDocument> documents,
                                                     String filenameBase, String packageName, String scopeText) {
        ObjectNode openApi = buildOpenApi(baseUrl, documents);
        ObjectNode collection = buildPostmanCollection(baseUrl, documents, openApi, scopeText);
        ObjectNode environment = buildPostmanEnvironment(baseUrl, scopeText);
        byte[] openApiBytes = json(openApi);
        byte[] collectionBytes = json(collection);
        byte[] environmentBytes = json(environment);
        byte[] readmeBytes = readme(baseUrl, packageName, documents.size(), scopeText).getBytes(StandardCharsets.UTF_8);
        return new OpenApiIntegrationExportDTO(filenameBase, openApiBytes, collectionBytes, environmentBytes, readmeBytes,
                zip(baseUrl, filenameBase, packageName, scopeText, documents,
                        openApiBytes, collectionBytes, environmentBytes, readmeBytes));
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

    private List<ResourceDocument> loadPublishedDocuments(Set<Long> allowedIds) {
        if (allowedIds != null && allowedIds.isEmpty()) {
            return List.of();
        }
        List<OpenApiResourcePo> resources = resourceMapper.selectList(
                new LambdaQueryWrapper<OpenApiResourcePo>()
                        .in(allowedIds != null, OpenApiResourcePo::getId, allowedIds)
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

    private String normalizeEnvironment(String value) {
        String environment = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SANDBOX", "PROD").contains(environment)) {
            throw new BusinessException("导出环境只能是 SANDBOX 或 PROD");
        }
        return environment;
    }

    private Set<String> splitScopes(String value) {
        if (!StringUtils.hasText(value)) return Set.of();
        return java.util.Arrays.stream(value.trim().split("[\\s,]+"))
                .filter(StringUtils::hasText).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String scopes(List<ResourceDocument> documents) {
        return documents.stream().map(document -> document.resource().getScopeCode())
                .filter(StringUtils::hasText).distinct().collect(java.util.stream.Collectors.joining(" "));
    }

    private String safeExportName(String value) {
        String name = defaultText(value, "开放平台").replaceAll("[\\\\/:*?\"<>|\\r\\n]", "-").trim();
        return name.length() > 50 ? name.substring(0, 50) : name;
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

    private ObjectNode buildPostmanCollection(String baseUrl, List<ResourceDocument> documents,
                                              ObjectNode openApi, String scopeText) {
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
        addVariable(variables, "scopes", scopeText, "string");
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

    private ObjectNode buildPostmanEnvironment(String baseUrl, String scopeText) {
        ObjectNode environment = objectMapper.createObjectNode();
        environment.put("id", UUID.nameUUIDFromBytes(("environment:" + baseUrl).getBytes(StandardCharsets.UTF_8)).toString());
        environment.put("name", "鲁巴教育开放平台环境");
        ArrayNode values = environment.putArray("values");
        addEnvironmentValue(values, "baseUrl", baseUrl, "default");
        addEnvironmentValue(values, "clientId", "", "default");
        addEnvironmentValue(values, "clientSecret", "", "secret");
        addEnvironmentValue(values, "scopes", scopeText, "default");
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
                const scopes = pm.environment.get('scopes') || pm.collectionVariables.get('scopes') || '';
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
                      { key: 'client_secret', value: clientSecret },
                      { key: 'scope', value: scopes }
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

    private byte[] zip(String baseUrl, String filenameBase, String packageName, String scopeText,
                       List<ResourceDocument> documents, byte[] openApi,
                       byte[] collection, byte[] environment, byte[] readme) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            String root = filenameBase + "/";
            writeZipEntry(zip, root + "README.md", readme);
            writeZipEntry(zip, root + "API导入文件/openapi.json", openApi);
            writeZipEntry(zip, root + "API导入文件/postman_collection.json", collection);
            writeZipEntry(zip, root + "API导入文件/postman_environment.json", environment);
            writeZipEntry(zip, root + "文档/01-快速接入.md", quickStart(baseUrl, scopeText).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "文档/02-鉴权与Scope说明.md", authGuide(baseUrl, scopeText).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "文档/03-接口清单.csv", endpointCsv(documents).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "文档/04-完整接口参考.md", apiReference(documents).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/README.md", demoGuide(scopeText).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/配置示例.env", demoEnvironment(baseUrl, scopeText).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/cURL/README.md", languageGuide("cURL", "sh 调用全部已授权接口.sh").getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/cURL/调用全部已授权接口.sh", curlDemo(baseUrl, scopeText, documents).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Python/README.md", languageGuide("Python", "python -m unittest && python src/open_platform_demo.py").getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Python/pyproject.toml", pythonProject().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Python/src/open_platform_demo.py", pythonDemo(baseUrl, scopeText, documents).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Python/tests/test_open_platform_demo.py", pythonTest().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Node.js/README.md", languageGuide("Node.js", "npm test && npm start").getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Node.js/package.json", nodeProject().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Node.js/src/open-platform-demo.mjs", nodeDemo(baseUrl, scopeText, documents).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Node.js/test/open-platform-demo.test.mjs", nodeTest().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Java/README.md", languageGuide("Java", "mvn test && mvn exec:java").getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Java/pom.xml", javaProject().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Java/src/main/java/com/lubashu/openplatform/demo/OpenPlatformDemo.java", javaDemo(baseUrl, scopeText, documents).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Java/src/test/java/com/lubashu/openplatform/demo/OpenPlatformDemoTest.java", javaTest().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Go/README.md", languageGuide("Go", "go test ./... && go run ./cmd/demo").getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Go/go.mod", goProject().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Go/cmd/demo/main.go", goDemo(baseUrl, scopeText, documents).getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "示例项目/Go/cmd/demo/main_test.go", goTest().getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, root + "校验报告/生成信息.json", generationInfo(packageName, documents, scopeText));
            writeZipEntry(zip, root + "校验报告/测试结果.md", validationGuide().getBytes(StandardCharsets.UTF_8));
            for (ResourceDocument document : documents) {
                writeExamples(zip, root, document);
            }
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

    private String readme(String baseUrl, String packageName, int apiCount, String scopeText) {
        String resourceDescription = "通用目录".equals(packageName)
                ? "当前已启用、已发布接口" : "当前环境已授权接口";
        return """
                # %s

                本包包含 %d 个%s，只保留最新发布版本。

                - `文档/`：快速接入、Scope 说明、接口清单与完整字段示例。
                - `API导入文件/`：Apifox、ApiPost、Postman 可直接导入。
                - `示例项目/`：Java、Python、Node.js、Go、cURL 完整示例。
                - `校验报告/`：生成信息和发布前检查说明。

                填写各示例项目中的 Client ID、Client Secret 后运行。Scope 已按应用授权生成：

                ```text
                %s
                ```

                Token 地址：`%s/open/oauth2/token`。Secret 不得提交到 Git 或写入日志。
                """.formatted(packageName, apiCount, resourceDescription, scopeText, baseUrl);
    }

    private String quickStart(String baseUrl, String scopeText) {
        return """
                # 快速接入

                1. 在凭证管理中获取当前环境的 Client ID 和 Client Secret。
                2. 使用 `%s/open/oauth2/token` 获取 Access Token。
                3. Token 请求中的 Scope 可一次传多个，使用空格分隔。
                4. 调用接口时携带 `Authorization: Bearer ACCESS_TOKEN`。
                5. HTTP 2xx 后继续检查业务 `success` 和 `code`。

                当前包 Scope：

                ```text
                %s
                ```
                """.formatted(baseUrl, scopeText);
    }

    private String authGuide(String baseUrl, String scopeText) {
        return """
                # 鉴权与 Key/Secret 使用说明

                ## 凭证含义

                - Client ID：开放平台凭证页显示的 Key/客户端标识。
                - Client Secret：客户端密钥，只在生成或轮换时显示一次。
                - Access Token：使用 Client ID/Secret 临时换取，调用接口时放入 Authorization: Bearer TOKEN。
                - Scope：每个接口所需权限，详见完整接口参考或 openapi.json。

                沙箱和生产凭证不能混用。Secret 遗失后无法找回，只能在凭证管理中轮换。

                ## Scope 如何传

                - 一次 Token 请求可以传一个或多个 Scope；多个 Scope 使用空格分隔。
                - 同一个 Access Token 可以调用它所包含 Scope 对应的全部已授权接口，无需每个接口重新获取 Token。
                - 多个接口可能共用同一个 Scope，例如开始课堂、加入课堂和进入课程共用 classroom.live.control。
                - 请求的 Scope 不能超过应用在当前环境已授权的范围；建议按最小权限申请。
                - 省略 scope 时，当前平台会使用应用已配置的全部 Scope，但正式对接建议显式填写。

                ## 获取 Access Token

                    curl -X POST '%s/open/oauth2/token' __CONT__
                      -H 'Content-Type: application/x-www-form-urlencoded' __CONT__
                      --data-urlencode 'grant_type=client_credentials' __CONT__
                      --data-urlencode 'client_id=YOUR_CLIENT_ID' __CONT__
                      --data-urlencode 'client_secret=YOUR_CLIENT_SECRET' __CONT__
                      --data-urlencode 'scope=%s'

                成功响应中的 access_token 是临时令牌，expires_in 是有效秒数。

                当前对接包包含以下 Scope：

                    scope=%s

                ## 调用接口

                    curl '%s/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo?appId=com.example.video&versionCode=1' __CONT__
                      -H 'Authorization: Bearer YOUR_ACCESS_TOKEN'

                HTTP 2xx 不等于业务成功；兼容接口还必须检查 success=true 和 code=200。
                Secret 不得写入前端、Git、URL、日志或公开文档。
                """.formatted(baseUrl, scopeText, scopeText, baseUrl).replace("__CONT__", "\\");
    }

    private String apiReference(List<ResourceDocument> documents) {
        StringBuilder result = new StringBuilder("# 完整接口参考\n\n")
                .append("本文件列出当前已启用、已发布接口；完整字段约束以同包 openapi.json 为准。\n\n")
                .append("| 接口编码 | 名称 | 方法 | 路径 | Scope | 最新版本 |\n")
                .append("|---|---|---|---|---|---|\n");
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            result.append("| ").append(markdown(resource.getResourceCode())).append(" | ")
                    .append(markdown(resource.getResourceName())).append(" | ")
                    .append(markdown(resource.getHttpMethod())).append(" | ")
                    .append(markdown(resource.getPath())).append(" | ")
                    .append(markdown(resource.getScopeCode())).append(" | ")
                    .append(markdown(document.version().getVersion())).append(" |\n");
        }
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            result.append("\n## ").append(resource.getResourceName()).append("\n\n")
                    .append("- 编码：").append(resource.getResourceCode()).append("\n")
                    .append("- 请求：").append(resource.getHttpMethod()).append(' ').append(resource.getPath()).append("\n")
                    .append("- Scope：").append(resource.getScopeCode()).append("\n")
                    .append("- 最新版本：").append(defaultText(document.version().getVersion(), "-")).append("\n")
                    .append("- 说明：").append(defaultText(resource.getDescription(), "-")).append("\n\n")
                    .append("请求实例：\n\n").append(pretty(redactPayload(parse(document.version().getRequestExampleJson()))))
                    .append("\n\n成功响应实例：\n\n").append(pretty(redactPayload(parse(document.version().getResponseExamplesJson()))))
                    .append("\n\n错误响应实例：\n\n").append(pretty(redactPayload(parse(document.version().getErrorExamplesJson()))))
                    .append("\n");
        }
        return result.toString();
    }

    private String endpointCsv(List<ResourceDocument> documents) {
        StringBuilder result = new StringBuilder("\uFEFF接口编码,接口名称,分类,请求方式,路径,Scope,最新版本\r\n");
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            result.append(csv(resource.getResourceCode())).append(',')
                    .append(csv(resource.getResourceName())).append(',')
                    .append(csv(resource.getCategory())).append(',')
                    .append(csv(resource.getHttpMethod())).append(',')
                    .append(csv(resource.getPath())).append(',')
                    .append(csv(resource.getScopeCode())).append(',')
                    .append(csv(document.version().getVersion())).append("\r\n");
        }
        return result.toString();
    }

    private void writeExamples(ZipOutputStream zip, String root, ResourceDocument document) throws IOException {
        String name = safeFilename(document.resource().getResourceCode());
        writeZipEntry(zip, root + "文档/接口示例/" + name + ".请求.json", jsonExample(document.version().getRequestExampleJson()));
        writeZipEntry(zip, root + "文档/接口示例/" + name + ".成功响应.json", jsonExample(document.version().getResponseExamplesJson()));
        writeZipEntry(zip, root + "文档/接口示例/" + name + ".错误响应.json", jsonExample(document.version().getErrorExamplesJson()));
    }

    private byte[] jsonExample(String value) {
        JsonNode node = redactPayload(parse(value));
        return json(node == null ? objectMapper.createObjectNode() : node);
    }

    private String demoGuide(String scopeText) {
        return """
                # 多语言 Demo

                五个项目都先获取一次 Token，再复用 Token 调用每个已授权接口方法。

                - Client ID 和 Client Secret 默认留空，可填写代码常量或使用环境变量。
                - Scope 已固定为：`%s`。
                - 查询接口默认运行；控制类接口需显式设置 `OPEN_PLATFORM_RUN_WRITE_APIS=true`。
                - 可用 `OPEN_PLATFORM_ONLY_RESOURCE=接口编码` 只运行一个接口。
                """.formatted(scopeText);
    }

    private String demoEnvironment(String baseUrl, String scopeText) {
        return """
                OPEN_PLATFORM_BASE_URL=%s
                OPEN_PLATFORM_CLIENT_ID=
                OPEN_PLATFORM_CLIENT_SECRET=
                OPEN_PLATFORM_SCOPES=%s
                OPEN_PLATFORM_RUN_WRITE_APIS=false
                OPEN_PLATFORM_ONLY_RESOURCE=
                """.formatted(baseUrl, scopeText);
    }

    private String curlDemo(String baseUrl) {
        return """
                #!/usr/bin/env sh
                set -eu
                : "${OPEN_PLATFORM_CLIENT_ID:?Please set OPEN_PLATFORM_CLIENT_ID}"
                : "${OPEN_PLATFORM_CLIENT_SECRET:?Please set OPEN_PLATFORM_CLIENT_SECRET}"
                base_url="${OPEN_PLATFORM_BASE_URL:-@@BASE_URL@@}"
                method="${OPEN_PLATFORM_METHOD:-GET}"
                path="${OPEN_PLATFORM_PATH:-/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo}"
                scope="${OPEN_PLATFORM_SCOPE:-classroom.app.read}"
                query="${OPEN_PLATFORM_QUERY:-appId=com.example.video&versionCode=1}"
                body="${OPEN_PLATFORM_BODY:-}"
                token_json="$(curl -fsS -X POST "$base_url/open/oauth2/token" __CONT__
                  -H 'Content-Type: application/x-www-form-urlencoded' __CONT__
                  --data-urlencode 'grant_type=client_credentials' __CONT__
                  --data-urlencode "client_id=$OPEN_PLATFORM_CLIENT_ID" __CONT__
                  --data-urlencode "client_secret=$OPEN_PLATFORM_CLIENT_SECRET" __CONT__
                  --data-urlencode "scope=$scope")"
                token="$(printf '%s' "$token_json" | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\\([^"]*\\)".*/\\1/p')"
                test -n "$token"
                url="$base_url$path"
                test -z "$query" || url="$url?$query"
                if [ -n "$body" ]; then
                  curl -fsS -X "$method" "$url" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body"
                else
                  curl -fsS -X "$method" "$url" -H "Authorization: Bearer $token"
                fi
                """.replace("@@BASE_URL@@", baseUrl).replace("__CONT__", "\\");
    }

    private String pythonDemo(String baseUrl) {
        return """
                import json, os, urllib.parse, urllib.request

                base = os.getenv("OPEN_PLATFORM_BASE_URL", "@@BASE_URL@@").rstrip("/")
                token_data = urllib.parse.urlencode({
                    "grant_type": "client_credentials",
                    "client_id": os.environ["OPEN_PLATFORM_CLIENT_ID"],
                    "client_secret": os.environ["OPEN_PLATFORM_CLIENT_SECRET"],
                    "scope": os.getenv("OPEN_PLATFORM_SCOPE", "classroom.app.read")
                }).encode()
                request = urllib.request.Request(base + "/open/oauth2/token", token_data,
                    {"Content-Type": "application/x-www-form-urlencoded"}, method="POST")
                with urllib.request.urlopen(request) as response:
                    token = json.load(response)["access_token"]
                method = os.getenv("OPEN_PLATFORM_METHOD", "GET")
                path = os.getenv("OPEN_PLATFORM_PATH", "/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo")
                query = os.getenv("OPEN_PLATFORM_QUERY", "appId=com.example.video&versionCode=1")
                body = os.getenv("OPEN_PLATFORM_BODY", "")
                url = base + path + (("?" + query) if query else "")
                headers = {"Authorization": "Bearer " + token}
                if body: headers["Content-Type"] = "application/json"
                api_request = urllib.request.Request(url, body.encode() if body else None, headers, method=method)
                with urllib.request.urlopen(api_request) as response: payload = json.load(response)
                print(json.dumps(payload, ensure_ascii=False, indent=2))
                if "success" in payload and (payload["success"] is not True or str(payload.get("code")) != "200"):
                    raise SystemExit("business request failed")
                """.replace("@@BASE_URL@@", baseUrl);
    }

    private String nodeDemo(String baseUrl) {
        return """
                const env = process.env;
                const base = (env.OPEN_PLATFORM_BASE_URL || '@@BASE_URL@@').replace(/\\/$/, '');
                if (!env.OPEN_PLATFORM_CLIENT_ID || !env.OPEN_PLATFORM_CLIENT_SECRET) throw new Error('Missing Client ID/Secret');
                const tokenResponse = await fetch(base + '/open/oauth2/token', {
                  method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                  body: new URLSearchParams({grant_type: 'client_credentials', client_id: env.OPEN_PLATFORM_CLIENT_ID,
                    client_secret: env.OPEN_PLATFORM_CLIENT_SECRET, scope: env.OPEN_PLATFORM_SCOPE || 'classroom.app.read'})
                });
                if (!tokenResponse.ok) throw new Error('token HTTP ' + tokenResponse.status);
                const {access_token} = await tokenResponse.json();
                const query = env.OPEN_PLATFORM_QUERY ?? 'appId=com.example.video&versionCode=1';
                const body = env.OPEN_PLATFORM_BODY || '';
                const response = await fetch(base + (env.OPEN_PLATFORM_PATH ||
                  '/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo') + (query ? '?' + query : ''), {
                  method: env.OPEN_PLATFORM_METHOD || 'GET',
                  headers: {Authorization: 'Bearer ' + access_token, ...(body ? {'Content-Type': 'application/json'} : {})},
                  body: body || undefined
                });
                const payload = await response.json();
                console.log(JSON.stringify(payload, null, 2));
                if (!response.ok || (Object.hasOwn(payload, 'success') && (!payload.success || String(payload.code) !== '200'))) process.exit(1);
                """.replace("@@BASE_URL@@", baseUrl);
    }

    private String javaDemo(String baseUrl) {
        return """
                import java.net.URI;
                import java.net.URLEncoder;
                import java.net.http.*;
                import java.nio.charset.StandardCharsets;
                import java.util.regex.*;

                public class OpenPlatformDemo {
                  static String env(String name, String fallback) {
                    String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value;
                  }
                  static String form(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
                  public static void main(String[] args) throws Exception {
                    String base = env("OPEN_PLATFORM_BASE_URL", "@@BASE_URL@@").replaceAll("/$", "");
                    String clientId = env("OPEN_PLATFORM_CLIENT_ID", "");
                    String secret = env("OPEN_PLATFORM_CLIENT_SECRET", "");
                    if (clientId.isBlank() || secret.isBlank()) throw new IllegalArgumentException("Missing Client ID/Secret");
                    String tokenBody = "grant_type=client_credentials&client_id=" + form(clientId)
                        + "&client_secret=" + form(secret) + "&scope=" + form(env("OPEN_PLATFORM_SCOPE", "classroom.app.read"));
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(base + "/open/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(tokenBody)).build();
                    String tokenJson = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString()).body();
                    Matcher matcher = Pattern.compile("\\\"access_token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(tokenJson);
                    if (!matcher.find()) throw new IllegalStateException("No access_token returned");
                    String query = env("OPEN_PLATFORM_QUERY", "appId=com.example.video&versionCode=1");
                    String url = base + env("OPEN_PLATFORM_PATH", "/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo")
                        + (query.isBlank() ? "" : "?" + query);
                    String body = env("OPEN_PLATFORM_BODY", "");
                    HttpRequest.Builder api = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + matcher.group(1));
                    if (body.isBlank()) api.method(env("OPEN_PLATFORM_METHOD", "GET"), HttpRequest.BodyPublishers.noBody());
                    else api.header("Content-Type", "application/json").method(env("OPEN_PLATFORM_METHOD", "POST"), HttpRequest.BodyPublishers.ofString(body));
                    HttpResponse<String> response = client.send(api.build(), HttpResponse.BodyHandlers.ofString());
                    System.out.println(response.body());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) System.exit(1);
                  }
                }
                """.replace("@@BASE_URL@@", baseUrl);
    }

    private String goDemo(String baseUrl) {
        return """
                package main

                import (
                    "encoding/json"
                    "fmt"
                    "io"
                    "net/http"
                    "net/url"
                    "os"
                    "strings"
                )

                func env(name, fallback string) string {
                    if value := os.Getenv(name); value != "" {
                        return value
                    }
                    return fallback
                }

                func main() {
                    base := strings.TrimRight(env("OPEN_PLATFORM_BASE_URL", "@@BASE_URL@@"), "/")
                    clientID, secret := os.Getenv("OPEN_PLATFORM_CLIENT_ID"), os.Getenv("OPEN_PLATFORM_CLIENT_SECRET")
                    if clientID == "" || secret == "" {
                        panic("missing Client ID/Secret")
                    }
                    form := url.Values{
                        "grant_type": {"client_credentials"},
                        "client_id": {clientID},
                        "client_secret": {secret},
                        "scope": {env("OPEN_PLATFORM_SCOPE", "classroom.app.read")},
                    }
                    tokenResponse, err := http.PostForm(base+"/open/oauth2/token", form)
                    if err != nil {
                        panic(err)
                    }
                    defer tokenResponse.Body.Close()
                    var token map[string]interface{}
                    json.NewDecoder(tokenResponse.Body).Decode(&token)
                    accessToken, _ := token["access_token"].(string)
                    if accessToken == "" {
                        panic("no access_token returned")
                    }
                    query, body := env("OPEN_PLATFORM_QUERY", "appId=com.example.video&versionCode=1"), os.Getenv("OPEN_PLATFORM_BODY")
                    endpoint := base + env("OPEN_PLATFORM_PATH", "/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo")
                    if query != "" {
                        endpoint += "?" + query
                    }
                    request, _ := http.NewRequest(env("OPEN_PLATFORM_METHOD", "GET"), endpoint, strings.NewReader(body))
                    request.Header.Set("Authorization", "Bearer "+accessToken)
                    if body != "" {
                        request.Header.Set("Content-Type", "application/json")
                    }
                    response, err := http.DefaultClient.Do(request)
                    if err != nil {
                        panic(err)
                    }
                    defer response.Body.Close()
                    content, _ := io.ReadAll(response.Body)
                    fmt.Println(string(content))
                    if response.StatusCode < 200 || response.StatusCode >= 300 {
                        os.Exit(1)
                    }
                }
                """.replace("@@BASE_URL@@", baseUrl);
    }

    private String curlDemo(String baseUrl, String scopeText, List<ResourceDocument> documents) {
        StringBuilder script = new StringBuilder("""
                #!/usr/bin/env sh
                set -eu

                BASE_URL="${OPEN_PLATFORM_BASE_URL:-@@BASE_URL@@}"
                CONTENT_TYPE='application/x-www-form-urlencoded'
                GRANT_TYPE='client_credentials'
                CLIENT_ID="${OPEN_PLATFORM_CLIENT_ID:-}"
                CLIENT_SECRET="${OPEN_PLATFORM_CLIENT_SECRET:-}"
                SCOPES="${OPEN_PLATFORM_SCOPES:-@@SCOPES@@}"
                RUN_WRITE_APIS="${OPEN_PLATFORM_RUN_WRITE_APIS:-false}"
                ONLY_RESOURCE="${OPEN_PLATFORM_ONLY_RESOURCE:-}"

                get_access_token() {
                  test -n "$CLIENT_ID" && test -n "$CLIENT_SECRET" || { echo '请填写 Client ID 和 Client Secret' >&2; exit 1; }
                  token_json="$(curl -fsS -X POST "$BASE_URL/open/oauth2/token" __CONT__
                    -H "Content-Type: $CONTENT_TYPE" __CONT__
                    --data-urlencode "grant_type=$GRANT_TYPE" __CONT__
                    --data-urlencode "client_id=$CLIENT_ID" __CONT__
                    --data-urlencode "client_secret=$CLIENT_SECRET" __CONT__
                    --data-urlencode "scope=$SCOPES")"
                  printf '%s' "$token_json" | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\\([^"]*\\)".*/\\1/p'
                }

                call_api() {
                  method="$1"; path="$2"; query="$3"; body="$4"; url="$BASE_URL$path"
                  test -z "$query" || url="$url?$query"
                  if [ -n "$body" ]; then
                    response="$(curl -fsS -X "$method" "$url" -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' -d "$body")"
                  else
                    response="$(curl -fsS -X "$method" "$url" -H "Authorization: Bearer $ACCESS_TOKEN")"
                  fi
                  printf '%s\n' "$response"
                  if printf '%s' "$response" | grep -Eq '"success"[[:space:]]*:[[:space:]]*false'; then return 1; fi
                  business_code="$(printf '%s' "$response" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*"*\\([0-9][0-9]*\\)"*.*/\\1/p')"
                  test -z "$business_code" || [ "$business_code" = 200 ]
                }

                should_run() { [ -z "$ONLY_RESOURCE" ] || [ "$ONLY_RESOURCE" = "$1" ]; }

                """.replace("@@BASE_URL@@", baseUrl).replace("@@SCOPES@@", scopeText).replace("__CONT__", "\\"));
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            script.append(shellMethodName(resource.getResourceCode())).append("() {\n  call_api ")
                    .append(shellQuote(resource.getHttpMethod())).append(' ')
                    .append(shellQuote(resource.getPath())).append(' ')
                    .append(shellQuote(demoQuery(document))).append(' ')
                    .append(shellQuote(demoBody(document))).append("\n}\n\n");
        }
        script.append("ACCESS_TOKEN=\"$(get_access_token)\"\ntest -n \"$ACCESS_TOKEN\" || { echo 'Token 响应缺少 access_token' >&2; exit 1; }\n");
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            script.append("if should_run ").append(shellQuote(resource.getResourceCode()));
            if (isControl(resource)) script.append(" && [ \"$RUN_WRITE_APIS\" = true ]");
            script.append("; then echo ").append(shellQuote("调用：" + resource.getResourceName()))
                    .append("; ").append(shellMethodName(resource.getResourceCode())).append("; fi\n");
        }
        return script.toString();
    }

    private String pythonDemo(String baseUrl, String scopeText, List<ResourceDocument> documents) {
        StringBuilder source = new StringBuilder("""
                import json
                import os
                import urllib.parse
                import urllib.request

                BASE_URL = os.getenv("OPEN_PLATFORM_BASE_URL", @@BASE_URL@@).rstrip("/")
                CONTENT_TYPE = "application/x-www-form-urlencoded"
                GRANT_TYPE = "client_credentials"
                CLIENT_ID = ""
                CLIENT_SECRET = ""
                SCOPES = @@SCOPES@@
                RUN_WRITE_APIS = os.getenv("OPEN_PLATFORM_RUN_WRITE_APIS", "false").lower() == "true"
                ONLY_RESOURCE = os.getenv("OPEN_PLATFORM_ONLY_RESOURCE", "")

                def credential(name: str, configured: str) -> str:
                    return configured or os.getenv(name, "")

                def get_access_token() -> str:
                    client_id = credential("OPEN_PLATFORM_CLIENT_ID", CLIENT_ID)
                    secret = credential("OPEN_PLATFORM_CLIENT_SECRET", CLIENT_SECRET)
                    if not client_id or not secret:
                        raise ValueError("请填写 Client ID 和 Client Secret")
                    data = urllib.parse.urlencode({"grant_type": GRANT_TYPE, "client_id": client_id,
                        "client_secret": secret, "scope": os.getenv("OPEN_PLATFORM_SCOPES", SCOPES)}).encode()
                    request = urllib.request.Request(BASE_URL + "/open/oauth2/token", data,
                        {"Content-Type": CONTENT_TYPE}, method="POST")
                    with urllib.request.urlopen(request) as response:
                        return json.load(response)["access_token"]

                def call_api(token: str, method: str, path: str, query: str = "", body: str = ""):
                    url = BASE_URL + path + (("?" + query) if query else "")
                    headers = {"Authorization": "Bearer " + token}
                    if body:
                        headers["Content-Type"] = "application/json"
                    request = urllib.request.Request(url, body.encode() if body else None, headers, method=method)
                    with urllib.request.urlopen(request) as response:
                        payload = json.load(response)
                    print(json.dumps(payload, ensure_ascii=False, indent=2))
                    if "success" in payload and payload["success"] is not True:
                        raise RuntimeError("业务请求失败")
                    if "code" in payload and str(payload["code"]) != "200":
                        raise RuntimeError("业务请求失败: " + str(payload["code"]))
                    return payload

                """.replace("@@BASE_URL@@", literal(baseUrl)).replace("@@SCOPES@@", literal(scopeText)));
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("def ").append(snakeName(resource.getResourceCode())).append("(token: str):\n")
                    .append("    return call_api(token, ").append(literal(resource.getHttpMethod())).append(", ")
                    .append(literal(resource.getPath())).append(", ").append(literal(demoQuery(document))).append(", ")
                    .append(literal(demoBody(document))).append(")\n\n");
        }
        source.append("def main():\n    token = get_access_token()\n");
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("    if (not ONLY_RESOURCE or ONLY_RESOURCE == ").append(literal(resource.getResourceCode())).append(")");
            if (isControl(resource)) source.append(" and RUN_WRITE_APIS");
            source.append(":\n        ").append(snakeName(resource.getResourceCode())).append("(token)\n");
        }
        source.append("\nif __name__ == \"__main__\":\n    main()\n");
        return source.toString();
    }

    private String nodeDemo(String baseUrl, String scopeText, List<ResourceDocument> documents) {
        StringBuilder source = new StringBuilder("""
                import { pathToFileURL } from 'node:url';

                export const BASE_URL = (process.env.OPEN_PLATFORM_BASE_URL || @@BASE_URL@@).replace(/\\/$/, '');
                export const CONTENT_TYPE = 'application/x-www-form-urlencoded';
                export const GRANT_TYPE = 'client_credentials';
                export const CLIENT_ID = '';
                export const CLIENT_SECRET = '';
                export const SCOPES = @@SCOPES@@;
                const RUN_WRITE_APIS = (process.env.OPEN_PLATFORM_RUN_WRITE_APIS || 'false') === 'true';
                const ONLY_RESOURCE = process.env.OPEN_PLATFORM_ONLY_RESOURCE || '';
                const credential = (name, configured) => configured || process.env[name] || '';

                export async function getAccessToken() {
                  const clientId = credential('OPEN_PLATFORM_CLIENT_ID', CLIENT_ID);
                  const secret = credential('OPEN_PLATFORM_CLIENT_SECRET', CLIENT_SECRET);
                  if (!clientId || !secret) throw new Error('请填写 Client ID 和 Client Secret');
                  const response = await fetch(BASE_URL + '/open/oauth2/token', { method: 'POST',
                    headers: {'Content-Type': CONTENT_TYPE}, body: new URLSearchParams({ grant_type: GRANT_TYPE,
                      client_id: clientId, client_secret: secret, scope: process.env.OPEN_PLATFORM_SCOPES || SCOPES }) });
                  if (!response.ok) throw new Error('获取 Token 失败: HTTP ' + response.status);
                  const payload = await response.json();
                  if (!payload.access_token) throw new Error('Token 响应缺少 access_token');
                  return payload.access_token;
                }

                async function callApi(token, method, path, query = '', body = '') {
                  const response = await fetch(BASE_URL + path + (query ? '?' + query : ''), { method,
                    headers: {Authorization: 'Bearer ' + token, ...(body ? {'Content-Type': 'application/json'} : {})},
                    body: body || undefined });
                  const payload = await response.json();
                  console.log(JSON.stringify(payload, null, 2));
                  if (!response.ok || (Object.hasOwn(payload, 'success') && !payload.success)
                      || (Object.hasOwn(payload, 'code') && String(payload.code) !== '200')) throw new Error('业务请求失败');
                  return payload;
                }

                """.replace("@@BASE_URL@@", literal(baseUrl)).replace("@@SCOPES@@", literal(scopeText)));
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("export const ").append(camelName(resource.getResourceCode())).append(" = token => callApi(token, ")
                    .append(literal(resource.getHttpMethod())).append(", ").append(literal(resource.getPath())).append(", ")
                    .append(literal(demoQuery(document))).append(", ").append(literal(demoBody(document))).append(");\n");
        }
        source.append("\nexport async function main() {\n  const token = await getAccessToken();\n");
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("  if ((!ONLY_RESOURCE || ONLY_RESOURCE === ").append(literal(resource.getResourceCode())).append(")");
            if (isControl(resource)) source.append(" && RUN_WRITE_APIS");
            source.append(") await ").append(camelName(resource.getResourceCode())).append("(token);\n");
        }
        source.append("}\n\nif (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();\n");
        return source.toString();
    }

    private String javaDemo(String baseUrl, String scopeText, List<ResourceDocument> documents) {
        StringBuilder source = new StringBuilder("""
                package com.lubashu.openplatform.demo;

                import java.net.URI;
                import java.net.URLEncoder;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.nio.charset.StandardCharsets;
                import java.util.regex.Matcher;
                import java.util.regex.Pattern;

                public final class OpenPlatformDemo {
                    static final String BASE_URL = @@BASE_URL@@;
                    static final String TOKEN_PATH = "/open/oauth2/token";
                    static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
                    static final String GRANT_TYPE = "client_credentials";
                    static final String CLIENT_ID = "";
                    static final String CLIENT_SECRET = "";
                    static final String SCOPES = @@SCOPES@@;
                    static final boolean RUN_WRITE_APIS = Boolean.parseBoolean(env("OPEN_PLATFORM_RUN_WRITE_APIS", "false"));
                    static final String ONLY_RESOURCE = env("OPEN_PLATFORM_ONLY_RESOURCE", "");
                    static final HttpClient HTTP = HttpClient.newHttpClient();

                    private OpenPlatformDemo() {}

                    public static void main(String[] args) throws Exception {
                        String accessToken = getAccessToken();
                """.replace("@@BASE_URL@@", literal(baseUrl)).replace("@@SCOPES@@", literal(scopeText)));
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("        if (shouldRun(").append(literal(resource.getResourceCode())).append(")");
            if (isControl(resource)) source.append(" && RUN_WRITE_APIS");
            source.append(") ").append(camelName(resource.getResourceCode())).append("(accessToken);\n");
        }
        source.append("    }\n\n").append("""
                    static String getAccessToken() throws Exception {
                        String clientId = credential("OPEN_PLATFORM_CLIENT_ID", CLIENT_ID);
                        String secret = credential("OPEN_PLATFORM_CLIENT_SECRET", CLIENT_SECRET);
                        if (clientId.isBlank() || secret.isBlank()) throw new IllegalArgumentException("请填写 Client ID 和 Client Secret");
                        String tokenBody = "grant_type=" + form(GRANT_TYPE) + "&client_id=" + form(clientId)
                                + "&client_secret=" + form(secret) + "&scope=" + form(env("OPEN_PLATFORM_SCOPES", SCOPES));
                        String base = env("OPEN_PLATFORM_BASE_URL", BASE_URL).replaceAll("/$", "");
                        HttpRequest request = HttpRequest.newBuilder(URI.create(base + TOKEN_PATH))
                                .header("Content-Type", CONTENT_TYPE).POST(HttpRequest.BodyPublishers.ofString(tokenBody)).build();
                        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                        Matcher matcher = Pattern.compile("\\\"access_token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(response.body());
                        if (response.statusCode() < 200 || response.statusCode() >= 300 || !matcher.find())
                            throw new IllegalStateException("获取 Access Token 失败: HTTP " + response.statusCode());
                        return matcher.group(1);
                    }

                """);
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("    static String ").append(camelName(resource.getResourceCode())).append("(String token) throws Exception {\n")
                    .append("        return callApi(token, ").append(literal(resource.getHttpMethod())).append(", ")
                    .append(literal(resource.getPath())).append(", ").append(literal(demoQuery(document))).append(", ")
                    .append(literal(demoBody(document))).append(");\n    }\n\n");
        }
        source.append("""
                    static String callApi(String token, String method, String path, String query, String body) throws Exception {
                        String base = env("OPEN_PLATFORM_BASE_URL", BASE_URL).replaceAll("/$", "");
                        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path + (query.isBlank() ? "" : "?" + query)))
                                .header("Authorization", "Bearer " + token);
                        if (body.isBlank()) builder.method(method, HttpRequest.BodyPublishers.noBody());
                        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
                        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                        System.out.println(response.body());
                        Matcher code = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"?([0-9]+)\\\"?").matcher(response.body());
                        if (response.statusCode() < 200 || response.statusCode() >= 300
                                || response.body().matches("(?s).*\\\"success\\\"\\s*:\\s*false.*")
                                || (code.find() && !"200".equals(code.group(1))))
                            throw new IllegalStateException("接口调用失败: HTTP " + response.statusCode());
                        return response.body();
                    }

                    static boolean shouldRun(String resourceCode) { return ONLY_RESOURCE.isBlank() || ONLY_RESOURCE.equals(resourceCode); }
                    static String credential(String name, String configured) { return configured.isBlank() ? env(name, "") : configured; }
                    static String env(String name, String fallback) { String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value; }
                    static String form(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
                }
                """);
        return source.toString();
    }

    private String goDemo(String baseUrl, String scopeText, List<ResourceDocument> documents) {
        StringBuilder source = new StringBuilder("""
                package main

                import (
                \t"encoding/json"
                \t"fmt"
                \t"io"
                \t"net/http"
                \t"net/url"
                \t"os"
                \t"strings"
                )

                const baseURL = @@BASE_URL@@
                const contentType = "application/x-www-form-urlencoded"
                const grantType = "client_credentials"
                const clientID = ""
                const clientSecret = ""
                const scopes = @@SCOPES@@

                func env(name, fallback string) string {
                \tif value := os.Getenv(name); value != "" {
                \t\treturn value
                \t}
                \treturn fallback
                }

                func credential(name, configured string) string {
                \tif configured != "" {
                \t\treturn configured
                \t}
                \treturn os.Getenv(name)
                }

                func getAccessToken() string {
                \tid, secret := credential("OPEN_PLATFORM_CLIENT_ID", clientID), credential("OPEN_PLATFORM_CLIENT_SECRET", clientSecret)
                \tif id == "" || secret == "" {
                \t\tpanic("请填写 Client ID 和 Client Secret")
                \t}
                \tform := url.Values{"grant_type": {grantType}, "client_id": {id}, "client_secret": {secret},
                \t\t"scope": {env("OPEN_PLATFORM_SCOPES", scopes)}}
                \tresponse, err := http.PostForm(strings.TrimRight(env("OPEN_PLATFORM_BASE_URL", baseURL), "/")+"/open/oauth2/token", form)
                \tif err != nil {
                \t\tpanic(err)
                \t}
                \tdefer response.Body.Close()
                \tvar payload map[string]interface{}
                \tif err = json.NewDecoder(response.Body).Decode(&payload); err != nil {
                \t\tpanic(err)
                \t}
                \ttoken, _ := payload["access_token"].(string)
                \tif response.StatusCode < 200 || response.StatusCode >= 300 || token == "" {
                \t\tpanic("获取 Access Token 失败")
                \t}
                \treturn token
                }

                func callAPI(token, method, path, query, body string) string {
                \tendpoint := strings.TrimRight(env("OPEN_PLATFORM_BASE_URL", baseURL), "/") + path
                \tif query != "" {
                \t\tendpoint += "?" + query
                \t}
                \trequest, err := http.NewRequest(method, endpoint, strings.NewReader(body))
                \tif err != nil {
                \t\tpanic(err)
                \t}
                \trequest.Header.Set("Authorization", "Bearer "+token)
                \tif body != "" {
                \t\trequest.Header.Set("Content-Type", "application/json")
                \t}
                \tresponse, err := http.DefaultClient.Do(request)
                \tif err != nil {
                \t\tpanic(err)
                \t}
                \tdefer response.Body.Close()
                \tcontent, _ := io.ReadAll(response.Body)
                \tfmt.Println(string(content))
                \tif response.StatusCode < 200 || response.StatusCode >= 300 {
                \t\tpanic("接口调用失败")
                \t}
                \tvar payload map[string]interface{}
                \tif json.Unmarshal(content, &payload) == nil {
                \t\tif success, ok := payload["success"].(bool); ok && !success {
                \t\t\tpanic("业务请求失败")
                \t\t}
                \t\tif code, ok := payload["code"]; ok && fmt.Sprint(code) != "200" {
                \t\t\tpanic("业务请求失败")
                \t\t}
                \t}
                \treturn string(content)
                }

                """.replace("@@BASE_URL@@", literal(baseUrl)).replace("@@SCOPES@@", literal(scopeText)));
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("func ").append(pascalName(resource.getResourceCode())).append("(token string) string {\n\treturn callAPI(token, ")
                    .append(literal(resource.getHttpMethod())).append(", ").append(literal(resource.getPath())).append(", ")
                    .append(literal(demoQuery(document))).append(", ").append(literal(demoBody(document))).append(")\n}\n");
        }
        source.append("\nfunc main() {\n\ttoken := getAccessToken()\n\tonly := os.Getenv(\"OPEN_PLATFORM_ONLY_RESOURCE\")\n");
        if (documents.stream().map(ResourceDocument::resource).anyMatch(this::isControl)) {
            source.append("\trunWrite := os.Getenv(\"OPEN_PLATFORM_RUN_WRITE_APIS\") == \"true\"\n");
        }
        for (ResourceDocument document : documents) {
            OpenApiResourcePo resource = document.resource();
            source.append("\tif only == \"\" || only == ").append(literal(resource.getResourceCode()));
            if (isControl(resource)) source.append(" && runWrite");
            source.append(" {\n\t\t").append(pascalName(resource.getResourceCode())).append("(token)\n\t}\n");
        }
        source.append("}\n");
        return source.toString();
    }

    private String languageGuide(String language, String command) {
        return "# " + language + " 示例项目\n\n1. 填写 Client ID 和 Client Secret，或设置同名环境变量。\n"
                + "2. 执行：`" + command + "`。\n3. 控制类接口仅在 `OPEN_PLATFORM_RUN_WRITE_APIS=true` 时执行。\n";
    }

    private String pythonProject() {
        return "[project]\nname = \"lubashu-open-platform-demo\"\nversion = \"1.0.0\"\nrequires-python = \">=3.10\"\n";
    }

    private String pythonTest() {
        return """
                import pathlib, sys, unittest
                sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src"))
                import open_platform_demo as demo

                class DemoTest(unittest.TestCase):
                    def test_generated_configuration(self):
                        self.assertTrue(demo.SCOPES)
                        self.assertNotIn("接口要求的Scope", demo.SCOPES)

                if __name__ == "__main__": unittest.main()
                """;
    }

    private String nodeProject() {
        return """
                {"name":"lubashu-open-platform-demo","version":"1.0.0","type":"module","private":true,
                "scripts":{"start":"node src/open-platform-demo.mjs","test":"node --test --test-isolation=none"}}
                """;
    }

    private String nodeTest() {
        return """
                import test from 'node:test';
                import assert from 'node:assert/strict';
                import { SCOPES } from '../src/open-platform-demo.mjs';
                test('生成具体 Scope', () => { assert.ok(SCOPES); assert.ok(!SCOPES.includes('接口要求的Scope')); });
                """;
    }

    private String javaProject() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.lubashu</groupId><artifactId>open-platform-demo</artifactId><version>1.0.0</version>
                  <properties><maven.compiler.release>17</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
                  <dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.11.4</version><scope>test</scope></dependency></dependencies>
                  <build><plugins>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.2</version></plugin>
                    <plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId><version>3.5.0</version>
                      <configuration><mainClass>com.lubashu.openplatform.demo.OpenPlatformDemo</mainClass></configuration></plugin>
                  </plugins></build>
                </project>
                """;
    }

    private String javaTest() {
        return """
                package com.lubashu.openplatform.demo;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                class OpenPlatformDemoTest {
                    @Test void generatedConfigurationIsConcreteAndSecretFree() {
                        assertFalse(OpenPlatformDemo.SCOPES.isBlank());
                        assertFalse(OpenPlatformDemo.SCOPES.contains("接口要求的Scope"));
                        assertTrue(OpenPlatformDemo.CLIENT_ID.isBlank());
                        assertTrue(OpenPlatformDemo.CLIENT_SECRET.isBlank());
                    }
                }
                """;
    }

    private String goProject() { return "module lubashu-open-platform-demo\n\ngo 1.21\n"; }

    private String goTest() {
        return """
                package main

                import (
                \t"strings"
                \t"testing"
                )

                func TestGeneratedScopes(t *testing.T) {
                \tif scopes == "" || strings.Contains(scopes, "接口要求的Scope") {
                \t\tt.Fatal("Scope 未正确生成")
                \t}
                }
                """;
    }

    private byte[] generationInfo(String packageName, List<ResourceDocument> documents, String scopeText) {
        ObjectNode info = objectMapper.createObjectNode();
        info.put("对接包", packageName);
        info.put("生成时间", Instant.now().toString());
        info.put("接口数量", documents.size());
        info.put("Scope", scopeText);
        info.put("生成器版本", "2.0");
        return json(info);
    }

    private String validationGuide() {
        return """
                # 测试结果说明

                本包由同一份已授权接口模型生成 OpenAPI、Postman、文档和五语言项目。
                发布前门禁执行 OpenAPI 校验、敏感信息扫描，以及 Java、Python、Node.js、Go、cURL 的构建或语法检查。
                真实接口仍受凭证状态、环境、学校数据范围和业务参数约束；控制类接口默认不自动执行。
                """;
    }

    private String demoQuery(ResourceDocument document) {
        if (!usesQueryParameters(document.resource(), objectMapper.createObjectNode())) return "";
        JsonNode example = redactPayload(parse(document.version().getRequestExampleJson()));
        if (example == null || !example.isObject()) return "";
        List<String> values = new ArrayList<>();
        example.fields().forEachRemaining(entry -> values.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(text(entry.getValue()), StandardCharsets.UTF_8)));
        return String.join("&", values);
    }

    private String demoBody(ResourceDocument document) {
        if (usesQueryParameters(document.resource(), objectMapper.createObjectNode())) return "";
        JsonNode example = redactPayload(parse(document.version().getRequestExampleJson()));
        return example == null || example.isNull() ? "{}" : example.toString();
    }

    private boolean isControl(OpenApiResourcePo resource) {
        return "CONTROL".equalsIgnoreCase(resource.getSensitivity());
    }

    private String literal(String value) {
        try {
            return objectMapper.writeValueAsString(value == null ? "" : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Demo 字符串生成失败", ex);
        }
    }

    private String[] words(String value) {
        return defaultText(value, "api").split("[^A-Za-z0-9]+");
    }

    private String camelName(String value) {
        String[] parts = words(value);
        StringBuilder name = new StringBuilder(parts[0].isEmpty() ? "api" : parts[0].toLowerCase(Locale.ROOT));
        for (int index = 1; index < parts.length; index++) {
            if (!parts[index].isEmpty()) name.append(Character.toUpperCase(parts[index].charAt(0))).append(parts[index].substring(1));
        }
        if (Character.isDigit(name.charAt(0))) name.insert(0, "api");
        return name.toString();
    }

    private String pascalName(String value) {
        String name = camelName(value);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String snakeName(String value) {
        String name = defaultText(value, "api").replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT);
        return Character.isDigit(name.charAt(0)) ? "api_" + name : name;
    }

    private String shellMethodName(String value) { return snakeName(value); }

    private String shellQuote(String value) {
        return "'" + (value == null ? "" : value).replace("'", "'\"'\"'") + "'";
    }

    private String markdown(String value) {
        return defaultText(value, "-").replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private String csv(String value) {
        return "\"" + defaultText(value, "").replace("\"", "\"\"") + "\"";
    }

    private String safeFilename(String value) {
        return defaultText(value, "api").replaceAll("[^A-Za-z0-9._-]", "_");
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
