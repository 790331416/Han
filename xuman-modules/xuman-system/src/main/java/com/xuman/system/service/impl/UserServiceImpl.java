package com.xuman.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuman.common.core.domain.PageResult;
import com.xuman.common.core.exception.BusinessException;
import com.xuman.common.core.util.PasswordUtil;
import com.xuman.common.core.util.XuStrUtil;
import com.xuman.common.security.context.SecurityContextHolder;
import com.xuman.common.security.util.DataOwnerUtil;
import com.xuman.system.convert.UserConvert;
import com.xuman.system.domain.dto.UserDTO;
import com.xuman.system.domain.entity.User;
import com.xuman.system.domain.query.UserQuery;
import com.xuman.system.domain.vo.UserVO;
import com.xuman.system.mapper.UserMapper;
import com.xuman.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final UserConvert userConvert;

    @Override
    public PageResult<UserVO> selectUserPage(UserQuery query) {
        IPage<UserVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        page = userMapper.selectUserPage(page, query);
        return new PageResult<>(page.getRecords(), page.getTotal(), 
            (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public UserVO selectUserById(Long userId) {
        UserVO user = userMapper.selectUserVoById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 查询角色和岗位
        user.setRoleIds(userMapper.selectRoleIdsByUserId(userId));
        user.setPostIds(userMapper.selectPostIdsByUserId(userId));
        return user;
    }

    @Override
    public UserVO selectUserByUsername(String username) {
        Long tenantId = SecurityContextHolder.getTenantId();
        return userMapper.selectUserByUsername(username, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUser(UserDTO dto) {
        Long tenantId = SecurityContextHolder.getTenantId();
        
        // 校验用户名唯一性
        if (userMapper.checkUsernameUnique(dto.getUsername(), tenantId, null) > 0) {
            throw new BusinessException("用户名'" + dto.getUsername() + "'已存在");
        }
        
        // 校验手机号唯一性
        if (XuStrUtil.isNotBlank(dto.getPhone()) && 
            userMapper.checkPhoneUnique(dto.getPhone(), tenantId, null) > 0) {
            throw new BusinessException("手机号'" + dto.getPhone() + "'已存在");
        }
        
        // 校验密码强度
        PasswordUtil.validate(dto.getPassword());
        
        // 创建用户实体
        User user = userConvert.toEntity(dto);
        user.setPassword(PasswordUtil.encrypt(dto.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(0);
        }
        
        // 保存用户
        save(user);
        
        // 保存用户角色关联
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            // 防止越权分配角色
            DataOwnerUtil.checkRolePermission(dto.getRoleIds());
            insertUserRole(user.getId(), dto.getRoleIds());
        }
        
        // 保存用户岗位关联
        if (dto.getPostIds() != null && !dto.getPostIds().isEmpty()) {
            insertUserPost(user.getId(), dto.getPostIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserDTO dto) {
        Long tenantId = SecurityContextHolder.getTenantId();
        
        // 检查用户是否存在
        User existUser = getById(dto.getUserId());
        if (existUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 校验手机号唯一性
        if (XuStrUtil.isNotBlank(dto.getPhone()) && 
            userMapper.checkPhoneUnique(dto.getPhone(), tenantId, dto.getUserId()) > 0) {
            throw new BusinessException("手机号'" + dto.getPhone() + "'已存在");
        }
        
        // 更新用户信息
        userConvert.updateEntity(dto, existUser);
        updateById(existUser);
        
        // 更新角色关联
        if (dto.getRoleIds() != null) {
            DataOwnerUtil.checkRolePermission(dto.getRoleIds());
            deleteUserRole(existUser.getId());
            if (!dto.getRoleIds().isEmpty()) {
                insertUserRole(existUser.getId(), dto.getRoleIds());
            }
        }
        
        // 更新岗位关联
        if (dto.getPostIds() != null) {
            deleteUserPost(existUser.getId());
            if (!dto.getPostIds().isEmpty()) {
                insertUserPost(existUser.getId(), dto.getPostIds());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserById(Long userId) {
        // 不能删除超级管理员
        if (userId == 1L) {
            throw new BusinessException("不允许删除超级管理员");
        }
        
        // 不能删除自己
        if (userId.equals(SecurityContextHolder.getUserId())) {
            throw new BusinessException("不能删除当前登录用户");
        }
        
        // 删除用户角色关联
        deleteUserRole(userId);
        // 删除用户岗位关联
        deleteUserPost(userId);
        // 逻辑删除用户
        removeById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserByIds(List<Long> userIds) {
        for (Long userId : userIds) {
            deleteUserById(userId);
        }
    }

    @Override
    public void resetPwd(Long userId, String password) {
        // 校验密码强度
        PasswordUtil.validate(password);
        
        User user = new User();
        user.setId(userId);
        user.setPassword(PasswordUtil.encrypt(password));
        updateById(user);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        // 不能停用超级管理员
        if (userId == 1L && status == 1) {
            throw new BusinessException("不允许停用超级管理员");
        }
        
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        updateById(user);
    }

    @Override
    public Set<String> selectPermissionsByUserId(Long userId) {
        // 超级管理员拥有所有权限
        if (userId == 1L) {
            Set<String> perms = new HashSet<>();
            perms.add("*:*:*");
            return perms;
        }
        return userMapper.selectPermissionsByUserId(userId);
    }

    @Override
    public Set<String> selectRoleKeysByUserId(Long userId) {
        // 超级管理员
        if (userId == 1L) {
            Set<String> roles = new HashSet<>();
            roles.add("admin");
            return roles;
        }
        return userMapper.selectRoleKeysByUserId(userId);
    }

    /**
     * 新增用户角色关联
     */
    private void insertUserRole(Long userId, Set<Long> roleIds) {
        // TODO: 批量插入sys_user_role表
    }

    /**
     * 删除用户角色关联
     */
    private void deleteUserRole(Long userId) {
        // TODO: 删除sys_user_role表中userId对应的记录
    }

    /**
     * 新增用户岗位关联
     */
    private void insertUserPost(Long userId, Set<Long> postIds) {
        // TODO: 批量插入sys_user_post表
    }

    /**
     * 删除用户岗位关联
     */
    private void deleteUserPost(Long userId) {
        // TODO: 删除sys_user_post表中userId对应的记录
    }
}
