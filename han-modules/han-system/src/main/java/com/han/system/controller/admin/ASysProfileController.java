package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.domain.dto.ProfileDto;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.service.ISysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个人中心 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysProfileController")
@RequestMapping("/system/user/profile")
@RequiredArgsConstructor
public class ASysProfileController {

    private final ISysUserService userService;

    @GetMapping
    @PermissionExempt("登录用户获取自身个人信息，无需特定权限")
    public R<SysUserDto> getProfile() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(userService.selectById(userId));
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PermissionExempt("登录用户修改自身信息，无需特定权限")
    @OperLog(module = "个人中心", type = OperLog.OperType.UPDATE)
    public R<Void> updateProfile(@Valid @RequestBody ProfileDto dto) {
        Long userId = SecurityContextHolder.getUserId();
        userService.updateProfile(userId, dto);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/password")
    @PermissionExempt("登录用户修改自身密码，无需特定权限")
    @OperLog(module = "个人中心", type = OperLog.OperType.UPDATE, saveParams = false)
    public R<Void> updatePwd(@RequestBody java.util.Map<String, String> body) {
        Long userId = SecurityContextHolder.getUserId();
        userService.updatePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/avatar")
    @PermissionExempt("登录用户修改自身头像，无需特定权限")
    @OperLog(module = "个人中心", type = OperLog.OperType.UPDATE)
    public R<Void> updateAvatar(@RequestBody java.util.Map<String, String> body) {
        Long userId = SecurityContextHolder.getUserId();
        userService.updateAvatar(userId, body.get("avatar"));
        return R.ok();
    }
}
