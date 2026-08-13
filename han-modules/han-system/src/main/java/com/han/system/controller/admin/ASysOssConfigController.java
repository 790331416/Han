package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.domain.po.SysOssConfigPo;
import com.han.system.service.ISysOssConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对象存储配置控制器。
 */
@AdminAuth
@RestController("adminSysOssConfigController")
@RequestMapping("/system/oss/config")
@RequiredArgsConstructor
public class ASysOssConfigController {

    private final ISysOssConfigService ossConfigService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:oss:list')")
    public R<PageResult<SysOssConfigPo>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "configKey", required = false) String configKey,
            @RequestParam(value = "status", required = false) String status) {
        return R.ok(ossConfigService.selectPage(pageNum, pageSize, configKey, status));
    }

    @GetMapping("/{ossConfigId}")
    @PreAuthorize("@ss.hasAuthority('system:oss:query')")
    public R<SysOssConfigPo> getInfo(@PathVariable Long ossConfigId) {
        return R.ok(ossConfigService.selectById(ossConfigId));
    }

    @GetMapping("/active")
    @PreAuthorize("@ss.hasAuthority('system:oss:query')")
    public R<SysOssConfigPo> active() {
        return R.ok(ossConfigService.selectActiveConfig());
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:oss:add')")
    public R<Void> add(@RequestBody SysOssConfigPo config) {
        ossConfigService.insert(config);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:oss:edit')")
    public R<Void> edit(@RequestBody SysOssConfigPo config) {
        ossConfigService.update(config);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove/{ossConfigId}")
    @PreAuthorize("@ss.hasAuthority('system:oss:remove')")
    public R<Void> remove(@PathVariable Long ossConfigId) {
        ossConfigService.deleteById(ossConfigId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/changeStatus/{ossConfigId}")
    @PreAuthorize("@ss.hasAuthority('system:oss:edit')")
    public R<Void> changeStatus(@PathVariable Long ossConfigId) {
        ossConfigService.changeStatus(ossConfigId);
        return R.ok();
    }
}
