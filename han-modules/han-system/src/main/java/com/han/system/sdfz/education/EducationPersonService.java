package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.security.util.DataOwnerUtil;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduPersonSubjectPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static com.han.system.sdfz.education.EducationSupport.LOCAL_SOURCE;
import static com.han.system.sdfz.education.EducationSupport.normalStatus;
import static com.han.system.sdfz.education.EducationSupport.notBlank;
import static com.han.system.sdfz.education.EducationSupport.page;
import static com.han.system.sdfz.education.EducationSupport.requireLocalSource;
import static com.han.system.sdfz.education.EducationSupport.requireTenant;
import static com.han.system.sdfz.education.EducationSupport.trimToNull;

/**
 * 教育人员统一入口：一次提交在同一事务内写入人员档案、可选登录账号、角色、归班与任教关系。
 *
 * <p>调用方不能指定 Han 用户 ID。启用登录时由本服务建号并回填 {@code edu_person.user_id}，
 * 任一步失败整笔回滚，不产生"只有账号"或"只有人员"的半成品。</p>
 */
@Service
@RequiredArgsConstructor
public class EducationPersonService {

    private static final String ADMIN_ROLE_KEY = "admin";
    private static final String STUDENT_TYPE = "STUDENT";
    private static final String TEACHER_TYPE = "TEACHER";
    private static final int GENERATED_PASSWORD_LENGTH = 12;
    private static final int NICKNAME_MAX_LENGTH = 50;

    /** 登录名：字母开头，4~30 位字母、数字、下划线、点或连字符。 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{3,29}$");

    private final EduPersonMapper personMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduPersonSubjectMapper personSubjectMapper;
    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduSubjectMapper subjectMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    public PageResult<EduPersonPo> list(Long schoolId, String personType, String keyword,
                                        Integer status, int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduPersonPo> query = new LambdaQueryWrapper<EduPersonPo>()
                .eq(schoolId != null, EduPersonPo::getSchoolId, schoolId)
                .eq(notBlank(personType), EduPersonPo::getPersonType, personType)
                .eq(status != null, EduPersonPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduPersonPo::getPersonNo, keyword)
                        .or().like(EduPersonPo::getPersonName, keyword))
                .orderByAsc(EduPersonPo::getPersonName);
        return page(personMapper, query, pageNum, pageSize);
    }

    public List<EduPersonClassPo> listMemberships(Long personId) {
        requireTenant();
        requirePerson(personId);
        return personClassMapper.selectList(new LambdaQueryWrapper<EduPersonClassPo>()
                .eq(EduPersonClassPo::getPersonId, personId));
    }

    public List<EduPersonSubjectPo> listAssignments(Long personId) {
        requireTenant();
        requirePerson(personId);
        return personSubjectMapper.selectList(new LambdaQueryWrapper<EduPersonSubjectPo>()
                .eq(EduPersonSubjectPo::getPersonId, personId));
    }

    /**
     * 读回人员登录账号已有的角色 ID。
     *
     * <p>与 memberships / subjects 对齐，供编辑页回填；没有账号时返回空列表。</p>
     */
    public List<Long> listRoleIds(Long personId) {
        requireTenant();
        EduPersonPo person = requirePerson(personId);
        if (person.getUserId() == null) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePo>()
                        .eq(SysUserRolePo::getUserId, person.getUserId()))
                .stream().map(SysUserRolePo::getRoleId).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public EducationForms.PersonResult save(EducationForms.Person form) {
        Long tenantId = requireTenant();
        requireSchool(form.schoolId());

        String personType = form.personType().trim().toUpperCase(Locale.ROOT);
        String personNo = form.personNo().trim();
        EduPersonPo person = form.id() == null ? new EduPersonPo() : requirePerson(form.id());
        if (form.id() != null) {
            requireLocalSource(person.getSourceSystem(), "人员");
        }
        requirePersonNoAvailable(form.schoolId(), personNo, form.id());

        Long userId = person.getUserId();
        String username = null;
        String initialPassword = null;
        if (form.wantsLogin()) {
            if (userId == null) {
                Account account = createAccount(tenantId, form, personType);
                userId = account.userId();
                username = account.username();
                initialPassword = account.initialPassword();
            } else {
                username = refreshAccount(userId, form);
            }
        } else if (userId != null) {
            throw new BusinessException("该人员已关联登录账号，请先在账号与权限中处理");
        }

        person.setUserId(userId);
        person.setSchoolId(form.schoolId());
        person.setPersonNo(personNo);
        person.setPersonName(form.personName().trim());
        person.setPersonType(personType);
        person.setPhone(trimToNull(form.phone()));
        person.setStatus(normalStatus(form.status()));
        person.setRemark(trimToNull(form.remark()));
        applyLeaveState(person, form.leaveFlag());
        if (form.id() == null) {
            person.setTenantId(tenantId);
            person.setSourceSystem(LOCAL_SOURCE);
            personMapper.insert(person);
        } else {
            personMapper.updateById(person);
        }

        if (form.classIds() != null) {
            applyMemberships(person, form.classIds(), form.membershipRole());
        }
        if (form.subjectIds() != null) {
            applyAssignments(person, form.subjectIds(), null);
        }
        return new EducationForms.PersonResult(person.getId(), userId, username, initialPassword);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deletePeople(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            EduPersonPo person = personMapper.selectById(id);
            if (person == null) {
                continue;
            }
            requireLocalSource(person.getSourceSystem(), "人员");

            personClassMapper.delete(new LambdaQueryWrapper<EduPersonClassPo>()
                    .eq(EduPersonClassPo::getPersonId, id));
            personSubjectMapper.delete(new LambdaQueryWrapper<EduPersonSubjectPo>()
                    .eq(EduPersonSubjectPo::getPersonId, id));
            disableAccount(person.getUserId());

            removed += personMapper.deleteById(id);
        }
        return removed;
    }

