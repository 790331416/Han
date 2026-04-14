package com.han.system.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.api.system.domain.DeptVO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.domain.R;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.converter.SysUserApiConverter;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.po.SysDeptPo;
import com.han.system.domain.po.SysRoleDeptPo;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysDeptMapper;
import com.han.system.mapper.SysRoleDeptMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户管理 - I 层（内部接口控制器）
 */
@InnerAuth
@RestController("innerSysUserController")
@RequestMapping("/inner/system")
@RequiredArgsConstructor
public class ISysUserController {

    private final ISysUserService sysUserService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserConverter sysUserConverter;
    private final SysUserApiConverter sysUserApiConverter;

    @GetMapping("/user/{userId}")
    public R<UserVO> getUserById(@PathVariable("userId") Long userId) {
        SysUserPo po = TenantHelper.ignore(() -> sysUserMapper.selectById(userId));
        if (po == null) {
            return R.fail("用户不存在");
        }
        return R.ok(toApiUserVO(po));
    }

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

    @GetMapping("/user/roles")
    public R<List<RoleVO>> getRolesByUserId(@RequestParam("userId") Long userId) {
        Set<Long> roleIds = TenantHelper.ignore(() -> sysUserMapper.selectRoleIdsByUserId(userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return R.ok(List.of());
        }

        List<RoleVO> roles = TenantHelper.ignore(() -> roleMapper.selectList(
                new LambdaQueryWrapper<SysRolePo>()
                        .in(SysRolePo::getId, roleIds)
                        .eq(SysRolePo::getDelFlag, 0)
                        .orderByAsc(SysRolePo::getRoleSort)
        )).stream().map(role -> {
            RoleVO vo = new RoleVO();
            vo.setRoleId(role.getId());
            vo.setTenantId(role.getTenantId());
            vo.setRoleName(role.getRoleName());
            vo.setRoleKey(role.getRoleKey());
            vo.setDataScope(role.getDataScope());
            vo.setSort(role.getRoleSort());
            vo.setStatus(role.getStatus());
            return vo;
        }).toList();
        return R.ok(roles);
    }

    @GetMapping("/user/permissions")
    public R<Set<String>> getPermissionsByUserId(@RequestParam("userId") Long userId) {
        return R.ok(sysUserService.selectPermissionsByUserId(userId));
    }

    @GetMapping("/dept/{deptId}")
    public R<DeptVO> getDeptById(@PathVariable("deptId") Long deptId) {
        SysDeptPo dept = TenantHelper.ignore(() -> deptMapper.selectById(deptId));
        if (dept == null) {
            return R.fail("部门不存在");
        }

        DeptVO vo = new DeptVO();
        vo.setDeptId(dept.getId());
        vo.setTenantId(dept.getTenantId());
        vo.setParentId(dept.getParentId());
        vo.setAncestors(dept.getAncestors());
        vo.setDeptName(dept.getDeptName());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setSort(dept.getOrderNum());
        vo.setStatus(dept.getStatus());
        vo.setLeader(dept.getLeaderName());
        return R.ok(vo);
    }

    @GetMapping("/user/datascope/depts")
    public R<Set<Long>> getDataScopeDeptIds(@RequestParam("userId") Long userId) {
        return R.ok(TenantHelper.ignore(() -> resolveDataScopeDeptIds(userId)));
    }

    @GetMapping("/user/count")
    public R<Integer> countUsersByTenantId(@RequestParam("tenantId") Long tenantId) {
        long count = TenantHelper.ignore(() -> sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUserPo>().eq(SysUserPo::getTenantId, tenantId)
        ));
        return R.ok((int) count);
    }

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

    @PostMapping("/user/totp")
    public R<Void> updateTotpSecret(@RequestParam("userId") Long userId,
                                    @RequestParam(value = "secret", required = false) String secret) {
        SysUserPo po = new SysUserPo();
        po.setId(userId);
        po.setTotpSecret(secret);
        po.setTotpEnabled(secret != null ? 1 : 0);
        sysUserMapper.updateById(po);
        return R.ok();
    }

    @GetMapping("/user/totp/{userId}")
    public R<String> getTotpSecret(@PathVariable("userId") Long userId) {
        SysUserPo po = TenantHelper.ignore(() -> sysUserMapper.selectById(userId));
        if (po == null) {
            return R.fail("用户不存在");
        }
        return R.ok(po.getTotpSecret());
    }

    private Set<Long> resolveDataScopeDeptIds(Long userId) {
        SysUserPo user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Set.of();
        }
        if (user.isAdmin()) {
            return null;
        }

        Set<Long> roleIds = sysUserMapper.selectRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }

        List<SysRolePo> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRolePo>()
                        .in(SysRolePo::getId, roleIds)
                        .eq(SysRolePo::getDelFlag, 0)
                        .eq(SysRolePo::getStatus, 0)
        );
        if (roles.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Long> deptIds = new LinkedHashSet<>();
        boolean onlySelf = true;
        for (SysRolePo role : roles) {
            String dataScope = role.getDataScope();
            if ("1".equals(dataScope)) {
                return null;
            }
            switch (dataScope) {
                case "2" -> {
                    onlySelf = false;
                    List<SysRoleDeptPo> roleDepts = roleDeptMapper.selectList(
                            new LambdaQueryWrapper<SysRoleDeptPo>().eq(SysRoleDeptPo::getRoleId, role.getId())
                    );
                    roleDepts.stream().map(SysRoleDeptPo::getDeptId).forEach(deptIds::add);
                }
                case "3" -> {
                    onlySelf = false;
                    if (user.getDeptId() != null) {
                        deptIds.add(user.getDeptId());
                    }
                }
                case "4" -> {
                    onlySelf = false;
                    if (user.getDeptId() != null) {
                        deptIds.add(user.getDeptId());
                        List<SysDeptPo> children = deptMapper.selectList(
                                new LambdaQueryWrapper<SysDeptPo>()
                                        .select(SysDeptPo::getId)
                                        .apply("position(',' || {0} || ',' in ',' || ancestors || ',') > 0", user.getDeptId())
                        );
                        children.stream().map(SysDeptPo::getId).forEach(deptIds::add);
                    }
                }
                case "5" -> {
                    // 仅本人模式，不追加部门范围
                }
                default -> {
                    onlySelf = false;
                }
            }
        }

        if (deptIds.isEmpty() && onlySelf) {
            return Set.of();
        }
        return deptIds;
    }

    private UserVO toApiUserVO(SysUserPo po) {
        UserVO vo = sysUserApiConverter.toApiUserVO(po);
        if (po.isAdmin()) {
            vo.setPermissions(Set.of("*:*:*"));
            vo.setRoleKeys(Set.of("admin"));
            return vo;
        }

        Set<String> perms = sysUserMapper.selectPermissionsByUserId(po.getId());
        vo.setPermissions(perms != null ? perms : Set.of());
        Set<String> roleKeys = sysUserMapper.selectRoleKeysByUserId(po.getId());
        vo.setRoleKeys(roleKeys != null ? roleKeys : Set.of());
        return vo;
    }
}
