package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.tenant.annotation.IgnoreTenant;
import com.han.open.service.ResourcePathMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放平台管理端控制器
 */
@Tag(name = "开放平台管理", description = "开放平台管理接口")
@RestController
@RequestMapping("/open/admin")
@RequiredArgsConstructor
public class OpenApiAdminController {

    private final ResourcePathMappingService pathMappingService;

    @Operation(summary = "刷新API资源映射缓存")
    @PostMapping("/resource/cache/refresh")
    @IgnoreTenant
    @PreAuthorize("@ss.hasAuthority('open:admin:config')")
    public R<Void> refreshResourceCache() {
        pathMappingService.refreshCache();
        return R.ok();
    }
}
