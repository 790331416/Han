package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.domain.po.SysPostPo;
import com.han.system.mapper.SysPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AdminAuth
@RestController("adminSysPostController")
@RequestMapping("/system/post")
@RequiredArgsConstructor
public class ASysPostController {

    private final SysPostMapper postMapper;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:post:list')")
    public R<PageResult<SysPostPo>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "postName", required = false) String postName) {
        LambdaQueryWrapper<SysPostPo> wrapper = new LambdaQueryWrapper<SysPostPo>()
                .like(postName != null && !postName.isEmpty(), SysPostPo::getPostName, postName)
                .orderByAsc(SysPostPo::getPostSort);
        Page<SysPostPo> page = postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/all")
    public R<List<SysPostPo>> listAll() {
        return R.ok(postMapper.selectList(new LambdaQueryWrapper<SysPostPo>().eq(SysPostPo::getStatus, 0).orderByAsc(SysPostPo::getPostSort)));
    }

    @GetMapping("/{postId}")
    @PreAuthorize("@ss.hasAuthority('system:post:query')")
    public R<SysPostPo> getInfo(@PathVariable Long postId) {
        return R.ok(postMapper.selectById(postId));
    }

    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:post:add')")
    public R<Void> add(@RequestBody SysPostPo post) {
        postMapper.insert(post);
        return R.ok();
    }

    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:post:edit')")
    public R<Void> edit(@RequestBody SysPostPo post) {
        postMapper.updateById(post);
        return R.ok();
    }

    @PostMapping("/remove/{postId}")
    @PreAuthorize("@ss.hasAuthority('system:post:remove')")
    public R<Void> remove(@PathVariable Long postId) {
        postMapper.deleteById(postId);
        return R.ok();
    }
}
