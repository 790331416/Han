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

    /**
     * 删除与清空日志本身也必须留痕，否则审计记录可以被无痕抹除。
     * 切面在方法返回后才异步写日志，所以 clean 写下的这条记录不会被本次清空带走。
     */
    @PostMapping("/remove")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:remove')")
    @OperLog(module = "操作日志", type = OperLog.OperType.DELETE)
    public R<Void> remove(@RequestBody List<Long> ids) {
        operLogService.deleteByIds(ids);
        return R.ok();
    }

    @PostMapping("/clean")
    @PreAuthorize("@ss.hasAuthority('monitor:operlog:remove')")
    @OperLog(module = "操作日志", type = OperLog.OperType.CLEAN)
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

    /**
     * 落库的 oper_type 是 {@code OperLog.OperType.ordinal()}，此前这里另写了一张硬编码
     * 映射表且顺序与枚举不符，type &gt;= 4 的记录全部显示成错误的操作类型。
     *
     * <p>改为先按 ordinal 还原枚举常量再取文案：switch 对枚举是穷尽的，
     * 以后往枚举里加值会直接编译失败，不会再静默漂移。
     */
    private String formatOperType(Integer operType) {
        OperLog.OperType[] types = OperLog.OperType.values();
        if (operType == null || operType < 0 || operType >= types.length) {
            return "其他";
        }
        return switch (types[operType]) {
            case OTHER -> "其他";
            case INSERT -> "新增";
            case UPDATE -> "修改";
            case DELETE -> "删除";
            case SELECT -> "查询";
            case QUERY -> "列表查询";
            case EXPORT -> "导出";
            case IMPORT -> "导入";
            case GRANT -> "授权";
            case FORCE_LOGOUT -> "强退";
            case CLEAN -> "清空";
        };
    }
}
