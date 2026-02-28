package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.util.XuStrUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.util.DataOwnerUtil;
import com.han.system.domain.dto.UserDTO;
import com.han.system.domain.entity.User;
import com.han.system.domain.query.UserQuery;
import com.han.system.domain.vo.UserVO;
import com.han.system.mapper.UserMapper;
import com.han.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;

    @Override
    public PageResult<UserVO> selectUserPage(UserQuery query) {
        IPage<UserVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        page = userMapper.selectUserPage(page, query);
        return new PageResult<>(page.getRecords(), page.getTotal(), 
            (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<UserDTO> selectListScope(UserQuery query) {
        return selectList(query);
    }

    @Override
    public List<UserDTO> selectList(UserQuery query) {
        return List.of();
    }

    @Override
    public UserDTO selectById(Long id) {
        User user = getById(id);
        if (user == null) {
            return null;
        }
        UserDTO dto = toDto(user);
        dto.setRoleIds(userMapper.selectRoleIdsByUserId(id));
        dto.setPostIds(userMapper.selectPostIdsByUserId(id));
        return dto;
    }

    @Override
    public List<UserDTO> selectByIds(List<Long> ids) {
        List<User> users = listByIds(ids);
        return users.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public UserDTO selectUserByUsername(String username) {
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (user == null) {
            return null;
        }
        return toDto(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(UserDTO dto) {
        Long tenantId = SecurityContextHolder.getTenantId();
        
        String username = dto.getBase() != null ? dto.getBase().getUsername() : null;
        if (username != null && userMapper.checkUsernameUnique(username, tenantId, null) > 0) {
            throw new BusinessException("用户名'" + username + "'已存在");
        }
        
        String phone = dto.getBase() != null ? dto.getBase().getPhone() : null;
        if (XuStrUtil.isNotBlank(phone) && 
            userMapper.checkPhoneUnique(phone, tenantId, null) > 0) {
            throw new BusinessException("手机号'" + phone + "'已存在");
        }
        
        PasswordUtil.validate(dto.getPassword());
        
        User user = toEntity(dto);
        user.setPassword(PasswordUtil.encrypt(dto.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(0);
        }
        
        save(user);
        
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            DataOwnerUtil.checkRolePermission(dto.getRoleIds());
            insertUserRole(user.getId(), dto.getRoleIds());
        }
        
        if (dto.getPostIds() != null && !dto.getPostIds().isEmpty()) {
            insertUserPost(user.getId(), dto.getPostIds());
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(UserDTO dto) {
        Long tenantId = SecurityContextHolder.getTenantId();
        
        User existUser = getById(dto.getUserId());
        if (existUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        String phone = dto.getBase() != null ? dto.getBase().getPhone() : null;
        if (XuStrUtil.isNotBlank(phone) && 
            userMapper.checkPhoneUnique(phone, tenantId, dto.getUserId()) > 0) {
            throw new BusinessException("手机号'" + phone + "'已存在");
        }
        
        updateEntityFromDto(dto, existUser);
        updateById(existUser);
        
        if (dto.getRoleIds() != null) {
            DataOwnerUtil.checkRolePermission(dto.getRoleIds());
            deleteUserRole(existUser.getId());
            if (!dto.getRoleIds().isEmpty()) {
                insertUserRole(existUser.getId(), dto.getRoleIds());
            }
        }
        
        if (dto.getPostIds() != null) {
            deleteUserPost(existUser.getId());
            if (!dto.getPostIds().isEmpty()) {
                insertUserPost(existUser.getId(), dto.getPostIds());
            }
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        // 不能删除超级管理员
        if (id == 1L) {
            throw new BusinessException("不允许删除超级管理员");
        }
        
        // 不能删除自己
        if (id.equals(SecurityContextHolder.getUserId())) {
            throw new BusinessException("不能删除当前登录用户");
        }
        
        // 删除用户角色关联
        deleteUserRole(id);
        // 删除用户岗位关联
        deleteUserPost(id);
        // 逻辑删除用户
        removeById(id);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            deleteById(id);
            count++;
        }
        return count;
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

    // ==================== 手动转换方法（替代 MapStruct） ====================

    private UserDTO toDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setBase(user);
        return dto;
    }

    private User toEntity(UserDTO dto) {
        if (dto.getBase() != null) {
            User user = new User();
            User base = dto.getBase();
            user.setId(base.getId());
            user.setDeptId(base.getDeptId());
            user.setUsername(base.getUsername());
            user.setNickname(base.getNickname());
            user.setAvatar(base.getAvatar());
            user.setPhone(base.getPhone());
            user.setEmail(base.getEmail());
            user.setSex(base.getSex());
            user.setStatus(base.getStatus());
            return user;
        }
        return new User();
    }

    private void updateEntityFromDto(UserDTO dto, User entity) {
        if (dto.getBase() == null) return;
        User base = dto.getBase();
        if (base.getDeptId() != null) entity.setDeptId(base.getDeptId());
        if (base.getNickname() != null) entity.setNickname(base.getNickname());
        if (base.getPhone() != null) entity.setPhone(base.getPhone());
        if (base.getEmail() != null) entity.setEmail(base.getEmail());
        if (base.getSex() != null) entity.setSex(base.getSex());
        if (base.getStatus() != null) entity.setStatus(base.getStatus());
        if (base.getRemark() != null) entity.setRemark(base.getRemark());
    }

    // ==================== 关联表操作 ====================

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
