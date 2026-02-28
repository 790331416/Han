package com.han.system.controller;

import com.han.api.system.domain.DeptVO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.domain.entity.User;
import com.han.system.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统服务内部接口（供其他微服务通过 HttpExchange 调用）
 *
 * <p>对应 {@code SystemServiceClient} 声明的所有接口端点。
 */
@InnerAuth
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final UserMapper userMapper;

    /**
     * 根据用户ID获取用户信息
     */
    @GetMapping("/user/{userId}")
    public R<UserVO> getUserById(@PathVariable("userId") Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        return R.ok(toApiUserVO(user));
    }

    /**
     * 根据用户名获取用户信息（登录流程核心接口）
     */
    @GetMapping("/user/info/{username}")
    public R<UserVO> getUserByUsername(@PathVariable("username") String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .last("LIMIT 1"));
        if (user == null) {
            return R.fail("用户不存在");
        }
        return R.ok(toApiUserVO(user));
    }

    /**
     * 获取用户角色列表
     */
    @GetMapping("/user/roles")
    public R<List<RoleVO>> getRolesByUserId(@RequestParam("userId") Long userId) {
        return R.ok(List.of());
    }

    /**
     * 获取用户权限列表
     */
    @GetMapping("/user/permissions")
    public R<Set<String>> getPermissionsByUserId(@RequestParam("userId") Long userId) {
        if (userId == 1L) {
            Set<String> perms = new HashSet<>();
            perms.add("*:*:*");
            return R.ok(perms);
        }
        Set<String> perms = userMapper.selectPermissionsByUserId(userId);
        return R.ok(perms != null ? perms : Set.of());
    }

    /**
     * 获取部门信息
     */
    @GetMapping("/dept/{deptId}")
    public R<DeptVO> getDeptById(@PathVariable("deptId") Long deptId) {
        return R.ok(new DeptVO());
    }

    /**
     * 获取用户数据权限部门ID列表
     */
    @GetMapping("/user/datascope/depts")
    public R<Set<Long>> getDataScopeDeptIds(@RequestParam("userId") Long userId) {
        return R.ok(Set.of());
    }

    /**
     * User 实体转 API UserVO
     */
    private UserVO toApiUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getId());
        vo.setDeptId(user.getDeptId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPassword(user.getPassword());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setSex(user.getSex());
        vo.setStatus(user.getStatus());
        vo.setLoginIp(user.getLoginIp());
        vo.setLoginTime(user.getLoginTime());
        // admin 拥有所有权限
        if (user.getId() != null && user.getId() == 1L) {
            vo.setPermissions(Set.of("*:*:*"));
            vo.setRoleKeys(Set.of("admin"));
        } else {
            Set<String> perms = userMapper.selectPermissionsByUserId(user.getId());
            vo.setPermissions(perms != null ? perms : Set.of());
            Set<String> roleKeys = userMapper.selectRoleKeysByUserId(user.getId());
            vo.setRoleKeys(roleKeys != null ? roleKeys : Set.of());
        }
        return vo;
    }
}
