package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.common.web.service.IBaseService;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.vo.UserVO;

import java.util.Set;

/**
 * 用户服务接口
 *
 * @see IBaseService
 */
public interface ISysUserService extends IBaseService<SysUserQuery, SysUserDto> {

    /**
     * 分页查询用户列表
     */
    PageResult<UserVO> selectUserPage(SysUserQuery query);

    /**
     * 根据用户名查询用户
     */
    SysUserDto selectUserByUsername(String username);

    /**
     * 重置密码
     */
    void resetPwd(Long userId, String password);

    /**
     * 修改用户状态
     */
    void updateUserStatus(Long userId, Integer status);

    /**
     * 获取用户权限列表
     */
    Set<String> selectPermissionsByUserId(Long userId);

    /**
     * 获取用户角色Key列表
     */
    Set<String> selectRoleKeysByUserId(Long userId);
}
