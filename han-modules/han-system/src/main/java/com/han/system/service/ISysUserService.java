package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.common.web.service.IBaseService;
import com.han.system.domain.dto.ProfileDto;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.vo.UserImportVo;
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

    /**
     * 修改个人信息（仅允许修改昵称/手机/邮箱/性别）
     */
    void updateProfile(Long userId, ProfileDto dto);

    /**
     * 修改密码（校验旧密码）
     */
    void updatePassword(Long userId, String oldPwd, String newPwd);

    /**
     * 修改头像
     */
    void updateAvatar(Long userId, String avatarUrl);

    /**
     * 批量导入用户
     *
     * @param list          导入数据列表
     * @param updateSupport 是否覆盖已存在用户
     * @return 导入结果描述
     */
    String importUsers(java.util.List<UserImportVo> list, boolean updateSupport);

    /**
     * 查询简单用户列表（下拉选择用）
     *
     * <p>返回当前租户下正常状态用户的 userId/nickname；手机号与邮箱只对具备
     * 用户查询或部门维护权限的调用方下发。结果按关键字过滤并限制条数，
     * 避免最低权限账号一次性拉走整租户通讯录。
     *
     * @param keyword 昵称/用户名模糊关键字，可为空
     */
    java.util.List<com.han.system.domain.vo.SimpleUserVo> selectSimpleUserList(String keyword);
}
