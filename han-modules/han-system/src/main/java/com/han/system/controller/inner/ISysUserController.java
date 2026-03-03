package com.han.system.controller.inner;

import com.han.api.system.domain.DeptVO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.domain.R;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.converter.SysUserApiConverter;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysUserMapper;
import com.han.system.service.ISysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 用户管理 - I层（内部接口控制器）
 *
 * <p>面向微服务内部 RPC 调用，对应 {@code SystemServiceClient}。
 */
@InnerAuth
@RestController("innerSysUserController")
@RequestMapping("/inner/system")
@RequiredArgsConstructor
public class ISysUserController {

    private final ISysUserService sysUserService;
    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;
    private final SysUserApiConverter sysUserApiConverter;

    /**
     * 根据用户ID获取用户信息
     */
    @GetMapping("/user/{userId}")
    public R<UserVO> getUserById(@PathVariable("userId") Long userId) {
        SysUserPo po = sysUserMapper.selectById(userId);
        if (po == null) {
            return R.fail("用户不存在");
        }
        return R.ok(toApiUserVO(po));
    }

    /**
     * 根据用户名获取用户信息（登录流程核心接口）
     * <p>
     * 登录时 SecurityContext 尚无 tenantId，因此使用 TenantHelper.ignore 跳过自动拦截器，手动控制租户条件。
     */
    @GetMapping("/user/info/{username}")
    public R<UserVO> getUserByUsername(@PathVariable("username") String username,
                                      @RequestParam(value = "tenantId", required = false) Long tenantId) {
        SysUserPo po = TenantHelper.ignore(() -> {
            LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<SysUserPo>()
                    .eq(SysUserPo::getUsername, username);
            if (tenantId != null) {
                wrapper.eq(SysUserPo::getTenantId, tenantId);
            }
            wrapper.last("LIMIT 1");
            return sysUserMapper.selectOne(wrapper);
        });
        if (po == null) {
            return R.fail("用户不存在");
        }
        return R.ok(toApiUserVO(po));
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
        return R.ok(sysUserService.selectPermissionsByUserId(userId));
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
     * 统计租户下用户数量
     */
    @GetMapping("/user/count")
    public R<Integer> countUsersByTenantId(@RequestParam("tenantId") Long tenantId) {
        long count = TenantHelper.ignore(() ->
                sysUserMapper.selectCount(
                        new LambdaQueryWrapper<SysUserPo>()
                                .eq(SysUserPo::getTenantId, tenantId)
                )
        );
        return R.ok((int) count);
    }

    /**
     * 跨租户查询用户名在所有租户的账号（租户切换使用）
     */
    @GetMapping("/user/tenants")
    public R<List<java.util.Map<String, Object>>> getUserTenants(@RequestParam("username") String username) {
        List<java.util.Map<String, Object>> result = TenantHelper.ignore(() -> {
            List<SysUserPo> users = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUserPo>()
                            .eq(SysUserPo::getUsername, username)
                            .select(SysUserPo::getId, SysUserPo::getTenantId, SysUserPo::getStatus)
            );
            return users.stream().map(u -> {
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("userId", u.getId());
                map.put("tenantId", u.getTenantId());
                map.put("status", u.getStatus());
                return map;
            }).toList();
        });
        return R.ok(result);
    }

    /**
     * SysUserPo 转 API UserVO（使用 MapStruct + 手动补充权限字段）
     */
    private UserVO toApiUserVO(SysUserPo po) {
        UserVO vo = sysUserApiConverter.toApiUserVO(po);
        if (po.isAdmin()) {
            vo.setPermissions(Set.of("*:*:*"));
            vo.setRoleKeys(Set.of("admin"));
        } else {
            Set<String> perms = sysUserMapper.selectPermissionsByUserId(po.getId());
            vo.setPermissions(perms != null ? perms : Set.of());
            Set<String> roleKeys = sysUserMapper.selectRoleKeysByUserId(po.getId());
            vo.setRoleKeys(roleKeys != null ? roleKeys : Set.of());
        }
        return vo;
    }
}
