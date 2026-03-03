package com.han.system.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.R;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.domain.po.*;
import com.han.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户数据清理 - I层（内部 RPC 接口）
 * <p>
 * 由 han-tenant 模块在删除租户时调用，负责清理 han-system 中的租户业务数据。
 */
@Slf4j
@InnerAuth
@RestController("innerTenantCleanupController")
@RequestMapping("/inner/system/tenant")
@RequiredArgsConstructor
public class TenantCleanupController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @PostMapping("/cleanup")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> cleanupTenantData(@RequestParam Long tenantId) {
        log.info("开始清理租户[{}]业务数据", tenantId);

        // 使用 TenantHelper.ignore 跳过租户拦截器，直接按 tenant_id 操作
        TenantHelper.ignore(() -> {
            // 1. 查出该租户下的用户ID和角色ID（用于清理关联表）
            List<Long> userIds = userMapper.selectList(
                    new LambdaQueryWrapper<SysUserPo>().eq(SysUserPo::getTenantId, tenantId).select(SysUserPo::getId)
            ).stream().map(SysUserPo::getId).toList();

            List<Long> roleIds = roleMapper.selectList(
                    new LambdaQueryWrapper<SysRolePo>().eq(SysRolePo::getTenantId, tenantId).select(SysRolePo::getId)
            ).stream().map(SysRolePo::getId).toList();

            // 2. 清理关联表（物理删除）
            if (!userIds.isEmpty()) {
                userRoleMapper.delete(new LambdaQueryWrapper<SysUserRolePo>().in(SysUserRolePo::getUserId, userIds));
                userPostMapper.delete(new LambdaQueryWrapper<SysUserPostPo>().in(SysUserPostPo::getUserId, userIds));
                log.info("清理租户[{}]用户关联表完成, userCount={}", tenantId, userIds.size());
            }
            if (!roleIds.isEmpty()) {
                roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenuPo>().in(SysRoleMenuPo::getRoleId, roleIds));
                log.info("清理租户[{}]角色菜单关联表完成, roleCount={}", tenantId, roleIds.size());
            }

            // 3. 查出部门ID和岗位ID
            List<Long> deptIds = deptMapper.selectList(
                    new LambdaQueryWrapper<SysDeptPo>().eq(SysDeptPo::getTenantId, tenantId).select(SysDeptPo::getId)
            ).stream().map(SysDeptPo::getId).toList();

            List<Long> postIds = postMapper.selectList(
                    new LambdaQueryWrapper<SysPostPo>().eq(SysPostPo::getTenantId, tenantId).select(SysPostPo::getId)
            ).stream().map(SysPostPo::getId).toList();

            // 4. 逻辑删除业务数据
            if (!userIds.isEmpty()) {
                userMapper.deleteByIds(userIds);
            }
            if (!roleIds.isEmpty()) {
                roleMapper.deleteByIds(roleIds);
            }
            if (!deptIds.isEmpty()) {
                deptMapper.deleteByIds(deptIds);
            }
            if (!postIds.isEmpty()) {
                postMapper.deleteByIds(postIds);
            }

            log.info("清理租户[{}]业务数据完成", tenantId);
        });

        return R.ok();
    }
}
