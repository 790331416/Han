package com.han.gen.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.gen.domain.DbTableInfo;
import com.han.gen.domain.GenTable;
import com.han.gen.service.GenTableService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 代码生成控制器
 */
@RestController
@RequestMapping("/gen")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasAuthority('tool:gen:list')")
public class GenController {

    private final GenTableService genTableService;

    /**
     * 查询已导入的表列表（分页）
     */
    @GetMapping("/list")
    public R<PageResult<GenTable>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "tableName", required = false) String tableName) {
        return R.ok(genTableService.selectGenTablePage(pageNum, pageSize, tableName));
    }

    /**
     * 查询数据库中未导入的表列表
     */
    @GetMapping("/db/list")
    public R<List<DbTableInfo>> dbTableList(
            @RequestParam(value = "tableName", required = false) String tableName) {
        return R.ok(genTableService.selectDbTableList(tableName));
    }

    /**
     * 导入表
     */
    @PostMapping("/importTable")
    public R<Void> importTable(@RequestBody List<String> tableNames) {
        genTableService.importTable(tableNames);
        return R.ok();
    }

    /**
     * 查询表详情（含列信息）
     */
    @GetMapping("/{id}")
    public R<GenTable> getInfo(@PathVariable Long id) {
        return R.ok(genTableService.selectGenTableById(id));
    }

    /**
     * 修改表配置
     */
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody GenTable table) {
        genTableService.updateGenTable(table);
        return R.ok();
    }

    /**
     * 删除表配置
     */
    @PostMapping("/remove/{id}")
    public R<Void> remove(@PathVariable Long id) {
        genTableService.deleteGenTable(id);
        return R.ok();
    }

    /**
     * 预览代码
     */
    @GetMapping("/preview/{id}")
    public R<Map<String, String>> preview(@PathVariable Long id) {
        return R.ok(genTableService.previewCode(id));
    }

    /**
     * 下载代码（ZIP）
     */
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        byte[] data = genTableService.generateCode(id);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment;filename=gen-code.zip");
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }
}
