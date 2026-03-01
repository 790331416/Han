package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.domain.dto.SysDeptDto;
import com.han.system.domain.po.SysDeptPo;
import com.han.system.domain.query.SysDeptQuery;
import com.han.system.service.ISysDeptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysDeptController")
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class ASysDeptController {

    private final ISysDeptService deptService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:dept:list')")
    public R<List<SysDeptPo>> list(SysDeptQuery query) {
        return R.ok(deptService.selectDeptList(query));
    }

    @GetMapping("/tree")
    @PreAuthorize("@ss.hasAuthority('system:dept:list')")
    public R<List<SysDeptPo>> tree(SysDeptQuery query) {
        return R.ok(deptService.selectDeptTree(query));
    }

    @GetMapping("/info/{deptId}")
    @PreAuthorize("@ss.hasAuthority('system:dept:query')")
    public R<SysDeptPo> getInfo(@PathVariable Long deptId) {
        return R.ok(deptService.selectDeptById(deptId));
    }

    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:dept:add')")
    public R<Void> add(@Valid @RequestBody SysDeptDto dto) {
        deptService.insertDept(dto);
        return R.ok();
    }

    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:dept:edit')")
    public R<Void> edit(@Valid @RequestBody SysDeptDto dto) {
        deptService.updateDept(dto);
        return R.ok();
    }

    @PostMapping("/remove/{deptId}")
    @PreAuthorize("@ss.hasAuthority('system:dept:remove')")
    public R<Void> remove(@PathVariable Long deptId) {
        deptService.deleteDeptById(deptId);
        return R.ok();
    }
}
