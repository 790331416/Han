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
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.po.SysUserPostPo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.vo.UserVO;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserPostMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.service.ISysUserService;
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
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUserPo> implements ISysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;

    @Override
    public PageResult<UserVO> selectUserPage(SysUserQuery query) {
        IPage<UserVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        page = sysUserMapper.selectUserPage(page, query);
        return new PageResult<>(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<SysUserDto> selectListScope(SysUserQuery query) {
        return selectList(query);
    }

    @Override
    public List<SysUserDto> selectList(SysUserQuery query) {
        return List.of();
    }

    @Override
    public SysUserDto selectById(Long id) {
        SysUserPo po = getById(id);
        if (po == null) {
            return null;
        }
        SysUserDto dto = sysUserConverter.toDto(po);
        dto.setRoleIds(sysUserMapper.selectRoleIdsByUserId(id));
        dto.setPostIds(sysUserMapper.selectPostIdsByUserId(id));
        return dto;
    }

    @Override
    public List<SysUserDto> selectByIds(List<Long> ids) {
        List<SysUserPo> users = listByIds(ids);
        return users.stream().map(sysUserConverter::toDto).collect(Collectors.toList());
    }

    @Override
    public SysUserDto selectUserByUsername(String username) {
        SysUserPo po = getOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getUsername, username)
                .last("LIMIT 1"));
        if (po == null) {
            return null;
        }
        return sysUserConverter.toDto(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(SysUserDto dto) {
        Long tenantId = SecurityContextHolder.getTenantId();

        if (dto.getUsername() != null && sysUserMapper.checkUsernameUnique(dto.getUsername(), tenantId, null) > 0) {
            throw new BusinessException("用户名'" + dto.getUsername() + "'已存在");
        }

        if (XuStrUtil.isNotBlank(dto.getPhone()) &&
                sysUserMapper.checkPhoneUnique(dto.getPhone(), tenantId, null) > 0) {
            throw new BusinessException("手机号'" + dto.getPhone() + "'已存在");
        }

        PasswordUtil.validate(dto.getPassword());

        SysUserPo po = sysUserConverter.toPo(dto);
        po.setPassword(PasswordUtil.encrypt(dto.getPassword()));
        if (po.getStatus() == null) {
            po.setStatus(0);
        }

        save(po);

        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            DataOwnerUtil.checkRolePermission(dto.getRoleIds());
            insertUserRole(po.getId(), dto.getRoleIds());
        }

        if (dto.getPostIds() != null && !dto.getPostIds().isEmpty()) {
            insertUserPost(po.getId(), dto.getPostIds());
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(SysUserDto dto) {
        Long tenantId = SecurityContextHolder.getTenantId();

        SysUserPo existUser = getById(dto.getUserId());
        if (existUser == null) {
            throw new BusinessException("用户不存在");
        }

        if (XuStrUtil.isNotBlank(dto.getPhone()) &&
                sysUserMapper.checkPhoneUnique(dto.getPhone(), tenantId, dto.getUserId()) > 0) {
            throw new BusinessException("手机号'" + dto.getPhone() + "'已存在");
        }

        sysUserConverter.updatePo(dto, existUser);
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
        if (id == 1L) {
            throw new BusinessException("不允许删除超级管理员");
        }
        if (id.equals(SecurityContextHolder.getUserId())) {
            throw new BusinessException("不能删除当前登录用户");
        }
        deleteUserRole(id);
        deleteUserPost(id);
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
        PasswordUtil.validate(password);
        SysUserPo po = new SysUserPo();
        po.setId(userId);
        po.setPassword(PasswordUtil.encrypt(password));
        updateById(po);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        if (userId == 1L && status == 1) {
            throw new BusinessException("不允许停用超级管理员");
        }
        SysUserPo po = new SysUserPo();
        po.setId(userId);
        po.setStatus(status);
        updateById(po);
    }

    @Override
    public Set<String> selectPermissionsByUserId(Long userId) {
        if (userId == 1L) {
            return Set.of("*:*:*");
        }
        return sysUserMapper.selectPermissionsByUserId(userId);
    }

    @Override
    public Set<String> selectRoleKeysByUserId(Long userId) {
        if (userId == 1L) {
            return Set.of("admin");
        }
        return sysUserMapper.selectRoleKeysByUserId(userId);
    }

    // ==================== 关联表操作 ====================

    private void insertUserRole(Long userId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            userRoleMapper.insert(new SysUserRolePo(userId, roleId));
        }
    }

    private void deleteUserRole(Long userId) {
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRolePo>().eq(SysUserRolePo::getUserId, userId)
        );
    }

    private void insertUserPost(Long userId, Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (Long postId : postIds) {
            userPostMapper.insert(new SysUserPostPo(userId, postId));
        }
    }

    private void deleteUserPost(Long userId) {
        userPostMapper.delete(
                new LambdaQueryWrapper<SysUserPostPo>().eq(SysUserPostPo::getUserId, userId)
        );
    }
}
