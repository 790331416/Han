package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.domain.po.SysNoticePo;
import com.han.system.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@AdminAuth
@RestController("adminSysNoticeController")
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class ASysNoticeController {

    private final SysNoticeMapper noticeMapper;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:notice:list')")
    public R<PageResult<SysNoticePo>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "noticeTitle", required = false) String noticeTitle,
            @RequestParam(value = "noticeType", required = false) String noticeType) {
        LambdaQueryWrapper<SysNoticePo> wrapper = new LambdaQueryWrapper<SysNoticePo>()
                .like(noticeTitle != null && !noticeTitle.isEmpty(), SysNoticePo::getNoticeTitle, noticeTitle)
                .eq(noticeType != null && !noticeType.isEmpty(), SysNoticePo::getNoticeType, noticeType);
        Page<SysNoticePo> page = noticeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/{noticeId}")
    @PreAuthorize("@ss.hasAuthority('system:notice:query')")
    public R<SysNoticePo> getInfo(@PathVariable Long noticeId) {
        return R.ok(noticeMapper.selectById(noticeId));
    }

    @PostMapping("/add")
    @PreAuthorize("@ss.hasAuthority('system:notice:add')")
    public R<Void> add(@RequestBody SysNoticePo notice) {
        noticeMapper.insert(notice);
        return R.ok();
    }

    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:notice:edit')")
    public R<Void> edit(@RequestBody SysNoticePo notice) {
        noticeMapper.updateById(notice);
        return R.ok();
    }

    @PostMapping("/remove/{noticeId}")
    @PreAuthorize("@ss.hasAuthority('system:notice:remove')")
    public R<Void> remove(@PathVariable Long noticeId) {
        noticeMapper.deleteById(noticeId);
        return R.ok();
    }
}
