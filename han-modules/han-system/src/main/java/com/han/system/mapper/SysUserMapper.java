package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 用户 Mapper 接口
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUserPo> {

    /**
     * 分页查询用户列表
     */
    IPage<UserVO> selectUserPage(IPage<UserVO> page, @Param("query") SysUserQuery query);

    /**
     * 查询用户列表（导出用，不分页，按 maxRows 截断）
     */
    List<UserVO> selectUserListForExport(@Param("query") SysUserQuery query, @Param("maxRows") int maxRows);

    /**
     * 根据ID查询用户详情
     */
    UserVO selectUserVoById(@Param("userId") Long userId);

    /**
     * 根据用户名查询用户
     */
    UserVO selectUserByUsername(@Param("username") String username, @Param("tenantId") Long tenantId);

    /**
     * 查询用户权限列表
     */
    Set<String> selectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户角色Key列表
     */
    Set<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /**
     * 查询用户角色ID列表
     */
    Set<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户岗位ID列表
     */
    Set<Long> selectPostIdsByUserId(@Param("userId") Long userId);

    /**
     * 检查用户名是否存在
     */
    int checkUsernameUnique(@Param("username") String username, @Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 检查手机号是否存在
     */
    int checkPhoneUnique(@Param("phone") String phone, @Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 检查邮箱是否存在
     */
    int checkEmailUnique(@Param("email") String email, @Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
