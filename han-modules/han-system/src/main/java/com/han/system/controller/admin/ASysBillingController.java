package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.domain.po.SysTenantBillPo;
import com.han.system.domain.po.SysTenantSubscriptionPo;
import com.han.system.mapper.SysTenantBillMapper;
import com.han.system.mapper.SysTenantSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 租户计费 - 管理端控制器
 */
@AdminAuth
@RestController("adminSysBillingController")
@RequestMapping("/system/billing")
@RequiredArgsConstructor
public class ASysBillingController {

    private final SysTenantSubscriptionMapper subscriptionMapper;
    private final SysTenantBillMapper billMapper;

    /**
     * 查询当前租户的订阅信息
     */
    @GetMapping("/subscription/current")
    @PermissionExempt("已登录用户查看自身租户订阅，无需特定权限")
    public R<SysTenantSubscriptionPo> currentSubscription() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("未获取到租户信息");
        }
        SysTenantSubscriptionPo sub = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<SysTenantSubscriptionPo>()
                        .eq(SysTenantSubscriptionPo::getTenantId, tenantId)
                        .eq(SysTenantSubscriptionPo::getStatus, 0)
                        .orderByDesc(SysTenantSubscriptionPo::getEndTime)
                        .last("LIMIT 1"));
        return R.ok(sub);
    }

    /**
     * 查询订阅历史
     */
    @GetMapping("/subscription/list")
    @PermissionExempt("已登录用户查看自身租户订阅历史")
    public R<PageResult<SysTenantSubscriptionPo>> subscriptionList(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) throw new BusinessException("未获取到租户信息");
        Page<SysTenantSubscriptionPo> page = subscriptionMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysTenantSubscriptionPo>()
                        .eq(SysTenantSubscriptionPo::getTenantId, tenantId)
                        .orderByDesc(SysTenantSubscriptionPo::getCreateTime));
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 查询账单列表
     */
    @GetMapping("/bill/list")
    @PermissionExempt("已登录用户查看自身租户账单")
    public R<PageResult<SysTenantBillPo>> billList(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) throw new BusinessException("未获取到租户信息");
        Page<SysTenantBillPo> page = billMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysTenantBillPo>()
                        .eq(SysTenantBillPo::getTenantId, tenantId)
                        .orderByDesc(SysTenantBillPo::getCreateTime));
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    // ==================== 管理员操作（平台管理员） ====================

    /**
     * 管理员查看所有租户订阅
     */
    @GetMapping("/admin/subscription/list")
    @PreAuthorize("@ss.hasAuthority('system:tenant:list')")
    public R<PageResult<SysTenantSubscriptionPo>> adminSubscriptionList(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<SysTenantSubscriptionPo> wrapper = new LambdaQueryWrapper<SysTenantSubscriptionPo>()
                .eq(tenantId != null, SysTenantSubscriptionPo::getTenantId, tenantId)
                .orderByDesc(SysTenantSubscriptionPo::getCreateTime);
        Page<SysTenantSubscriptionPo> page = subscriptionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 管理员为租户添加订阅
     */
    @PostMapping("/admin/subscription/add")
    @PreAuthorize("@ss.hasAuthority('system:tenant:edit')")
    public R<Void> addSubscription(@RequestBody SysTenantSubscriptionPo sub) {
        sub.setStatus(0);
        sub.setCreateTime(LocalDateTime.now());
        subscriptionMapper.insert(sub);
        return R.ok();
    }
}
