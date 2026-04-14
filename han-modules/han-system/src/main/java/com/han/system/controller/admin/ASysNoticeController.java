package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.domain.po.SysNoticePo;
import com.han.system.domain.po.SysNoticeReadPo;
import com.han.system.domain.vo.NoticeLatestVo;
import com.han.system.mapper.SysNoticeMapper;
import com.han.system.mapper.SysNoticeReadMapper;
import com.han.system.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通知公告管理控制器。
 */
@AdminAuth
@RestController("adminSysNoticeController")
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class ASysNoticeController {

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeReadMapper noticeReadMapper;
    private final SseEmitterService sseEmitterService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:notice:list')")
    public R<PageResult<SysNoticePo>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "noticeTitle", required = false) String noticeTitle,
            @RequestParam(value = "noticeType", required = false) String noticeType,
            @RequestParam(value = "status", required = false) Integer status) {
        LambdaQueryWrapper<SysNoticePo> wrapper = new LambdaQueryWrapper<SysNoticePo>()
                .like(noticeTitle != null && !noticeTitle.isEmpty(), SysNoticePo::getNoticeTitle, noticeTitle)
                .eq(noticeType != null && !noticeType.isEmpty(), SysNoticePo::getNoticeType, noticeType)
                .eq(status != null, SysNoticePo::getStatus, status)
                .orderByDesc(SysNoticePo::getCreateTime);
        Page<SysNoticePo> page = noticeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/{noticeId}")
    @PreAuthorize("@ss.hasAuthority('system:notice:query')")
    public R<SysNoticePo> getInfo(@PathVariable Long noticeId) {
        return R.ok(noticeMapper.selectById(noticeId));
    }

    @RepeatSubmit
    @PostMapping("/add")
    @PreAuthorize("@ss.hasAuthority('system:notice:add')")
    public R<Void> add(@RequestBody SysNoticePo notice) {
        noticeMapper.insert(notice);
        broadcastNoticeRefresh();
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:notice:edit')")
    public R<Void> edit(@RequestBody SysNoticePo notice) {
        noticeMapper.updateById(notice);
        broadcastNoticeRefresh();
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove/{noticeId}")
    @PreAuthorize("@ss.hasAuthority('system:notice:remove')")
    public R<Void> remove(@PathVariable Long noticeId) {
        noticeMapper.deleteById(noticeId);
        noticeReadMapper.delete(new LambdaQueryWrapper<SysNoticeReadPo>()
                .eq(SysNoticeReadPo::getNoticeId, noticeId));
        broadcastNoticeRefresh();
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove")
    @PreAuthorize("@ss.hasAuthority('system:notice:remove')")
    public R<Void> remove(@RequestBody List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return R.ok();
        }
        noticeMapper.deleteByIds(noticeIds);
        noticeReadMapper.delete(new LambdaQueryWrapper<SysNoticeReadPo>()
                .in(SysNoticeReadPo::getNoticeId, noticeIds));
        broadcastNoticeRefresh();
        return R.ok();
    }

    // ==================== 通知铃铛 ====================

    @GetMapping("/latest")
    @PermissionExempt("已登录用户获取最新通知，无需特定权限")
    public R<List<NoticeLatestVo>> latest(@RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        Long userId = requireLoginUserId();
        int safeLimit = normalizeLimit(limit);
        List<NoticeLatestVo> list = noticeMapper.selectLatestForUser(userId, safeLimit);
        return R.ok(list);
    }

    @GetMapping("/unreadCount")
    @PermissionExempt("已登录用户获取未读通知数，无需特定权限")
    public R<Long> unreadCount() {
        Long count = noticeMapper.countUnreadForUser(requireLoginUserId());
        return R.ok(count == null ? 0L : count);
    }

    @RepeatSubmit
    @PostMapping("/markRead/{noticeId}")
    @PermissionExempt("已登录用户标记通知已读，无需特定权限")
    public R<Void> markRead(@PathVariable Long noticeId) {
        Long userId = requireLoginUserId();
        SysNoticePo notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BusinessException("通知不存在或已删除");
        }
        upsertReadState(userId, noticeId);
        pushNoticeRefreshToUser(userId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/markAllRead")
    @PermissionExempt("已登录用户全部标记已读，无需特定权限")
    public R<Void> markAllRead() {
        Long userId = requireLoginUserId();
        List<SysNoticePo> activeNotices = noticeMapper.selectList(new LambdaQueryWrapper<SysNoticePo>()
                .select(SysNoticePo::getId)
                .eq(SysNoticePo::getStatus, 0)
                .orderByDesc(SysNoticePo::getCreateTime));
        if (activeNotices.isEmpty()) {
            return R.ok();
        }

        List<Long> noticeIds = activeNotices.stream().map(SysNoticePo::getId).toList();
        List<SysNoticeReadPo> existingReads = noticeReadMapper.selectList(new LambdaQueryWrapper<SysNoticeReadPo>()
                .select(SysNoticeReadPo::getNoticeId)
                .eq(SysNoticeReadPo::getUserId, userId)
                .in(SysNoticeReadPo::getNoticeId, noticeIds));
        Set<Long> existingNoticeIds = new HashSet<>(existingReads.stream().map(SysNoticeReadPo::getNoticeId).toList());

        List<SysNoticeReadPo> toInsert = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long noticeId : noticeIds) {
            if (existingNoticeIds.contains(noticeId)) {
                continue;
            }
            SysNoticeReadPo readPo = new SysNoticeReadPo();
            readPo.setNoticeId(noticeId);
            readPo.setUserId(userId);
            readPo.setReadTime(now);
            toInsert.add(readPo);
        }
        toInsert.forEach(noticeReadMapper::insert);
        pushNoticeRefreshToUser(userId);
        return R.ok();
    }

    @GetMapping("/sse")
    @PermissionExempt("SSE 长连接，已登录即可")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter sse(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            Long uid = SecurityContextHolder.getUserId();
            if (uid == null) {
                throw new UnauthorizedException("未登录或登录已过期");
            }
            userId = String.valueOf(uid);
        }
        return sseEmitterService.connect(userId);
    }

    private void upsertReadState(Long userId, Long noticeId) {
        SysNoticeReadPo existed = noticeReadMapper.selectOne(new LambdaQueryWrapper<SysNoticeReadPo>()
                .eq(SysNoticeReadPo::getUserId, userId)
                .eq(SysNoticeReadPo::getNoticeId, noticeId)
                .last("LIMIT 1"));
        if (existed != null) {
            existed.setReadTime(LocalDateTime.now());
            noticeReadMapper.updateById(existed);
            return;
        }

        SysNoticeReadPo readPo = new SysNoticeReadPo();
        readPo.setUserId(userId);
        readPo.setNoticeId(noticeId);
        readPo.setReadTime(LocalDateTime.now());
        noticeReadMapper.insert(readPo);
    }

    private Long requireLoginUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return userId;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 5;
        }
        return Math.min(limit, 20);
    }

    private void broadcastNoticeRefresh() {
        sseEmitterService.broadcast("notice", "refresh");
    }

    private void pushNoticeRefreshToUser(Long userId) {
        sseEmitterService.sendToUser(String.valueOf(userId), "notice", "refresh");
    }
}
