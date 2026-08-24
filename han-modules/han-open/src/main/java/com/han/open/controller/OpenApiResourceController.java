package com.han.open.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanIdUtil;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.tenant.annotation.IgnoreTenant;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.vo.OpenApiResourceDetailVO;
import com.han.open.domain.vo.OpenApiResourceVersionVO;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.service.OpenApiResourceService;
import com.han.open.service.ResourcePathMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 开放接口目录，供应用授权页面选择；目录项不会因为被扫描到而自动授权。
 */
@AdminAuth
@RestController
@RequestMapping({"/open/app/api-resources", "/open/api-resource"})
@RequiredArgsConstructor
public class OpenApiResourceController {

    private final OpenApiResourceMapper resourceMapper;
    private final OpenApiResourceService resourceService;
    private final ResourcePathMappingService pathMappingService;

    @GetMapping({"", "/list"})
    @IgnoreTenant
    @PreAuthorize("@ss.hasAnyAuthority('open:app:query','open:api-resource:list','open:api-resource:query')")
    public R<List<OpenApiResourcePo>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        List<OpenApiResourcePo> resources = resourceMapper.selectList(
                new LambdaQueryWrapper<OpenApiResourcePo>()
                        .eq(!includeDisabled, OpenApiResourcePo::getStatus, 0)
                        .orderByAsc(OpenApiResourcePo::getSort)
                        .orderByAsc(OpenApiResourcePo::getId));
        return R.ok(resources);
    }

    @PostMapping
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:add')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录", type = OperLog.OperType.INSERT)
    public R<Void> add(@RequestBody OpenApiResourcePo resource) {
        validate(resource);
        resource.setId(HanIdUtil.snowflakeId());
        resource.setStatus(resource.getStatus() == null ? 0 : resource.getStatus());
        // 发布状态只能由版本发布流程推进，不能由资源编辑请求越权设置。
        resource.setPublishStatus(0);
        if (resourceMapper.insert(resource) > 0) {
            pathMappingService.refreshCache();
        }
        return R.ok();
    }

    @PostMapping("/edit")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:edit')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@RequestBody OpenApiResourcePo resource) {
        if (resource == null || resource.getId() == null) {
            throw new BusinessException("接口ID不能为空");
        }
        validate(resource);
        resource.setPublishStatus(null);
        if (resourceMapper.updateById(resource) > 0) {
            pathMappingService.refreshCache();
        }
        return R.ok();
    }

    @PostMapping("/remove/{id}")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:remove')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long id) {
        if (resourceMapper.deleteById(id) > 0) {
            pathMappingService.refreshCache();
        }
        return R.ok();
    }

    @PostMapping("/changeStatus")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:edit')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录", type = OperLog.OperType.UPDATE)
    public R<Void> changeStatus(@RequestBody OpenApiResourcePo resource) {
        if (resource == null || resource.getId() == null || resource.getStatus() == null) {
            throw new BusinessException("接口状态参数不能为空");
        }
        if (resource.getStatus() != 0 && resource.getStatus() != 1) {
            throw new BusinessException("接口状态仅支持上线或下线");
        }
        resourceService.setOnlineStatus(resource.getId(), resource.getStatus() == 0);
        pathMappingService.refreshCache();
        return R.ok();
    }

    /**
     * 下线资源但保留版本历史。
     */
    @PostMapping("/offline/{id}")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:edit')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录", type = OperLog.OperType.UPDATE)
    public R<Void> offline(@PathVariable Long id) {
        resourceService.setOnlineStatus(id, false);
        pathMappingService.refreshCache();
        return R.ok();
    }

    private void validate(OpenApiResourcePo resource) {
        if (resource == null || resource.getResourceName() == null || resource.getResourceName().isBlank()) {
            throw new BusinessException("接口名称不能为空");
        }
        if (resource.getResourceCode() == null || resource.getResourceCode().isBlank()) {
            throw new BusinessException("接口编码不能为空");
        }
        if (resource.getHttpMethod() == null || resource.getHttpMethod().isBlank()) {
            throw new BusinessException("请求方法不能为空");
        }
        if (resource.getPath() == null || !resource.getPath().startsWith("/open/api/")) {
            throw new BusinessException("开放路径必须以 /open/api/ 开头");
        }
        if (resource.getScopeCode() == null || resource.getScopeCode().isBlank()) {
            throw new BusinessException("Scope不能为空");
        }
        resource.setHttpMethod(resource.getHttpMethod().trim().toUpperCase(Locale.ROOT));
        resource.setSensitivity(resource.getSensitivity() == null || resource.getSensitivity().isBlank()
                ? "NORMAL" : resource.getSensitivity().trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 查询资源详情，包含版本信息
     */
    @GetMapping("/{id}")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:query')")
    public R<OpenApiResourceDetailVO> getDetail(@PathVariable Long id) {
        return R.ok(resourceService.getDetail(id));
    }

    /**
     * 新增草稿版本。
     */
    @PostMapping("/{resourceId}/versions")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:edit')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录版本", type = OperLog.OperType.INSERT)
    public R<OpenApiResourceVersionVO> createDraftVersion(@PathVariable Long resourceId,
                                                            @RequestBody OpenApiResourceVersionVO version) {
        return R.ok(resourceService.createDraftVersion(resourceId, version));
    }

    /**
     * 编辑草稿版本。
     */
    @PostMapping("/versions/edit")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:edit')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录版本", type = OperLog.OperType.UPDATE)
    public R<OpenApiResourceVersionVO> updateDraftVersion(@RequestBody OpenApiResourceVersionVO version) {
        if (version == null || version.getId() == null) {
            throw new BusinessException("版本ID不能为空");
        }
        return R.ok(resourceService.updateDraftVersion(version.getId(), version));
    }

    /**
     * 发布版本；服务层事务保证同一资源只有一个已发布版本。
     */
    @PostMapping("/versions/{versionId}/publish")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:edit')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录版本", type = OperLog.OperType.UPDATE)
    public R<OpenApiResourceVersionVO> publishVersion(@PathVariable Long versionId) {
        OpenApiResourceVersionVO result = resourceService.publishVersion(versionId);
        pathMappingService.refreshCache();
        return R.ok(result);
    }

    /**
     * 废弃版本，历史记录仍保留。
     */
    @PostMapping("/versions/{versionId}/deprecate")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:api-resource:edit')")
    @RepeatSubmit
    @OperLog(module = "开放接口目录版本", type = OperLog.OperType.UPDATE)
    public R<OpenApiResourceVersionVO> deprecateVersion(@PathVariable Long versionId) {
        OpenApiResourceVersionVO result = resourceService.deprecateVersion(versionId);
        pathMappingService.refreshCache();
        return R.ok(result);
    }
}
