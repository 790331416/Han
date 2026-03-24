package com.han.system.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.R;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户内部查询控制器。
 */
@InnerAuth
@RestController
@RequestMapping("/inner/system/tenant")
@RequiredArgsConstructor
public class ITenantQueryController {

    private final SysUserMapper sysUserMapper;

    /**
     * 查询租户管理员用户 ID。
     */
    @GetMapping("/adminUser")
    public R<Long> getTenantAdminUserId(@RequestParam("tenantId") Long tenantId) {
        SysUserPo admin = TenantHelper.ignore(() -> sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserPo>()
                        .eq(SysUserPo::getTenantId, tenantId)
                        .orderByAsc(SysUserPo::getId)
                        .last("LIMIT 1")
        ));
        if (admin == null) {
            return R.fail("该租户下无用户");
        }
        return R.ok(admin.getId());
    }
}
