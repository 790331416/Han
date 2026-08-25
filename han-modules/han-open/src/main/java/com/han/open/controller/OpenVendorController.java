package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.security.context.SecurityContextHolder;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.vo.VendorApplicationVO;
import com.han.open.domain.vo.VendorDetailVO;
import com.han.open.domain.vo.VendorProfileUpdateVO;
import com.han.open.domain.vo.OpenVendorApplicationAdminVO;
import com.han.open.service.OpenVendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 厂商主体控制器
 */
@AdminAuth
@Tag(name = "厂商管理", description = "厂商入驻、审核、查询接口")
@RestController
@RequestMapping("/open/vendor")
@RequiredArgsConstructor
public class OpenVendorController {

    private final OpenVendorService vendorService;

    @Operation(summary = "分页查询厂商")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('open:vendor:list')")
    public R<PageResult<OpenVendorPo>> list(@RequestParam(required = false) String name,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(vendorService.listPage(name, status, pageNum, pageSize));
    }

    @Operation(summary = "分页查询厂商入驻申请")
    @GetMapping("/applications")
    @PreAuthorize("@ss.hasAuthority('open:vendor:list')")
    public R<PageResult<OpenVendorApplicationAdminVO>> applications(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(vendorService.listApplicationPage(vendorId, status, pageNum, pageSize));
    }

    @Operation(summary = "提交厂商入驻申请")
    @PostMapping("/application")
    @PreAuthorize("@ss.hasAuthority('open:vendor:apply')")
    @RepeatSubmit
    @OperLog(module = "厂商管理", type = OperLog.OperType.INSERT, saveParams = false)
    public R<Long> submitApplication(@Validated @RequestBody VendorApplicationVO applicationVO) {
        return R.ok(vendorService.submitApplication(applicationVO));
    }

    @Operation(summary = "审核厂商入驻申请")
    @RequestMapping(value = "/application/review/{id}", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("@ss.hasAuthority('open:vendor:review')")
    @RepeatSubmit
    @OperLog(module = "厂商管理", type = OperLog.OperType.UPDATE)
    public R<Void> reviewApplication(@PathVariable Long id,
                                      @RequestParam Integer status,
                                      @RequestParam(required = false) String reason) {
        vendorService.reviewApplication(id, status, reason);
        return R.ok();
    }

    @Operation(summary = "获取厂商详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAuthority('open:vendor:query')")
    public R<VendorDetailVO> getDetail(@PathVariable Long id) {
        return R.ok(vendorService.getDetail(id));
    }

    @Operation(summary = "修改厂商基础资料")
    @RequestMapping(value = "/{vendorId}", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("@ss.hasAnyAuthority('open:vendor:manage','open:vendor:my')")
    @RepeatSubmit
    @OperLog(module = "厂商管理", type = OperLog.OperType.UPDATE)
    public R<Void> updateProfile(@PathVariable Long vendorId,
                                 @Validated @RequestBody VendorProfileUpdateVO profile) {
        vendorService.updateProfile(vendorId, profile);
        return R.ok();
    }

    @Operation(summary = "删除厂商")
    @PostMapping("/remove/{vendorId}")
    @PreAuthorize("@ss.hasAuthority('open:vendor:manage')")
    @RepeatSubmit
    @OperLog(module = "厂商管理", type = OperLog.OperType.DELETE)
    public R<Void> removeVendor(@PathVariable Long vendorId) {
        vendorService.removeVendor(vendorId);
        return R.ok();
    }

    @Operation(summary = "查询当前用户所属厂商列表")
    @GetMapping("/my")
    @PreAuthorize("@ss.hasAuthority('open:vendor:my')")
    public R<List<OpenVendorPo>> listMyVendors() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        return R.ok(vendorService.listByUserId(userId));
    }

    @Operation(summary = "关联用户到厂商")
    @PostMapping("/{vendorId}/bind-user")
    @PreAuthorize("@ss.hasAuthority('open:vendor:manage')")
    @RepeatSubmit
    @OperLog(module = "厂商管理", type = OperLog.OperType.GRANT)
    public R<Void> bindUser(@PathVariable Long vendorId,
                            @RequestParam Long userId,
                            @RequestParam String role) {
        vendorService.bindUser(vendorId, userId, role);
        return R.ok();
    }

    @Operation(summary = "变更厂商状态")
    @RequestMapping(value = "/{vendorId}/status", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("@ss.hasAuthority('open:vendor:manage')")
    @RepeatSubmit
    @OperLog(module = "厂商管理", type = OperLog.OperType.UPDATE)
    public R<Void> updateStatus(@PathVariable Long vendorId,
                                @RequestParam Integer status,
                                @RequestParam(required = false) String reason) {
        vendorService.updateStatus(vendorId, status, reason);
        return R.ok();
    }

}
