package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.domain.po.SysLoginLogPo;
import com.han.system.service.ISysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 登录日志管理 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysLoginLogController")
@RequestMapping("/system/loginlog")
@RequiredArgsConstructor
public class ASysLoginLogController {

    private final ISysLoginLogService loginLogService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('monitor:logininfor:list')")
    public R<PageResult<SysLoginLogPo>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(loginLogService.selectPage(pageNum, pageSize));
    }

    @PostMapping("/remove")
    @PreAuthorize("@ss.hasAuthority('monitor:logininfor:remove')")
    public R<Void> remove(@RequestBody List<Long> ids) {
        loginLogService.deleteByIds(ids);
        return R.ok();
    }

    @PostMapping("/clean")
    @PreAuthorize("@ss.hasAuthority('monitor:logininfor:remove')")
    public R<Void> clean() {
        loginLogService.cleanAll();
        return R.ok();
    }
}
