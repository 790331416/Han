package com.xuman.system.service;

import com.xuman.common.core.domain.PageResult;
import com.xuman.system.domain.dto.UserDTO;
import com.xuman.system.domain.query.UserQuery;
import com.xuman.system.domain.vo.UserVO;

import java.util.List;
import java.util.Set;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 分页查询用户列表
     */
    PageResult<UserVO> selectUserPage(UserQuery query);

    /**
     * 根据ID查询用户
     */
    UserVO selectUserById(Long userId);

    /**
     * 根据用户名查询用户
     */
    UserVO selectUserByUsername(String username);

    /**
     * 新增用户
     */
    void insertUser(UserDTO dto);

    /**
     * 修改用户
     */
    void updateUser(UserDTO dto);

    /**
     * 删除用户
     */
    void deleteUserById(Long userId);

    /**
     * 批量删除用户
     */
    void deleteUserByIds(List<Long> userIds);

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
