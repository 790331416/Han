package com.han.gen.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.gen.domain.DbTableInfo;
import com.han.gen.domain.GenTable;
import com.han.gen.service.GenTableService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 代码生成 - 管理端控制器。
 *
 * <p>权限按操作语义逐方法拆分：读列表、读详情、导入、改配置、删配置、出码各自独立，
 * 类级注解只作为漏标方法的兜底，避免「能看见菜单」等于「能改能删能导出全库表结构」。
 *
 * <p>路由 {@code /gen} 是网关（{@code Path=/gen/**}）与前端的调用契约，不随类名调整。
 */
@AdminAuth
@RestController("adminGenController")
@RequestMapping("/gen")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasAuthority('tool:gen:list')")
public class AGenController {

    private final GenTableService genTableService;

    /**
     * 查询已导入的表列表（分页）
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('tool:gen:list')")
    public R<PageResult<GenTable>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "tableName", required = false) String tableName) {
        return R.ok(genTableService.selectGenTablePage(pageNum, pageSize, tableName));
    }

    /**
     * 查询数据库中未导入的表列表。
     *
     * <p>返回的是整个 public schema 的表结构清单，只对具备导入权限的人开放。
     */
    @GetMapping("/db/list")
    @PreAuthorize("@ss.hasAuthority('tool:gen:import')")
    public R<List<DbTableInfo>> dbTableList(
            @RequestParam(value = "tableName", required = false) String tableName) {
        return R.ok(genTableService.selectDbTableList(tableName));
    }

    /**
     * 导入表
     */
    @RepeatSubmit
    @PostMapping("/importTable")
    @PreAuthorize("@ss.hasAuthority('tool:gen:import')")
    public R<Void> importTable(@RequestBody List<String> tableNames) {
        genTableService.importTable(tableNames);
        return R.ok();
    }

    /**
     * 查询表详情（含列信息）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAuthority('tool:gen:query')")
    public R<GenTable> getInfo(@PathVariable Long id) {
        return R.ok(genTableService.selectGenTableById(id));
    }

    /**
     * 修改表配置
     */
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('tool:gen:edit')")
    public R<Void> edit(@Valid @RequestBody GenTable table) {
        genTableService.updateGenTable(table);
        return R.ok();
    }

    /**
     * 删除表配置
     */
    @RepeatSubmit
    @PostMapping("/remove/{id}")
    @PreAuthorize("@ss.hasAuthority('tool:gen:remove')")
    public R<Void> remove(@PathVariable Long id) {
        genTableService.deleteGenTable(id);
        return R.ok();
    }

    /**
     * 预览代码
     */
    @GetMapping("/preview/{id}")
    @PreAuthorize("@ss.hasAuthority('tool:gen:code')")
    public R<Map<String, String>> preview(@PathVariable Long id) {
        return R.ok(genTableService.previewCode(id));
    }

    /**
     * 下载代码（ZIP）
     *
     * <p>字节先全部生成完再写响应头。生成失败时响应还没提交，此时显式落 500，
     * 让前端的 blob 下载走错误分支，而不是把错误 JSON 当成 zip 存盘。
     */
    @GetMapping("/download/{id}")
    @PreAuthorize("@ss.hasAuthority('tool:gen:code')")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        byte[] data;
        String fileName;
        try {
            data = genTableService.generateCode(id);
            fileName = genTableService.selectGenTableById(id).getTableName() + "-code.zip";
        } catch (RuntimeException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"msg\":\"" + jsonEscape(e.getMessage()) + "\"}");
            response.getWriter().flush();
            return;
        }
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setContentLength(data.length);
        response.setHeader("Content-Disposition",
                "attachment;filename=gen-code.zip;filename*=UTF-8''" + encoded);
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }

    private String jsonEscape(String raw) {
        if (raw == null) {
            return "代码生成失败";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }
}
