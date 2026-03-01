package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.system.controller.base.BSysUserController;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.vo.CurrentUserVO;
import com.han.system.domain.vo.UserVO;
import com.han.system.service.ISysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 用户管理 - A层（管理端控制器）
 *
 * <p>面向 UI 管理系统，处理 HTTP 请求。
 */
@AdminAuth
@RestController("adminSysUserController")
@RequestMapping("/system/user")
public class ASysUserController extends BSysUserController {

    public ASysUserController(ISysUserService service) {
        super(service);
    }

    @Override
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:user:list')")
    public R<PageResult<UserVO>> list(SysUserQuery query) {
        return super.list(query);
    }

    @Override
    @GetMapping("/info/{userId}")
    @PreAuthorize("@ss.hasAuthority('system:user:query')")
    public R<SysUserDto> getInfo(@PathVariable Long userId) {
        return super.getInfo(userId);
    }

    @Override
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:user:add')")
    public R<Void> add(@RequestBody SysUserDto dto) {
        return super.add(dto);
    }

    @Override
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:user:edit')")
    public R<Void> edit(@RequestBody SysUserDto dto) {
        return super.edit(dto);
    }

    @Override
    @PostMapping("/remove/{userId}")
    @PreAuthorize("@ss.hasAuthority('system:user:remove')")
    public R<Void> remove(@PathVariable Long userId) {
        return super.remove(userId);
    }

    @Override
    @PostMapping("/resetPwd")
    @PreAuthorize("@ss.hasAuthority('system:user:resetPwd')")
    public R<Void> resetPwd(@RequestParam Long userId, @RequestParam String password) {
        return super.resetPwd(userId, password);
    }

    @Override
    @PostMapping("/changeStatus")
    @PreAuthorize("@ss.hasAuthority('system:user:edit')")
    public R<Void> changeStatus(@RequestParam Long userId, @RequestParam Integer status) {
        return super.changeStatus(userId, status);
    }

    /**
     * 获取当前登录用户信息（通过网关传递的 X-User-Id header）
     */
    @GetMapping("/current")
    @PermissionExempt("登录用户获取自身信息，无需特定权限")
    public R<CurrentUserVO> getCurrentUserInfo(@RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) {
            return R.fail("未获取到用户信息");
        }
        Long userId = Long.parseLong(userIdStr);
        SysUserDto user = baseService.selectById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        Set<String> roleKeys = baseService.selectRoleKeysByUserId(userId);
        Set<String> perms = baseService.selectPermissionsByUserId(userId);
        CurrentUserVO vo = CurrentUserVO.builder()
                .userId(user.getUserId())
                .tenantId(null)
                .deptId(user.getDeptId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .roles(roleKeys != null ? roleKeys : Set.of())
                .permissions(perms != null ? perms : Set.of())
                .build();
        return R.ok(vo);
    }

}
