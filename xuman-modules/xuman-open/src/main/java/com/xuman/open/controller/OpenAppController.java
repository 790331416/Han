package com.xuman.open.controller;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.core.domain.R;
import com.xuman.common.security.annotation.RequiresPermission;
import com.xuman.open.domain.dto.OpenAppDTO;
import com.xuman.open.domain.vo.OpenAppVO;
import com.xuman.open.service.OpenAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 开放平台应用管理控制器
 */
@RestController
@RequestMapping("/open/app")
@RequiredArgsConstructor
public class OpenAppController {

    private final OpenAppService openAppService;

    /**
     * 查询应用列表
     */
    @RequiresPermission("open:app:list")
    @GetMapping("/list")
    public R<PageResult<OpenAppVO>> list(
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(openAppService.listApps(appName, status, pageNum, pageSize));
    }

    /**
     * 获取应用详情
     */
    @RequiresPermission("open:app:query")
    @GetMapping("/{appId}")
    public R<OpenAppVO> getInfo(@PathVariable Long appId) {
        return R.ok(openAppService.getAppById(appId));
    }

    /**
     * 创建应用
     */
    @RequiresPermission("open:app:add")
    @PostMapping
    public R<OpenAppVO> add(@RequestBody @Validated OpenAppDTO dto) {
        return R.ok(openAppService.createApp(dto));
    }

    /**
     * 修改应用
     */
    @RequiresPermission("open:app:edit")
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody @Validated OpenAppDTO dto) {
        openAppService.updateApp(dto);
        return R.ok();
    }

    /**
     * 删除应用
     */
    @RequiresPermission("open:app:remove")
    @PostMapping("/remove/{appId}")
    public R<Void> remove(@PathVariable Long appId) {
        openAppService.deleteApp(appId);
        return R.ok();
    }

    /**
     * 重置应用密钥
     */
    @RequiresPermission("open:app:edit")
    @PostMapping("/resetSecret/{appId}")
    public R<String> resetSecret(@PathVariable Long appId) {
        return R.ok(openAppService.resetAppSecret(appId));
    }

    /**
     * 修改应用状态
     */
    @RequiresPermission("open:app:edit")
    @PostMapping("/changeStatus")
    public R<Void> changeStatus(@RequestParam Long appId, @RequestParam Integer status) {
        openAppService.updateStatus(appId, status);
        return R.ok();
    }
}
