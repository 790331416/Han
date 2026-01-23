package com.xuman.system.controller;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.core.domain.R;
import com.xuman.common.log.annotation.OperLog;
import com.xuman.common.security.annotation.RequiresPermission;
import com.xuman.system.domain.dto.UserDTO;
import com.xuman.system.domain.query.UserQuery;
import com.xuman.system.domain.vo.UserVO;
import com.xuman.system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理Controller
 */
@RestController
@RequestMapping("/system/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 查询用户列表
     */
    @RequiresPermission("system:user:list")
    @GetMapping("/list")
    public R<PageResult<UserVO>> list(UserQuery query) {
        return R.ok(userService.selectUserPage(query));
    }

    /**
     * 获取用户详情
     */
    @RequiresPermission("system:user:query")
    @GetMapping("/{userId}")
    public R<UserVO> getInfo(@PathVariable Long userId) {
        return R.ok(userService.selectUserById(userId));
    }

    /**
     * 新增用户
     */
    @RequiresPermission("system:user:add")
    @OperLog(module = "用户管理", type = OperLog.OperType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody @Valid UserDTO dto) {
        userService.insertUser(dto);
        return R.ok();
    }

    /**
     * 修改用户
     */
    @RequiresPermission("system:user:edit")
    @OperLog(module = "用户管理", type = OperLog.OperType.UPDATE)
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody @Valid UserDTO dto) {
        userService.updateUser(dto);
        return R.ok();
    }

    /**
     * 删除用户
     */
    @RequiresPermission("system:user:remove")
    @OperLog(module = "用户管理", type = OperLog.OperType.DELETE)
    @PostMapping("/remove/{userId}")
    public R<Void> remove(@PathVariable Long userId) {
        userService.deleteUserById(userId);
        return R.ok();
    }

    /**
     * 批量删除用户
     */
    @RequiresPermission("system:user:remove")
    @OperLog(module = "用户管理", type = OperLog.OperType.DELETE)
    @PostMapping("/remove")
    public R<Void> removeBatch(@RequestBody List<Long> userIds) {
        userService.deleteUserByIds(userIds);
        return R.ok();
    }

    /**
     * 重置密码
     */
    @RequiresPermission("system:user:resetPwd")
    @OperLog(module = "用户管理", type = OperLog.OperType.UPDATE)
    @PostMapping("/resetPwd")
    public R<Void> resetPwd(@RequestBody UserDTO dto) {
        userService.resetPwd(dto.getUserId(), dto.getPassword());
        return R.ok();
    }

    /**
     * 修改状态
     */
    @RequiresPermission("system:user:edit")
    @OperLog(module = "用户管理", type = OperLog.OperType.UPDATE)
    @PostMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody UserDTO dto) {
        userService.updateUserStatus(dto.getUserId(), dto.getStatus());
        return R.ok();
    }
}
