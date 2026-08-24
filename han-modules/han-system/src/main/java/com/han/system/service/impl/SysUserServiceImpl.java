package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.han.common.core.domain.PageResult;
import com.han.api.tenant.TenantServiceClient;
import com.han.api.system.domain.OpenVendorAccountCreateDTO;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
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
import com.han.system.mapper.SysRoleMapper;
import com.han.system.domain.po.SysRolePo;
import com.han.system.service.ISysUserService;
import com.han.system.sdfz.education.EducationAccountIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final long OPEN_PLATFORM_TENANT_ID = 1L;
    private static final String OPEN_VENDOR_ROLE_KEY = "openVendor";

    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysRoleMapper sysRoleMapper;
    private final TenantServiceClient tenantServiceClient;
    private final EducationAccountIdentityService educationAccountIdentityService;

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

        // 校验租户用户数配额（租户初始化时跳过，避免循环RPC+未提交事务导致死锁）
        String currentUser = SecurityContextHolder.getUsername();
        boolean isTenantInit = "system-init".equals(currentUser);
        if (tenantId != null && tenantId != 1L && !isTenantInit) {
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

        // 唯一性冲突用独立错误码，便于前端区分"重名"和"系统故障"
        if (dto.getUsername() != null && sysUserMapper.checkUsernameUnique(dto.getUsername(), tenantId, null) > 0) {
            throw new ConflictException("用户名“" + dto.getUsername() + "”已存在，请更换后重试");
        }

        if (HanStrUtil.isNotBlank(dto.getPhone()) &&
                sysUserMapper.checkPhoneUnique(dto.getPhone(), tenantId, null) > 0) {
            throw new ConflictException("手机号“" + dto.getPhone() + "”已存在，请更换后重试");
        }

        PasswordUtil.validate(dto.getPassword());

        SysUserPo po = sysUserConverter.toPo(dto);
        po.setPassword(PasswordUtil.encrypt(dto.getPassword()));
        po.setPwdUpdateTime(java.time.LocalDateTime.now());
        po.setPwdResetFlag(0);
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
            throw new ConflictException("手机号“" + dto.getPhone() + "”已存在，请更换后重试");
        }

        sysUserConverter.updatePo(dto, existUser);
        updateById(existUser);
        educationAccountIdentityService.syncFromAccount(existUser);

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
        po.setPwdUpdateTime(java.time.LocalDateTime.now());
        po.setPwdResetFlag(1);
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
                        educationAccountIdentityService.syncFromAccount(existing);
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
    @Transactional(rollbackFor = Exception.class)
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
        educationAccountIdentityService.syncFromAccount(user);
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
        PasswordUtil.validate(newPwd);
        if (PasswordUtil.matches(newPwd, user.getPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }
        user.setPassword(PasswordUtil.encode(newPwd));
        user.setPwdUpdateTime(java.time.LocalDateTime.now());
        user.setPwdResetFlag(0);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOpenVendorAccount(OpenVendorAccountCreateDTO dto) {
        if (dto == null || !java.util.Objects.equals(dto.getTenantId(), OPEN_PLATFORM_TENANT_ID)) {
            throw new BusinessException("开放平台厂商账号只能创建在平台租户");
        }
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        String nickname = dto.getNickname() == null ? "" : dto.getNickname().trim();
        String phone = dto.getPhone() == null ? null : dto.getPhone().trim();
        if (username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (nickname.isBlank()) {
            nickname = username;
        }
        PasswordUtil.validate(dto.getPassword());

        final String finalUsername = username;
        long globalUsernameCount = TenantHelper.ignore(() -> sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUserPo>()
                        .eq(SysUserPo::getUsername, finalUsername)
                        .eq(SysUserPo::getDelFlag, 0)));
        if (globalUsernameCount > 0) {
            Long existingUserId = findIdempotentOpenVendorAccount(username, phone, dto.getEmail(), dto.getPassword());
            if (existingUserId != null) {
                return existingUserId;
            }
            throw new ConflictException("用户名“" + username + "”已存在，请更换后重试");
        }
        if (HanStrUtil.isNotBlank(phone)) {
            long tenantPhoneCount = TenantHelper.ignore(() -> sysUserMapper.selectCount(
                    new LambdaQueryWrapper<SysUserPo>()
                            .eq(SysUserPo::getTenantId, OPEN_PLATFORM_TENANT_ID)
                            .eq(SysUserPo::getPhone, phone)
                            .eq(SysUserPo::getDelFlag, 0)));
            if (tenantPhoneCount > 0) {
                throw new ConflictException("手机号“" + phone + "”已存在，请更换后重试");
            }
        }

        SysRolePo role = TenantHelper.ignore(() -> sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRolePo>()
                        .eq(SysRolePo::getTenantId, OPEN_PLATFORM_TENANT_ID)
                        .eq(SysRolePo::getRoleKey, OPEN_VENDOR_ROLE_KEY)
                        .eq(SysRolePo::getStatus, 0)
                        .eq(SysRolePo::getDelFlag, 0)
                        .last("LIMIT 1")));
        if (role == null) {
            throw new BusinessException("开放平台厂商角色未初始化");
        }

        SysUserPo user = new SysUserPo();
        user.setTenantId(OPEN_PLATFORM_TENANT_ID);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setPhone(phone);
        user.setEmail(dto.getEmail() == null ? null : dto.getEmail().trim());
        user.setStatus(1); // 审核通过前禁止登录
        user.setPwdUpdateTime(java.time.LocalDateTime.now());
        user.setPwdResetFlag(0);
        try {
            TenantHelper.ignore(() -> {
                sysUserMapper.insert(user);
                userRoleMapper.insert(new SysUserRolePo(user.getId(), role.getId()));
            });
        } catch (DataIntegrityViolationException e) {
            Long existingUserId = findIdempotentOpenVendorAccount(username, phone, dto.getEmail(), dto.getPassword());
            if (existingUserId != null) {
                return existingUserId;
            }
            throw new ConflictException("用户名或手机号已存在，请更换后重试");
        }
        return user.getId();
    }

    /** 仅把同一份公开申请重试识别为幂等；不同联系方式、密码、状态或角色都继续冲突。 */
    private Long findIdempotentOpenVendorAccount(String username, String phone, String email, String password) {
        List<SysUserPo> candidates = TenantHelper.ignore(() -> sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUserPo>()
                        .eq(SysUserPo::getUsername, username)
                        .eq(SysUserPo::getDelFlag, 0)
                        .last("LIMIT 5")));
        if (candidates == null) {
            return null;
        }
        String normalizedEmail = email == null ? "" : email.trim();
        for (SysUserPo candidate : candidates) {
            if (!java.util.Objects.equals(OPEN_PLATFORM_TENANT_ID, candidate.getTenantId())
                    || !Integer.valueOf(1).equals(candidate.getStatus())
                    || !java.util.Objects.equals(phone, candidate.getPhone())
                    || !java.util.Objects.equals(normalizedEmail,
                    candidate.getEmail() == null ? "" : candidate.getEmail().trim())
                    || !PasswordUtil.matches(password, candidate.getPassword())
                    || !isOnlyOpenVendorRole(candidate.getId())) {
                continue;
            }
            return candidate.getId();
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateOpenVendorAccount(Long userId) {
        SysUserPo user = requireOpenVendorAccount(userId);
        if (user.getStatus() != null && user.getStatus() == 0) {
            return;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("开放平台厂商账号状态不允许激活");
        }
        SysUserPo update = new SysUserPo();
        update.setId(userId);
        update.setStatus(0);
        TenantHelper.ignore(() -> sysUserMapper.updateById(update));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void compensateOpenVendorAccount(Long userId) {
        SysUserPo user = requireOpenVendorAccount(userId);
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("仅允许补偿删除禁用的开放平台厂商账号");
        }
        TenantHelper.ignore(() -> {
            SysUserPo rename = new SysUserPo();
            rename.setId(userId);
            rename.setUsername("vendor_compensated_" + userId);
            // MyBatis-Plus 默认忽略 null 更新；空串才能在逻辑删除前真正清除申请人的联系方式。
            rename.setPhone("");
            rename.setEmail("");
            if (sysUserMapper.updateById(rename) <= 0) {
                throw new BusinessException("开放平台厂商账号补偿失败");
            }
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRolePo>()
                    .eq(SysUserRolePo::getUserId, userId));
            sysUserMapper.deleteById(userId);
        });
    }

    private SysUserPo requireOpenVendorAccount(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUserPo user = TenantHelper.ignore(() -> sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserPo>()
                        .eq(SysUserPo::getId, userId)
                        .eq(SysUserPo::getTenantId, OPEN_PLATFORM_TENANT_ID)
                        .eq(SysUserPo::getDelFlag, 0)
                        .last("LIMIT 1")));
        if (user == null || !isOnlyOpenVendorRole(userId)) {
            throw new BusinessException("账号不是开放平台厂商账号");
        }
        return user;
    }

    private boolean isOnlyOpenVendorRole(Long userId) {
        Set<Long> roleIds = TenantHelper.ignore(() -> sysUserMapper.selectRoleIdsByUserId(userId));
        if (roleIds == null || roleIds.size() != 1) {
            return false;
        }
        SysRolePo role = TenantHelper.ignore(() -> sysRoleMapper.selectById(roleIds.iterator().next()));
        return role != null
                && OPEN_PLATFORM_TENANT_ID == (role.getTenantId() == null ? -1L : role.getTenantId())
                && OPEN_VENDOR_ROLE_KEY.equals(role.getRoleKey())
                && role.getDelFlag() != null && role.getDelFlag() == 0;
    }
}
