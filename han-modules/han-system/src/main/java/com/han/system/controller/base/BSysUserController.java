package com.han.system.controller.base;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.vo.UserVO;
import com.han.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;

/**
 * 用户管理 - B层（基础控制器）
 *
 * <p>持有 Service，实现通用业务逻辑流转。
 * 不标注 @RestController，不直接暴露接口。
 */
@RequiredArgsConstructor
public class BSysUserController {

    protected final ISysUserService baseService;

    protected String getNodeName() {
        return "用户管理";
    }

    /**
     * 分页查询用户列表
     */
    public R<PageResult<UserVO>> list(SysUserQuery query) {
        return R.ok(baseService.selectUserPage(query));
    }

    /**
     * 获取用户详情
     */
    public R<SysUserDto> getInfo(Long userId) {
        return R.ok(baseService.selectById(userId));
    }

    /**
     * 新增用户
     */
    public R<Void> add(SysUserDto dto) {
        baseService.insert(dto);
        return R.ok();
    }

    /**
     * 修改用户
     */
    public R<Void> edit(SysUserDto dto) {
        baseService.update(dto);
        return R.ok();
    }

    /**
     * 删除用户
     */
    public R<Void> remove(Long userId) {
        baseService.deleteById(userId);
        return R.ok();
    }

    /**
     * 重置密码
     */
    public R<Void> resetPwd(Long userId, String password) {
        baseService.resetPwd(userId, password);
        return R.ok();
    }

    /**
     * 修改状态
     */
    public R<Void> changeStatus(Long userId, Integer status) {
        baseService.updateUserStatus(userId, status);
        return R.ok();
    }
}
