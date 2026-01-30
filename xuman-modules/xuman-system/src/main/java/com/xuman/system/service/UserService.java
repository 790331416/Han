package com.xuman.system.service;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.web.service.IBaseService;
import com.xuman.system.domain.dto.UserDTO;
import com.xuman.system.domain.query.UserQuery;
import com.xuman.system.domain.vo.UserVO;

import java.util.List;
import java.util.Set;

/**
 * 用户服务接口
 * 
 * <p>继承 {@link IBaseService} 获得通用CRUD能力：
 * <ul>
 *   <li>selectListScope - 查询列表（带数据权限）</li>
 *   <li>insert - 新增记录</li>
 *   <li>updateById - 根据ID修改</li>
 *   <li>deleteById - 根据ID删除</li>
 *   <li>deleteByIds - 批量删除</li>
 *   <li>selectById - 根据ID查询</li>
 *   <li>selectByIds - 批量查询</li>
 * </ul>
 * 
 * @see IBaseService
 */
public interface UserService extends IBaseService<UserQuery, UserDTO> {

    /**
     * 分页查询用户列表
     */
    PageResult<UserVO> selectUserPage(UserQuery query);

    /**
     * 根据用户名查询用户
     */
    UserDTO selectUserByUsername(String username);

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