    @Transactional(rollbackFor = Exception.class)
    public int replaceMemberships(EducationForms.Membership form) {
        requireTenant();
        EduPersonPo person = requirePerson(form.personId());
        requireLocalSource(person.getSourceSystem(), "人员");
        return applyMemberships(person, form.classIds(), form.membershipRole());
    }

    @Transactional(rollbackFor = Exception.class)
    public int replaceAssignments(EducationForms.TeachingAssignment form) {
        requireTenant();
        EduPersonPo person = requirePerson(form.personId());
        requireLocalSource(person.getSourceSystem(), "人员");
        return applyAssignments(person, form.subjectIds(), form.classId());
    }

    /**
     * 维护离校状态与离校时间。
     *
     * <p>只在 0/1 之间切换时改写 {@code leaveTime}：置为离校时打上时间戳，恢复在校时清空，
     * 重复提交同一状态不刷新时间，避免离校时间被后续无关编辑覆盖。</p>
     */
    private static void applyLeaveState(EduPersonPo person, Integer requested) {
        int target = requested == null ? 0 : normalStatus(requested);
        int current = person.getLeaveFlag() == null ? 0 : person.getLeaveFlag();
        if (target == current) {
            person.setLeaveFlag(current);
            return;
        }
        person.setLeaveFlag(target);
        person.setLeaveTime(target == 1 ? LocalDateTime.now() : null);
    }

    // ---------------------------------------------------------------- 账号

    private Account createAccount(Long tenantId, EducationForms.Person form, String personType) {
        String username = trimToNull(form.username());
        if (username == null) {
            throw new BusinessException("启用登录时必须填写登录名");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException("登录名必须以字母开头，长度 4~30 位，只能包含字母、数字、下划线、点或连字符");
        }
        if (userMapper.checkUsernameUnique(username, tenantId, null) > 0) {
            throw new ConflictException("登录名“" + username + "”已存在，请更换后重试");
        }
        String phone = trimToNull(form.phone());
        if (phone != null && userMapper.checkPhoneUnique(phone, tenantId, null) > 0) {
            throw new ConflictException("手机号“" + phone + "”已被其他账号使用");
        }
        Set<Long> roleIds = resolveRoles(form.roleIds(), personType);

        String rawPassword = trimToNull(form.password());
        boolean generated = rawPassword == null;
        if (generated) {
            rawPassword = PasswordUtil.generatePassword(GENERATED_PASSWORD_LENGTH);
        } else {
            PasswordUtil.validate(rawPassword);
        }

        SysUserPo user = new SysUserPo();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setNickname(truncate(form.personName().trim(), NICKNAME_MAX_LENGTH));
        user.setPhone(phone);
        user.setPassword(PasswordUtil.encode(rawPassword));
        user.setStatus(normalStatus(form.status()));
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setPwdResetFlag(generated ? 1 : 0);
        user.setRemark("教育人员统一入口建号");
        userMapper.insert(user);

        for (Long roleId : roleIds) {
            userRoleMapper.insert(new SysUserRolePo(user.getId(), roleId));
        }
        return new Account(user.getId(), username, generated ? rawPassword : null);
    }

