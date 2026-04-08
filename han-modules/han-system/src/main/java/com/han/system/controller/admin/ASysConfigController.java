package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.domain.po.SysConfigPo;
import com.han.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 参数配置 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysConfigController")
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class ASysConfigController {

    private final SysConfigMapper configMapper;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:config:list')")
    public R<PageResult<SysConfigPo>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "configName", required = false) String configName,
            @RequestParam(value = "configKey", required = false) String configKey,
            @RequestParam(value = "configType", required = false) String configType) {
        LambdaQueryWrapper<SysConfigPo> wrapper = new LambdaQueryWrapper<SysConfigPo>()
                .like(configName != null && !configName.isEmpty(), SysConfigPo::getConfigName, configName)
                .like(configKey != null && !configKey.isEmpty(), SysConfigPo::getConfigKey, configKey)
                .eq(configType != null && !configType.isEmpty(), SysConfigPo::getConfigType, configType)
                .orderByDesc(SysConfigPo::getCreateTime);
        Page<SysConfigPo> page = configMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/{configId}")
    @PreAuthorize("@ss.hasAuthority('system:config:query')")
    public R<SysConfigPo> getInfo(@PathVariable Long configId) {
        return R.ok(configMapper.selectById(configId));
    }

    @GetMapping("/key/{configKey}")
    public R<String> getByKey(@PathVariable String configKey) {
        LambdaQueryWrapper<SysConfigPo> wrapper = new LambdaQueryWrapper<SysConfigPo>()
                .eq(SysConfigPo::getConfigKey, configKey);
        SysConfigPo config = configMapper.selectOne(wrapper);
        return R.ok(config != null ? config.getConfigValue() : "");
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:config:add')")
    public R<Void> add(@RequestBody SysConfigPo config) {
        configMapper.insert(config);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:config:edit')")
    public R<Void> edit(@RequestBody SysConfigPo config) {
        configMapper.updateById(config);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove/{configId}")
    @PreAuthorize("@ss.hasAuthority('system:config:remove')")
    public R<Void> remove(@PathVariable Long configId) {
        configMapper.deleteById(configId);
        return R.ok();
    }
}
