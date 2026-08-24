package com.han.open.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanIdUtil;
import com.han.open.converter.OpenApiResourceConverter;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiResourceVersionPo;
import com.han.open.domain.vo.OpenApiResourceDetailVO;
import com.han.open.domain.vo.OpenApiResourceVersionVO;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiResourceVersionMapper;
import com.han.open.service.OpenApiResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * 开放接口资源服务实现。
 */
@Service
@RequiredArgsConstructor
public class OpenApiResourceServiceImpl extends ServiceImpl<OpenApiResourceMapper, OpenApiResourcePo>
        implements OpenApiResourceService {

    private static final int VERSION_DRAFT = 0;
    private static final int VERSION_PUBLISHED = 1;
    private static final int VERSION_DEPRECATED = 2;
    private static final int RESOURCE_ENABLED = 0;
    private static final int RESOURCE_DISABLED = 1;
    private static final int RESOURCE_PUBLISHED = 2;
    private static final int RESOURCE_OFFLINE = 3;

    private final OpenApiResourceVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public OpenApiResourceDetailVO getDetail(Long id) {
        OpenApiResourcePo resource = requireResource(id);

        List<OpenApiResourceVersionPo> versionPos = versionMapper.selectList(
                new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                        .eq(OpenApiResourceVersionPo::getResourceId, id)
                        .eq(OpenApiResourceVersionPo::getDelFlag, 0)
                        .orderByDesc(OpenApiResourceVersionPo::getVersion));
        List<OpenApiResourceVersionVO> versions = (versionPos == null ? List.<OpenApiResourceVersionPo>of() : versionPos)
                .stream().map(this::convertToVersionVO).collect(Collectors.toList());
        OpenApiResourceDetailVO detail = OpenApiResourceConverter.toDetailVO(resource, versions);
        versions.stream()
                .filter(version -> Objects.equals(version.getStatus(), VERSION_PUBLISHED))
                .findFirst()
                .ifPresent(detail::setCurrentVersion);
        return detail;
    }

    @Override
    public boolean validateOpenApiSchema(String openapiJson) {
        try {
            JsonNode root = objectMapper.readTree(openapiJson);
            if (root == null || !root.isObject()
                    || !root.path("openapi").isTextual()
                    || !root.path("openapi").asText().startsWith("3.")) {
                return false;
            }
            JsonNode paths = root.get("paths");
            if (paths == null || !paths.isObject()) {
                return false;
            }
            Iterator<JsonNode> pathItems = paths.elements();
            while (pathItems.hasNext()) {
                JsonNode pathItem = pathItems.next();
                if (!pathItem.isObject()) {
                    continue;
                }
                Iterator<JsonNode> operations = pathItem.elements();
                while (operations.hasNext()) {
                    JsonNode operation = operations.next();
                    if (operation.isObject() && operation.get("responses") != null
                            && operation.get("responses").isObject()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setOnlineStatus(Long resourceId, boolean online) {
        OpenApiResourcePo resource = requireResource(resourceId);
        if (online && versionMapper.selectCount(new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                .eq(OpenApiResourceVersionPo::getResourceId, resource.getId())
                .eq(OpenApiResourceVersionPo::getStatus, VERSION_PUBLISHED)
                .eq(OpenApiResourceVersionPo::getDelFlag, 0)) == 0) {
            throw new BusinessException("请先发布接口版本再上线");
        }
        OpenApiResourcePo update = new OpenApiResourcePo();
        update.setId(resourceId);
        update.setStatus(online ? RESOURCE_ENABLED : RESOURCE_DISABLED);
        update.setPublishStatus(online ? RESOURCE_PUBLISHED : RESOURCE_OFFLINE);
        if (getBaseMapper().updateById(update) != 1) {
            throw new BusinessException("接口上下线状态更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiResourceVersionVO createDraftVersion(Long resourceId, OpenApiResourceVersionVO version) {
        OpenApiResourcePo resource = requireResource(resourceId);
        if (version == null) {
            throw new BusinessException("版本信息不能为空");
        }
        String versionNo = requireText(version.getVersion(), "版本号不能为空");
        ensureVersionUnique(resourceId, versionNo, null);

        OpenApiResourceVersionPo draft = new OpenApiResourceVersionPo();
        draft.setId(HanIdUtil.snowflakeId());
        draft.setResourceId(resourceId);
        draft.setVersion(versionNo);
        applyPayload(draft, version, true);
        draft.setStatus(VERSION_DRAFT);
        draft.setDelFlag(0);
        validateVersion(resource, draft);
        if (versionMapper.insert(draft) <= 0) {
            throw new BusinessException("版本草稿保存失败");
        }
        return convertToVersionVO(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiResourceVersionVO updateDraftVersion(Long versionId, OpenApiResourceVersionVO version) {
        OpenApiResourceVersionPo draft = requireVersion(versionId);
        if (!Objects.equals(draft.getStatus(), VERSION_DRAFT)) {
            throw new BusinessException("已发布或已废弃版本不可编辑");
        }
        if (version == null) {
            throw new BusinessException("版本信息不能为空");
        }
        OpenApiResourcePo resource = requireResource(draft.getResourceId());
        String versionNo = StringUtils.hasText(version.getVersion())
                ? version.getVersion().trim() : draft.getVersion();
        ensureVersionUnique(draft.getResourceId(), versionNo, versionId);
        draft.setVersion(versionNo);
        applyPayload(draft, version, false);
        validateVersion(resource, draft);
        if (versionMapper.updateById(draft) <= 0) {
            throw new BusinessException("版本草稿更新失败");
        }
        return convertToVersionVO(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiResourceVersionVO publishVersion(Long versionId) {
        OpenApiResourceVersionPo target = requireVersion(versionId);
        if (Objects.equals(target.getStatus(), VERSION_PUBLISHED)) {
            return convertToVersionVO(target);
        }
        if (!Objects.equals(target.getStatus(), VERSION_DRAFT)) {
            throw new BusinessException("仅草稿版本可以发布");
        }

        OpenApiResourcePo resource = requireResource(target.getResourceId());
        validateVersion(resource, target);

        // 事务内锁定当前发布记录，并用状态条件更新目标，避免同一资源出现两个 current 版本。
        List<OpenApiResourceVersionPo> currentVersions = versionMapper.selectList(
                new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                        .eq(OpenApiResourceVersionPo::getResourceId, target.getResourceId())
                        .eq(OpenApiResourceVersionPo::getStatus, VERSION_PUBLISHED)
                        .eq(OpenApiResourceVersionPo::getDelFlag, 0)
                        .last("FOR UPDATE"));
        if (currentVersions != null) {
            for (OpenApiResourceVersionPo current : currentVersions) {
                OpenApiResourceVersionPo deprecated = new OpenApiResourceVersionPo();
                deprecated.setStatus(VERSION_DEPRECATED);
                deprecated.setDeprecatedAt(LocalDateTime.now());
                int retired = versionMapper.update(deprecated, new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                        .eq(OpenApiResourceVersionPo::getId, current.getId())
                        .eq(OpenApiResourceVersionPo::getStatus, VERSION_PUBLISHED));
                if (retired != 1) {
                    throw new BusinessException("当前发布版本状态已变化，请重试");
                }
            }
        }

        LocalDateTime publishedAt = LocalDateTime.now();
        OpenApiResourceVersionPo published = new OpenApiResourceVersionPo();
        published.setStatus(VERSION_PUBLISHED);
        published.setPublishedAt(publishedAt);
        int updated = versionMapper.update(published, new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                .eq(OpenApiResourceVersionPo::getId, target.getId())
                .eq(OpenApiResourceVersionPo::getStatus, VERSION_DRAFT));
        if (updated != 1) {
            throw new BusinessException("版本状态已变化，请重试");
        }

        OpenApiResourcePo resourceUpdate = new OpenApiResourcePo();
        resourceUpdate.setId(resource.getId());
        resourceUpdate.setStatus(RESOURCE_ENABLED);
        resourceUpdate.setPublishStatus(RESOURCE_PUBLISHED);
        if (getBaseMapper().updateById(resourceUpdate) != 1) {
            throw new BusinessException("资源发布状态更新失败");
        }
        target.setStatus(VERSION_PUBLISHED);
        target.setPublishedAt(publishedAt);
        return convertToVersionVO(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiResourceVersionVO deprecateVersion(Long versionId) {
        OpenApiResourceVersionPo target = requireVersion(versionId);
        if (!Objects.equals(target.getStatus(), VERSION_PUBLISHED)) {
            throw new BusinessException("仅已发布版本可以废弃");
        }
        LocalDateTime deprecatedAt = LocalDateTime.now();
        OpenApiResourceVersionPo deprecated = new OpenApiResourceVersionPo();
        deprecated.setStatus(VERSION_DEPRECATED);
        deprecated.setDeprecatedAt(deprecatedAt);
        int updated = versionMapper.update(deprecated, new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                .eq(OpenApiResourceVersionPo::getId, target.getId())
                .eq(OpenApiResourceVersionPo::getStatus, VERSION_PUBLISHED));
        if (updated != 1) {
            throw new BusinessException("版本状态已变化，请重试");
        }
        target.setStatus(VERSION_DEPRECATED);
        target.setDeprecatedAt(deprecatedAt);
        if (versionMapper.selectCount(new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                .eq(OpenApiResourceVersionPo::getResourceId, target.getResourceId())
                .eq(OpenApiResourceVersionPo::getStatus, VERSION_PUBLISHED)
                .eq(OpenApiResourceVersionPo::getDelFlag, 0)) == 0) {
            OpenApiResourcePo resourceUpdate = new OpenApiResourcePo();
            resourceUpdate.setId(target.getResourceId());
            resourceUpdate.setStatus(RESOURCE_DISABLED);
            resourceUpdate.setPublishStatus(RESOURCE_OFFLINE);
            if (getBaseMapper().updateById(resourceUpdate) != 1) {
                throw new BusinessException("接口下线状态更新失败");
            }
        }
        return convertToVersionVO(target);
    }

    private OpenApiResourcePo requireResource(Long id) {
        if (id == null) {
            throw new BusinessException("资源ID不能为空");
        }
        OpenApiResourcePo resource = getBaseMapper().selectById(id);
        if (resource == null) {
            throw new BusinessException("资源不存在");
        }
        return resource;
    }

    private OpenApiResourceVersionPo requireVersion(Long id) {
        if (id == null) {
            throw new BusinessException("版本ID不能为空");
        }
        OpenApiResourceVersionPo version = versionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException("资源版本不存在");
        }
        return version;
    }

    private void ensureVersionUnique(Long resourceId, String version, Long currentId) {
        if (!StringUtils.hasText(version)) {
            throw new BusinessException("版本号不能为空");
        }
        LambdaQueryWrapper<OpenApiResourceVersionPo> wrapper = new LambdaQueryWrapper<OpenApiResourceVersionPo>()
                .eq(OpenApiResourceVersionPo::getResourceId, resourceId)
                .eq(OpenApiResourceVersionPo::getVersion, version)
                .eq(OpenApiResourceVersionPo::getDelFlag, 0);
        if (currentId != null) {
            wrapper.ne(OpenApiResourceVersionPo::getId, currentId);
        }
        if (versionMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("版本号已存在");
        }
    }

    private void applyPayload(OpenApiResourceVersionPo target, OpenApiResourceVersionVO source, boolean required) {
        if (source.getOpenapiSchema() != null) {
            target.setOpenapiJson(writeJson(source.getOpenapiSchema(), "OpenAPI Schema"));
        } else if (required) {
            target.setOpenapiJson(null);
        }
        if (source.getRequestExample() != null) {
            target.setRequestExampleJson(writeObjectJson(source.getRequestExample(), "请求示例"));
        }
        if (source.getResponseExamples() != null) {
            target.setResponseExamplesJson(writeObjectJson(source.getResponseExamples(), "响应示例"));
        }
        if (source.getErrorExamples() != null) {
            target.setErrorExamplesJson(writeObjectJson(source.getErrorExamples(), "错误示例"));
        }
        if (source.getAuthConfig() != null) {
            target.setAuthConfigJson(writeObjectJson(source.getAuthConfig(), "认证配置"));
        }
        if (source.getSandboxConfig() != null) {
            target.setSandboxConfigJson(writeObjectJson(source.getSandboxConfig(), "沙箱配置"));
        }
    }

    private void validateVersion(OpenApiResourcePo resource, OpenApiResourceVersionPo version) {
        requireValidSchema(version.getOpenapiJson(), resource.getPath(), resource.getHttpMethod());
        validateOptionalObject(version.getRequestExampleJson(), "请求示例");
        validateOptionalObject(version.getResponseExamplesJson(), "响应示例");
        validateOptionalObject(version.getErrorExamplesJson(), "错误示例");
    }

    private void requireValidSchema(String json, String resourcePath, String httpMethod) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException("OpenAPI Schema必须是合法JSON对象");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException("OpenAPI Schema必须是JSON对象");
        }
        String openapi = root.path("openapi").asText(null);
        if (!StringUtils.hasText(openapi) || !openapi.startsWith("3.")) {
            throw new BusinessException("OpenAPI版本必须是3.x");
        }
        JsonNode paths = root.get("paths");
        JsonNode pathItem = paths == null ? null : paths.get(resourcePath);
        String method = httpMethod == null ? "" : httpMethod.trim().toLowerCase();
        JsonNode operation = pathItem == null ? null : pathItem.get(method);
        if (pathItem == null || !pathItem.isObject() || operation == null || !operation.isObject()) {
            throw new BusinessException("OpenAPI Schema必须包含资源路径及对应HTTP方法");
        }
        if (!operation.has("responses") || !operation.get("responses").isObject()) {
            throw new BusinessException("OpenAPI Schema必须包含responses对象");
        }
    }

    private void validateOptionalObject(String json, String label) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new BusinessException(label + "必须是JSON对象");
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException(label + "必须是合法JSON对象");
        }
    }

    private String writeJson(Object value, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(label + "序列化失败");
        }
    }

    private String writeObjectJson(Map<String, Object> value, String label) {
        return writeJson(value, label);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private OpenApiResourceVersionVO convertToVersionVO(OpenApiResourceVersionPo po) {
        OpenApiResourceVersionVO vo = OpenApiResourceConverter.toVersionVO(po);
        vo.setOpenapiSchema(readMap(po.getOpenapiJson(), "OpenAPI Schema"));
        vo.setRequestExample(readMap(po.getRequestExampleJson(), "请求示例"));
        vo.setResponseExamples(readMap(po.getResponseExamplesJson(), "响应示例"));
        vo.setErrorExamples(readMap(po.getErrorExamplesJson(), "错误示例"));
        vo.setAuthConfig(readMap(po.getAuthConfigJson(), "认证配置"));
        vo.setSandboxConfig(readMap(po.getSandboxConfigJson(), "沙箱配置"));
        return vo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json, String label) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException(label + "解析失败");
        }
    }
}
