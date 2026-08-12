package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.web.excel.ExcelUtil;
import com.han.system.controller.base.BSysPostController;
import com.han.system.domain.dto.SysPostDto;
import com.han.system.domain.po.SysPostPo;
import com.han.system.domain.query.SysPostQuery;
import com.han.system.domain.vo.PostExportVo;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 岗位管理 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysPostController")
@RequestMapping("/system/post")
public class ASysPostController extends BSysPostController {

    @Override
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:post:list')")
    public R<PageResult<SysPostPo>> list(SysPostQuery query) {
        return super.list(query);
    }

    @Override
    @GetMapping("/all")
    @PreAuthorize("@ss.hasAuthority('system:post:list')")
    public R<List<SysPostPo>> listAll() {
        return super.listAll();
    }

    @Override
    @GetMapping("/{postId}")
    @PreAuthorize("@ss.hasAuthority('system:post:query')")
    public R<SysPostPo> getInfo(@PathVariable Long postId) {
        return super.getInfo(postId);
    }

    @Override
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:post:add')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.INSERT)
    public R<Void> add(@Valid @RequestBody SysPostDto dto) {
        return super.add(dto);
    }

    @Override
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:post:edit')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@Valid @RequestBody SysPostDto dto) {
        return super.edit(dto);
    }

    @Override
    @RepeatSubmit
    @PostMapping("/remove/{postId}")
    @PreAuthorize("@ss.hasAuthority('system:post:remove')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long postId) {
        return super.remove(postId);
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.hasAuthority('system:post:export')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.EXPORT)
    public void export(SysPostQuery query, HttpServletResponse response) throws IOException {
        // 导出走不分页查询：复用 list 会带上前端传来的 pageNum/pageSize，只导出当前页
        List<PostExportVo> list = postService.selectPostList(query).stream()
                .map(p -> PostExportVo.builder()
                        .postId(String.valueOf(p.getId()))
                        .postCode(p.getPostCode())
                        .postName(p.getPostName())
                        .postSort(String.valueOf(p.getPostSort()))
                        .statusText(p.getStatus() != null && p.getStatus() == 0 ? "正常" : "停用")
                        .createTime(p.getCreateTime() != null ? p.getCreateTime().toString() : "")
                        .build())
                .toList();
        ExcelUtil.exportExcel(response, "岗位数据", PostExportVo.class, list);
    }
}
