package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.web.excel.ExcelUtil;
import com.han.system.domain.po.SysOperLogPo;
import com.han.system.domain.query.SysOperLogQuery;
import com.han.system.domain.vo.OperLogExportVo;
import com.han.system.service.ISysOperLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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

    @GetMapping("/export")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:export')")
    @OperLog(module = "操作日志", type = OperLog.OperType.EXPORT)
    public void export(SysOperLogQuery query, HttpServletResponse response) throws IOException {
        query.setPageSize(10000);
        List<OperLogExportVo> list = operLogService.selectPage(query).getRows().stream()
                .map(o -> OperLogExportVo.builder()
                        .id(String.valueOf(o.getId()))
                        .module(o.getModule())
                        .operTypeText(formatOperType(o.getOperType()))
                        .operName(o.getOperName())
                        .operIp(o.getOperIp())
                        .operLocation(o.getOperLocation())
                        .requestMethod(o.getRequestMethod())
                        .statusText(o.getStatus() != null && o.getStatus() == 0 ? "成功" : "失败")
                        .costTime(String.valueOf(o.getCostTime()))
                        .operTime(o.getOperTime() != null ? o.getOperTime().toString() : "")
                        .build())
                .toList();
        ExcelUtil.exportExcel(response, "操作日志", OperLogExportVo.class, list);
    }

    private String formatOperType(Integer operType) {
        if (operType == null) return "其他";
        return switch (operType) {
            case 1 -> "新增";
            case 2 -> "修改";
            case 3 -> "删除";
            case 4 -> "授权";
            case 5 -> "导出";
            case 6 -> "导入";
            case 7 -> "强退";
            case 8 -> "清空";
            case 9 -> "查询";
            default -> "其他";
        };
    }
}