    /**
     * 已有账号只同步显示名、状态和角色，不改口令。
     *
     * <p>只有在调用方明确要改角色时才动 {@code sys_user_role}：空数组按"未提供"处理，
     * 否则前端漏回填一次就会静默清空该账号的全部角色。</p>
     */
    private String refreshAccount(Long userId, EducationForms.Person form) {
        SysUserPo user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("人员关联的登录账号不存在，请先修复账号数据");
        }
        user.setNickname(truncate(form.personName().trim(), NICKNAME_MAX_LENGTH));
        // 人员状态到账号状态**只同步停用这一个方向**：人员被停用，账号必须跟着停，
        // 不允许出现「人员已停用但账号还能登录」的中间态。
        //
        // 反向不能同步。账号是在「账号与权限」里被显式停用的（安全原因、离职流程、
        // 管理员手工处置），一次无关的人员资料编辑——改个电话、打个离校标记——
        // 不能把它悄悄放开。恢复登录能力必须走账号那条有审批的路径。
        if (normalStatus(form.status()) == 1) {
            user.setStatus(1);
        }
        userMapper.updateById(user);

        if (form.wantsRoleChange()) {
            Set<Long> roleIds = resolveRoles(form.effectiveRoleIds(),
                    form.personType().trim().toUpperCase(Locale.ROOT));
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRolePo>()
                    .eq(SysUserRolePo::getUserId, userId));
            for (Long roleId : roleIds) {
                userRoleMapper.insert(new SysUserRolePo(userId, roleId));
            }
        }
        return user.getUsername();
    }

    private void disableAccount(Long userId) {
        if (userId == null) {
            return;
        }
        SysUserPo user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        if (Objects.equals(user.getId(), 1L)) {
            throw new BusinessException("不允许通过教育人员入口停用超级管理员");
        }
        if (Objects.equals(user.getStatus(), 1)) {
            return;
        }
        user.setStatus(1);
        userMapper.updateById(user);
    }

    private Set<Long> resolveRoles(List<Long> requested, String personType) {
        Set<Long> roleIds = new LinkedHashSet<>();
        if (requested != null) {
            requested.stream().filter(Objects::nonNull).forEach(roleIds::add);
        }
        if (roleIds.isEmpty()) {
            return roleIds;
        }
        DataOwnerUtil.checkRolePermission(roleIds);
        for (Long roleId : roleIds) {
            SysRolePo role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException("角色不存在或不在当前数据范围: " + roleId);
            }
            if (ADMIN_ROLE_KEY.equals(role.getRoleKey())) {
                throw new BusinessException("不能通过教育人员入口分配超级管理员角色");
            }
            if (!Objects.equals(role.getStatus(), 0)) {
                throw new BusinessException("角色已停用: " + role.getRoleName());
            }
            if (STUDENT_TYPE.equals(personType) && role.getRoleKey() != null
                    && role.getRoleKey().toLowerCase(Locale.ROOT).contains("admin")) {
                throw new BusinessException("学生账号不能分配管理类角色");
            }
        }
        return roleIds;
    }

    // ---------------------------------------------------------------- 关系

    /** 按目标集合收敛归班关系：不在集合内的旧关系失效，缺失的补建。 */
    private int applyMemberships(EduPersonPo person, List<Long> classIds, String membershipRole) {
        List<Long> targets = distinctOrEmpty(classIds);
        String role = trimToNull(membershipRole);
        if (role == null) {
            role = STUDENT_TYPE.equals(person.getPersonType()) ? STUDENT_TYPE : TEACHER_TYPE;
        }
        if (STUDENT_TYPE.equals(person.getPersonType()) && targets.size() > 1) {
            throw new BusinessException("学生只能归属一个有效行政班");
        }
        for (Long classId : targets) {
            EduClassPo item = classMapper.selectById(classId);
            if (item == null) {
                throw new BusinessException("班级不存在或不在当前数据范围: " + classId);
            }
            if (!Objects.equals(item.getSchoolId(), person.getSchoolId())) {
                throw new BusinessException("班级“" + item.getClassName() + "”不属于人员所在学校");
            }
        }

        List<EduPersonClassPo> existing = personClassMapper.selectList(
                new LambdaQueryWrapper<EduPersonClassPo>().eq(EduPersonClassPo::getPersonId, person.getId()));
        Set<Long> keep = new LinkedHashSet<>();
        for (EduPersonClassPo item : existing) {
            if (targets.contains(item.getClassId()) && role.equals(item.getMembershipRole())) {
                keep.add(item.getClassId());
            } else {
                personClassMapper.deleteById(item.getId());
            }
        }
        int created = 0;
        for (Long classId : targets) {
            if (keep.contains(classId)) {
                continue;
            }
            EduPersonClassPo item = new EduPersonClassPo();
            item.setTenantId(person.getTenantId());
            item.setPersonId(person.getId());
            item.setClassId(classId);
            item.setMembershipRole(role);
            item.setSourceSystem(LOCAL_SOURCE);
            personClassMapper.insert(item);
            created++;
        }
        return created;
    }

    /** 按目标集合收敛任教关系。学生不允许配置任教科目。 */
    private int applyAssignments(EduPersonPo person, List<Long> subjectIds, Long classId) {
        List<Long> targets = distinctOrEmpty(subjectIds);
        if (STUDENT_TYPE.equals(person.getPersonType()) && !targets.isEmpty()) {
            throw new BusinessException("学生不能配置任教科目");
        }
        for (Long subjectId : targets) {
            EduSubjectPo subject = subjectMapper.selectById(subjectId);
            if (subject == null) {
                throw new BusinessException("科目不存在或不在当前数据范围: " + subjectId);
            }
        }
        if (classId != null) {
            EduClassPo item = classMapper.selectById(classId);
            if (item == null || !Objects.equals(item.getSchoolId(), person.getSchoolId())) {
                throw new BusinessException("任教班级不存在或不属于人员所在学校");
            }
        }

        List<EduPersonSubjectPo> existing = personSubjectMapper.selectList(
                new LambdaQueryWrapper<EduPersonSubjectPo>().eq(EduPersonSubjectPo::getPersonId, person.getId()));
        Set<Long> keep = new LinkedHashSet<>();
        for (EduPersonSubjectPo item : existing) {
            if (targets.contains(item.getSubjectId()) && Objects.equals(item.getClassId(), classId)) {
                keep.add(item.getSubjectId());
            } else {
                personSubjectMapper.deleteById(item.getId());
            }
        }
        int created = 0;
        for (Long subjectId : targets) {
            if (keep.contains(subjectId)) {
                continue;
            }
            EduPersonSubjectPo item = new EduPersonSubjectPo();
            item.setTenantId(person.getTenantId());
            item.setPersonId(person.getId());
            item.setSubjectId(subjectId);
            item.setClassId(classId);
            personSubjectMapper.insert(item);
            created++;
        }
        return created;
    }

    // ---------------------------------------------------------------- 内部

    private EduSchoolPo requireSchool(Long id) {
        EduSchoolPo value = id != null ? schoolMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        return value;
    }

    private EduPersonPo requirePerson(Long id) {
        EduPersonPo value = id != null ? personMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("人员不存在或不在当前数据范围");
        }
        return value;
    }

    private void requirePersonNoAvailable(Long schoolId, String personNo, Long selfId) {
        Long existing = personMapper.selectCount(new LambdaQueryWrapper<EduPersonPo>()
                .eq(EduPersonPo::getSchoolId, schoolId)
                .eq(EduPersonPo::getPersonNo, personNo)
                .ne(selfId != null, EduPersonPo::getId, selfId));
        if (existing != null && existing > 0) {
            throw new ConflictException("学校内人员编号“" + personNo + "”已存在，请更换后重试");
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static List<Long> distinct(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<Long> distinctOrEmpty(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private record Account(Long userId, String username, String initialPassword) {
    }
}
