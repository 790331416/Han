package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.domain.po.SysOperLogPo;
import com.han.system.domain.query.SysOperLogQuery;
import com.han.system.service.ISysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志管理 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysOperLogController")
@RequestMapping("/system/operlog")
@RequiredArgsConstructor
public class ASysOperLogController {

    private final ISysOperLogService operLogService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:list')")
    public R<PageResult<SysOperLogPo>> list(SysOperLogQuery query) {
        return R.ok(operLogService.selectPage(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:list')")
    public R<SysOperLogPo> getInfo(@PathVariable Long id) {
        return R.ok(operLogService.selectById(id));
    }

    @PostMapping("/remove")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:remove')")
    public R<Void> remove(@RequestBody List<Long> ids) {
        operLogService.deleteByIds(ids);
        return R.ok();
    }

    @PostMapping("/clean")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:remove')")
    public R<Void> clean() {
        operLogService.cleanAll();
        return R.ok();
    }
}
