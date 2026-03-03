package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.han.common.core.domain.PageResult;
import com.han.api.tenant.TenantServiceClient;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.util.HanStrUtil;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.util.DataOwnerUtil;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.dto.ProfileDto;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.vo.UserImportVo;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUserPo> implements ISysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final TenantServiceClient tenantServiceClient;

    @Override
    public PageResult<UserVO> selectUserPage(SysUserQuery query) {
        final IPage<UserVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<UserVO> result;
        if (SecurityContextHolder.isAdmin()) {
            result = TenantHelper.ignore(() -> sysUserMapper.selectUserPage(page, query));
        } else {
            result = sysUserMapper.selectUserPage(page, query);
        }
        return new PageResult<>(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
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

        // 校验租户用户数配额
        if (tenantId != null && tenantId != 1L) {
            try {
                R<Boolean> limitResult = tenantServiceClient.checkUserLimit(tenantId);
                if (limitResult.getData() != null && !limitResult.getData()) {
                    throw new BusinessException("当前租户用户数已达上限，无法新增用户");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                // 租户服务不可用时不阻塞用户创建
                log.warn("校验租户用户配额失败，跳过校验: tenantId={}", tenantId, e);
            }
        }

        if (dto.getUsername() != null && sysUserMapper.checkUsernameUnique(dto.getUsername(), tenantId, null) > 0) {
            throw new BusinessException("用户名'" + dto.getUsername() + "'已存在");
        }

        if (HanStrUtil.isNotBlank(dto.getPhone()) &&
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

        if (HanStrUtil.isNotBlank(dto.getPhone()) &&
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
        return TenantHelper.ignore(() -> sysUserMapper.selectPermissionsByUserId(userId));
    }

    @Override
    public Set<String> selectRoleKeysByUserId(Long userId) {
        if (userId == 1L) {
            return Set.of("admin");
        }
        return TenantHelper.ignore(() -> sysUserMapper.selectRoleKeysByUserId(userId));
    }

    // ==================== 用户导入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importUsers(List<UserImportVo> list, boolean updateSupport) {
        int successCount = 0;
        int failCount = 0;
        StringBuilder failMsg = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            UserImportVo row = list.get(i);
            int rowNum = i + 2;
            try {
                if (HanStrUtil.isBlank(row.getUsername())) {
                    failCount++;
                    failMsg.append("第").append(rowNum).append("行: 用户名不能为空; ");
                    continue;
                }

                LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<SysUserPo>()
                        .eq(SysUserPo::getUsername, row.getUsername());
                SysUserPo existing = sysUserMapper.selectOne(wrapper.last("LIMIT 1"));

                if (existing != null) {
                    if (updateSupport) {
                        if (HanStrUtil.isNotBlank(row.getNickname())) existing.setNickname(row.getNickname());
                        if (HanStrUtil.isNotBlank(row.getPhone())) existing.setPhone(row.getPhone());
                        if (HanStrUtil.isNotBlank(row.getEmail())) existing.setEmail(row.getEmail());
                        if (HanStrUtil.isNotBlank(row.getSexText())) existing.setSex(parseSex(row.getSexText()));
                        sysUserMapper.updateById(existing);
                        successCount++;
                    } else {
                        failCount++;
                        failMsg.append("第").append(rowNum).append("行: 用户名[").append(row.getUsername()).append("]已存在; ");
                    }
                } else {
                    SysUserPo user = new SysUserPo();
                    user.setUsername(row.getUsername());
                    user.setNickname(HanStrUtil.isNotBlank(row.getNickname()) ? row.getNickname() : row.getUsername());
                    user.setPassword(PasswordUtil.encode(HanStrUtil.isNotBlank(row.getPassword()) ? row.getPassword() : "Han@2026"));
                    user.setPhone(row.getPhone());
                    user.setEmail(row.getEmail());
                    user.setSex(parseSex(row.getSexText()));
                    user.setStatus(0);
                    sysUserMapper.insert(user);
                    successCount++;
                }
            } catch (Exception e) {
                failCount++;
                failMsg.append("第").append(rowNum).append("行: ").append(e.getMessage()).append("; ");
            }
        }

        String result = "导入完成：成功" + successCount + "条，失败" + failCount + "条";
        if (failCount > 0) {
            result += "。失败详情: " + failMsg;
        }
        return result;
    }

    private Integer parseSex(String sexText) {
        if (sexText == null) return 0;
        return switch (sexText.trim()) {
            case "男" -> 1;
            case "女" -> 2;
            default -> 0;
        };
    }

    // ==================== 个人中心 ====================

    @Override
    public void updateProfile(Long userId, ProfileDto dto) {
        SysUserPo user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getSex() != null) {
            user.setSex(dto.getSex());
        }
        sysUserMapper.updateById(user);
    }

    @Override
    public void updatePassword(Long userId, String oldPwd, String newPwd) {
        SysUserPo user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(oldPwd, user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        if (HanStrUtil.isBlank(newPwd) || newPwd.length() < 6) {
            throw new BusinessException("新密码长度不能少于6位");
        }
        user.setPassword(PasswordUtil.encode(newPwd));
        sysUserMapper.updateById(user);
    }

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        SysUserPo user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setAvatar(avatarUrl);
        sysUserMapper.updateById(user);
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

    @Override
    public List<java.util.Map<String, Object>> selectSimpleUserList() {
        List<SysUserPo> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUserPo>()
                        .eq(SysUserPo::getStatus, 0)
                        .select(SysUserPo::getId, SysUserPo::getNickname, SysUserPo::getPhone, SysUserPo::getEmail)
                        .orderByAsc(SysUserPo::getNickname)
        );
        return users.stream().map(u -> {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("userId", u.getId());
            map.put("nickname", u.getNickname());
            map.put("phone", u.getPhone());
            map.put("email", u.getEmail());
            return map;
        }).toList();
    }
}
