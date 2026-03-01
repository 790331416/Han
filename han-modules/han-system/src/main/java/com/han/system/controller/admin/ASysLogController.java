package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 日志管理（操作日志 + 登录日志）- A层
 */
@AdminAuth
@RestController("adminSysLogController")
@RequiredArgsConstructor
public class ASysLogController {

    // ==================== 操作日志 ====================

    @GetMapping("/system/operlog/list")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:list')")
    public R<PageResult<Object>> listOperLog(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(new PageResult<>(List.of(), 0L));
    }

    @GetMapping("/system/operlog/{id}")
    public R<Object> getOperLog(@PathVariable Long id) {
        return R.ok();
    }

    @PostMapping("/system/operlog/remove")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:remove')")
    public R<Void> removeOperLog(@RequestBody List<Long> ids) {
        return R.ok();
    }

    @PostMapping("/system/operlog/clean")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:remove')")
    public R<Void> cleanOperLog() {
        return R.ok();
    }

    // ==================== 登录日志 ====================

    @GetMapping("/system/loginlog/list")
    @PreAuthorize("@ss.hasAuthority('monitor:logininfor:list')")
    public R<PageResult<Object>> listLoginLog(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(new PageResult<>(List.of(), 0L));
    }

    @PostMapping("/system/loginlog/remove")
    @PreAuthorize("@ss.hasAuthority('monitor:logininfor:remove')")
    public R<Void> removeLoginLog(@RequestBody List<Long> ids) {
        return R.ok();
    }

    @PostMapping("/system/loginlog/clean")
    @PreAuthorize("@ss.hasAuthority('monitor:logininfor:remove')")
    public R<Void> cleanLoginLog() {
        return R.ok();
    }

    // ==================== 在线用户 ====================

    @GetMapping("/system/online/list")
    @PreAuthorize("@ss.hasAuthority('monitor:online:list')")
    public R<List<Object>> listOnline() {
        return R.ok(List.of());
    }

    @PostMapping("/system/online/forceLogout")
    @PreAuthorize("@ss.hasAuthority('monitor:online:forceLogout')")
    public R<Void> forceLogout(@RequestBody Map<String, String> body) {
        return R.ok();
    }
}
