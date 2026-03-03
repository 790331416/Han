package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.controller.base.BSysPostController;
import com.han.system.domain.dto.SysPostDto;
import com.han.system.domain.po.SysPostPo;
import com.han.system.domain.query.SysPostQuery;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:post:add')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.INSERT)
    public R<Void> add(@Valid @RequestBody SysPostDto dto) {
        return super.add(dto);
    }

    @Override
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:post:edit')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@Valid @RequestBody SysPostDto dto) {
        return super.edit(dto);
    }

    @Override
    @PostMapping("/remove/{postId}")
    @PreAuthorize("@ss.hasAuthority('system:post:remove')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long postId) {
        return super.remove(postId);
    }
}
