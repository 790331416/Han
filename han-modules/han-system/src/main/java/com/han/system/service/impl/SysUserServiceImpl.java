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
import com.han.common.security.domain.LoginUser;
import com.han.common.security.util.DataOwnerUtil;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.dto.ProfileDto;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.vo.SimpleUserVo;
import com.han.system.domain.vo.UserImportVo;
import com.han.system.domain.po.SysPostPo;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPostPo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.vo.UserVO;
import com.han.system.mapper.SysPostMapper;
import com.han.system.mapper.SysRoleMapper;
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

    /** 超级管理员用户ID */
    private static final long SUPER_ADMIN_USER_ID = 1L;

    /** 用户状态：停用 */
    private static final int STATUS_DISABLED = 1;

    /** 下拉接口单次最多返回的用户数，避免大租户下变成通讯录导出口 */
    private static final int SIMPLE_LIST_MAX_ROWS = 200;

    /** 允许在下拉接口里看到联系方式的权限点 */
    private static final List<String> CONTACT_VISIBLE_PERMISSIONS = List.of(
            "system:user:query", "system:user:list", "system:dept:add", "system:dept:edit");

    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
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

        assertSuperAdminOperable(dto.getUserId());
        if (isSuperAdmin(dto.getUserId()) && dto.getStatus() != null && dto.getStatus() == STATUS_DISABLED) {
            throw new BusinessException("不允许停用超级管理员");
        }

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
        if (isSuperAdmin(id)) {
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
        assertSuperAdminOperable(userId);
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
        assertSuperAdminOperable(userId);
        if (isSuperAdmin(userId) && status != null && status == STATUS_DISABLED) {
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

    // ==================== 超级管理员保护 ====================

    private boolean isSuperAdmin(Long userId) {
        return userId != null && userId == SUPER_ADMIN_USER_ID;
    }

    /**
     * 超级管理员只能由超级管理员本人操作。
     *
     * <p>与 {@code deleteById} 的「不允许删除超级管理员」、{@code updateUserStatus} 的
     * 「不允许停用超级管理员」同属一批规则，此前 {@code resetPwd} / {@code update} 两个入口漏掉了：
     * 只要持有 {@code system:user:resetPwd} 就能改掉 1 号超管密码并登录，等于拿下整个平台。
     */
    private void assertSuperAdminOperable(Long targetUserId) {
        if (isSuperAdmin(targetUserId) && !SecurityContextHolder.isAdmin()) {
            throw new BusinessException("不允许操作超级管理员");
        }
    }

    // ==================== 关联表操作 ====================

    private void insertUserRole(Long userId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        assertRolesInCurrentTenant(roleIds);
        for (Long roleId : roleIds) {
            userRoleMapper.insert(new SysUserRolePo(userId, roleId));
        }
    }

    /**
     * 校验角色都属于当前租户。
     *
     * <p>{@code sys_user_role} 被排除出租户过滤（表上没有 tenant_id 列），跨租户绑定的防线
     * 全在应用层，而此前拿到 ID 就直接 insert。这里按 ID 反查 {@code sys_role}——该表本身受
     * 租户插件过滤，查不到即说明不属于当前租户。
     */
    private void assertRolesInCurrentTenant(Set<Long> roleIds) {
        if (SecurityContextHolder.isAdmin()) {
            return;
        }
        long visible = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRolePo>().in(SysRolePo::getId, roleIds));
        if (visible != roleIds.size()) {
            throw new BusinessException("存在不属于当前租户的角色，无法分配");
        }
    }

    /**
     * 校验岗位都属于当前租户，理由同 {@link #assertRolesInCurrentTenant(Set)}。
     */
    private void assertPostsInCurrentTenant(Set<Long> postIds) {
        if (SecurityContextHolder.isAdmin()) {
            return;
        }
        long visible = postMapper.selectCount(
                new LambdaQueryWrapper<SysPostPo>().in(SysPostPo::getId, postIds));
        if (visible != postIds.size()) {
            throw new BusinessException("存在不属于当前租户的岗位，无法分配");
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
        assertPostsInCurrentTenant(postIds);
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
    public List<SimpleUserVo> selectSimpleUserList(String keyword) {
        boolean contactVisible = canViewContact();

        LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getStatus, 0);
        if (HanStrUtil.isNotBlank(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(SysUserPo::getNickname, trimmed).or().like(SysUserPo::getUsername, trimmed));
        }
        if (contactVisible) {
            wrapper.select(SysUserPo::getId, SysUserPo::getNickname, SysUserPo::getPhone, SysUserPo::getEmail);
        } else {
            wrapper.select(SysUserPo::getId, SysUserPo::getNickname);
        }
        wrapper.orderByAsc(SysUserPo::getNickname).last("LIMIT " + SIMPLE_LIST_MAX_ROWS);

        return sysUserMapper.selectList(wrapper).stream()
                .map(u -> SimpleUserVo.builder()
                        .userId(u.getId())
                        .nickname(u.getNickname())
                        .phone(contactVisible ? u.getPhone() : null)
                        .email(contactVisible ? u.getEmail() : null)
                        .build())
                .toList();
    }

    /**
     * 下拉接口是否可以下发联系方式。
     *
     * <p>部门维护页需要用负责人的真实手机号/邮箱回填部门联系方式，因此对具备
     * 用户查询或部门维护权限的调用方保留明文；其余已登录用户只能拿到昵称。
     */
    private boolean canViewContact() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        for (String permission : CONTACT_VISIBLE_PERMISSIONS) {
            if (user.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
}
