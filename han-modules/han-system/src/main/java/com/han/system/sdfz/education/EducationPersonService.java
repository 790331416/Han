package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.api.system.AuthServiceClient;
import com.han.api.system.domain.SessionRevokeRequest;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.util.DataOwnerUtil;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDuty;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
@Slf4j
@RequiredArgsConstructor
public class EducationPersonService {

    private static final String ADMIN_ROLE_KEY = "admin";
    private static final String SCHOOL_DUTY_DICT = "edu_school_duty";
    private static final String STUDENT_TYPE = "STUDENT";
    private static final String TEACHER_TYPE = "TEACHER";
    private static final int GENERATED_PASSWORD_LENGTH = 12;
    private static final int NICKNAME_MAX_LENGTH = 50;

    /** 登录名：字母开头，4~30 位字母、数字、下划线、点或连字符。 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{3,29}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final EduPersonMapper personMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduPersonSubjectMapper personSubjectMapper;
    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduSubjectMapper subjectMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysDictDataMapper dictDataMapper;
    private final EducationDataScopeService dataScopeService;
    private final EducationAccountIdentityService accountIdentityService;

    /**
     * han-auth 会话撤销客户端（构造器注入）。
     *
     * <p>撤销失败必须阻断身份变更：任一会话撤销异常都会抛出业务异常，依赖外层事务
     * 回滚已写入的人员/账号变更，不允许出现「身份已变更但 token 未撤销」的半成品。</p>
     */
    private final AuthServiceClient authServiceClient;

    public PageResult<EduPersonPo> list(Long schoolId, String personType, String keyword,
                                        Integer status, int pageNum, int pageSize) {
        requireTenant();
        EducationDataScopeService.Scope scope = dataScopeService.current();
        if (schoolId != null) {
            dataScopeService.requireSchool(schoolId);
        } else if (!scope.all() && scope.schoolIds().isEmpty()) {
            int safePage = Math.max(pageNum, 1);
            int safeSize = Math.min(Math.max(pageSize, 1), 100);
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        LambdaQueryWrapper<EduPersonPo> query = new LambdaQueryWrapper<EduPersonPo>()
                .eq(schoolId != null, EduPersonPo::getSchoolId, schoolId)
                .in(schoolId == null && !scope.all(), EduPersonPo::getSchoolId, scope.schoolIds())
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
        if (STUDENT_TYPE.equalsIgnoreCase(person.getPersonType())) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePo>()
                        .eq(SysUserRolePo::getUserId, person.getUserId()))
                .stream().map(SysUserRolePo::getRoleId).toList();
    }

    /**
     * 重置已绑定账号的登录密码。
     *
     * <p>任务书 24：去掉「仅限教育入口建号」的限制，独立系统账号只要满足
     * 「人员存在 + user_id 已绑定 + 操作人有权限（控制器校验）+ 目标账号属当前租户
     * + 非超管 + 数据范围（{@link #requirePerson}）」，同样允许重置。</p>
     */
    /**
     * 关联账号精确匹配：按当前租户 + 精确手机号查询，最多返回一条脱敏信息。
     *
     * <p>只脱敏返回，不暴露完整邮箱/手机号；不允许遍历全租户账号。保存时由
     * {@link #requireLinkableAccount} 重新按 {@code linkUserId} 复核。</p>
     */
    public EducationForms.LinkableAccount linkableAccount(String phone) {
        Long tenantId = requireTenant();
        String value = trimToNull(phone);
        if (value == null || !PHONE_PATTERN.matcher(value).matches()) {
            throw new BusinessException("手机号格式不正确");
        }
        SysUserPo user = userMapper.selectOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, tenantId)
                .eq(SysUserPo::getPhone, value)
                .eq(SysUserPo::getStatus, 0)
                .last("LIMIT 1"));
        if (user == null) {
            return null;
        }
        return new EducationForms.LinkableAccount(user.getId(), user.getNickname(),
                maskPhone(user.getPhone()), maskEmail(user.getEmail()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetAccountPassword(Long personId, String password) {
        Long tenantId = requireTenant();
        EduPersonPo person = requirePerson(personId);
        requireLocalSource(person.getSourceSystem(), "人员");
        if (person.getUserId() == null) {
            throw new BusinessException("人员未绑定登录账号，请先重新绑定并设置密码");
        }
        SysUserPo user = userMapper.selectById(person.getUserId());
        if (user == null) {
            throw new BusinessException("人员关联的登录账号不存在，请先修复账号数据");
        }
        if (Objects.equals(user.getId(), 1L)) {
            throw new BusinessException("不允许重置超级管理员密码");
        }
        if (!Objects.equals(user.getTenantId(), tenantId)) {
            throw new BusinessException("目标账号不属于当前租户");
        }
        if (Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException("目标账号已停用，请先启用账号");
        }
        PasswordUtil.validate(password);
        user.setPassword(PasswordUtil.encrypt(password));
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setPwdResetFlag(1);
        userMapper.updateById(user);
    }

    /**
     * 旧单参数入口兼容：账号只绑定一条身份时行为与原来一致；绑定多条身份时按身份粒度，
     * 仅凭 userId 无法确定要解绑哪条身份，要求调用方改用 {@link #unbindClientUser(Long, Long)}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbindClientUser(Long userId) {
        requireTenant();
        if (userId == null) {
            throw new BusinessException("请选择客户端用户");
        }
        List<EduPersonPo> people = personMapper.selectList(new LambdaQueryWrapper<EduPersonPo>()
                .eq(EduPersonPo::getUserId, userId));
        if (people.isEmpty()) {
            throw new BusinessException("客户端用户未绑定教育人员");
        }
        if (people.size() > 1) {
            throw new BusinessException("该账号关联多个教育身份，请指定要解绑的人员");
        }
        unbindIdentity(userId, people.get(0));
    }

    /**
     * 按身份粒度解绑：只把当前 {@code edu_person.user_id} 置空，不删人员、不删账号、
     * 不清其他身份的角色或班级。仅当这是账号的最后一个有效绑定身份且账号为教育人员入口
     * 自动创建的客户端账号时才停用账号；独立系统账号解绑最后一个身份不停用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbindClientUser(Long userId, Long personId) {
        requireTenant();
        if (userId == null || personId == null) {
            throw new BusinessException("请选择要解绑的教育人员");
        }
        EduPersonPo person = personMapper.selectById(personId);
        if (person == null) {
            throw new BusinessException("人员不存在或不在当前数据范围");
        }
        if (!Objects.equals(person.getUserId(), userId)) {
            throw new BusinessException("人员未绑定该登录账号");
        }
        unbindIdentity(userId, person);
    }

    private void unbindIdentity(Long userId, EduPersonPo person) {
        dataScopeService.requireSchool(person.getSchoolId());
        requireLocalSource(person.getSourceSystem(), "人员");
        unbindIdentityState(userId, person);
        personMapper.updateById(person);
    }

    /**
     * 解绑当前身份的内部动作：置空 {@code edu_person.user_id} 并撤销该身份会话，
     * 随后统一走「最后有效教育身份」判断决定是否停用账号、清角色。
     * 独立系统账号解绑最后一个身份不停用。不负责落库，由调用方统一 {@code updateById}。
     */
    private void unbindIdentityState(Long userId, EduPersonPo person) {
        person.setUserId(null);
        revokeIdentitySession(userId, person.getId());
        disableAccountIfLastEducationAccount(userId, person.getId());
    }

    EduSchoolPo requireImportSchool(Long schoolId) {
        return requireSchool(schoolId);
    }

    @Transactional(rollbackFor = Exception.class)
    public EducationForms.PersonResult save(EducationForms.Person form) {
        Long tenantId = requireTenant();
        requireSchool(form.schoolId());

        String personType = form.personType().trim().toUpperCase(Locale.ROOT);
        // 纯入参校验放在建号之前：岗位写错属于请求不合法，不该先把账号建出来再靠事务回滚。
        String duty = resolveDuty(form.dutyCode(), personType);
        if (STUDENT_TYPE.equals(personType) && form.roleIds() != null && !form.roleIds().isEmpty()) {
            throw new BusinessException("学生账号不能分配管理端角色");
        }
        boolean editing = form.id() != null;
        EduPersonPo person = editing ? requirePerson(form.id()) : new EduPersonPo();
        if (editing) {
            requireLocalSource(person.getSourceSystem(), "人员");
        }
        String personNo = editing
                ? person.getPersonNo()
                : EducationCodeGenerator.unique("PERSON", form.personName(), candidate -> personMapper.selectCount(
                        new LambdaQueryWrapper<EduPersonPo>()
                                .eq(EduPersonPo::getSchoolId, form.schoolId())
                                .eq(EduPersonPo::getPersonNo, candidate)) > 0);
        requirePersonNoAvailable(form.schoolId(), personNo, form.id());
        String phone = requiredPhone(form.phone());

        Long oldUserId = person.getUserId();
        String oldDuty = person.getDutyCode();
        Long oldSchoolId = person.getSchoolId();
        Integer oldLeaveFlag = person.getLeaveFlag();
        Integer oldStatus = person.getStatus();

        EducationForms.AccountMode mode = parseAccountMode(form.accountMode());

        Long userId = oldUserId;
        String username = null;
        String initialPassword = null;
        SysUserPo linkedAccount = null;
        boolean rolesCleared = false;

        if (mode != null) {
            switch (mode) {
                case KEEP -> {
                    // 编辑已绑定人员默认保留绑定：同步账号显示资料；显式要求改角色时才动角色，
                    // 角色集合真正变化时账号级撤销会话，不动口令/状态。
                    if (userId != null) {
                        KeepResult kept = keepAccount(userId, form, phone, personType, duty, form.id());
                        username = kept.username();
                        if (kept.rolesChanged()) {
                            revokeAccountSession(userId);
                        }
                    }
                }
                case CREATE -> {
                    // 建新号：手机号/用户名已存在则冲突，绝不静默改成关联已有账号。
                    if (userId != null) {
                        throw new BusinessException("人员已绑定登录账号，不能新建账号");
                    }
                    Account account = createNewAccount(tenantId, form, personType, phone);
                    userId = account.userId();
                    username = account.username();
                    initialPassword = account.initialPassword();
                }
                case LINK -> {
                    if (form.linkUserId() == null) {
                        throw new BusinessException("关联已有账号必须传 linkUserId");
                    }
                    SysUserPo target = requireLinkableAccount(tenantId, form.linkUserId(), phone);
                    if (userId != null && !Objects.equals(userId, target.getId())) {
                        // 切绑到新账号：先撤销旧账号的该身份会话；若旧账号因此不再有其他
                        // 有效教育身份且是教育入口账号，则停旧账号、清旧角色并撤其全部会话；
                        // 独立系统账号不停用。随后绑定新账号，不改新账号口令/状态/角色。
                        revokeIdentitySession(userId, person.getId());
                        disableAccountIfLastEducationAccount(userId, person.getId());
                    }
                    requireNoDuplicateIdentity(tenantId, form.schoolId(), target.getId());
                    linkedAccount = target;
                    userId = target.getId();
                    username = target.getUsername();
                }
                case DISABLED -> {
                    // 新增不建号；编辑已绑定按当前身份解绑。
                    if (userId != null) {
                        unbindIdentityState(userId, person);
                        userId = null;
                    }
                }
            }
        } else {
            // 旧语义：loginEnabled 为空按「新增默认建号、编辑保留绑定」兜底。
            boolean createLoginAccount = form.loginEnabled() == null ? !editing : form.wantsLogin();
            if (createLoginAccount) {
                if (userId == null) {
                    Account account = createAccount(tenantId, form, personType, phone);
                    userId = account.userId();
                    username = account.username();
                    initialPassword = account.initialPassword();
                    linkedAccount = account.linkedUser();
                } else {
                    AccountRefresh refreshed = refreshAccount(userId, form, phone, duty, form.id());
                    username = refreshed.username();
                    rolesCleared = refreshed.rolesCleared();
                }
            } else if (userId != null) {
                // 关闭登录：只解绑当前身份，不停整个账号（任务书 17 节）。
                unbindIdentityState(userId, person);
                userId = null;
            }
        }

        person.setUserId(userId);
        person.setSchoolId(form.schoolId());
        person.setPersonNo(personNo);
        person.setPersonName(form.personName().trim());
        person.setPersonType(personType);
        person.setDutyCode(duty);
        person.setPhone(phone);
        person.setStatus(normalStatus(form.status()));
        person.setRemark(trimToNull(form.remark()));
        applyLeaveState(person, form.leaveFlag());

        if (editing) {
            personMapper.updateById(person);
        } else {
            person.setTenantId(tenantId);
            person.setSourceSystem(LOCAL_SOURCE);
            personMapper.insert(person);
        }

        // 身份级变更立即撤销旧会话：停用/离校/岗位降级/学校变更/管理角色清空（任务书 13-15）。
        revokeOnIdentityEdit(oldUserId, userId, form.id(), oldDuty, duty, oldSchoolId, form.schoolId(),
                oldLeaveFlag, person.getLeaveFlag(), oldStatus, person.getStatus(), rolesCleared);

        // 新增第二身份（关联已有账号）：只新增绑定，不动账号口令/状态/角色；
        // 姓名/手机号统一账号级，把账号现有姓名/手机号同步到全部身份，避免出现同一账号身份不同名。
        if (linkedAccount != null) {
            accountIdentityService.syncFromAccount(linkedAccount);
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
            dataScopeService.requireSchool(person.getSchoolId());
            requireLocalSource(person.getSourceSystem(), "人员");

            Long userId = person.getUserId();
            // 任务书 18：先撤销被删除身份会话，再按剩余有效绑定决定是否停用账号；
            // 撤销失败抛出业务异常，依赖事务回滚本次删除。
            revokeIdentitySession(userId, id);
            disableAccountIfLastEducationAccount(userId, id);

            personClassMapper.delete(new LambdaQueryWrapper<EduPersonClassPo>()
                    .eq(EduPersonClassPo::getPersonId, id));
            personSubjectMapper.delete(new LambdaQueryWrapper<EduPersonSubjectPo>()
                    .eq(EduPersonSubjectPo::getPersonId, id));
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
     * 解析校内岗位。
     *
     * <p>缺省回落成普通教师，<b>不回落成管理岗</b>：调用方漏传一次就把校级管理权发出去，
     * 与"管理员由管理员显式授予"直接冲突。取值写错则拒绝，不静默降级——
     * 静默降级会让管理员以为授权成功，实际上什么也没变。</p>
     *
     * <p>学生不参与岗位授权：岗位是教职工的职务，给学生挂管理岗没有业务含义，
     * 而且会让他在旧前端拿到校级菜单。</p>
     */
    private String resolveDuty(String requested, String personType) {
        if (STUDENT_TYPE.equals(personType)) {
            if (trimToNull(requested) != null) {
                throw new BusinessException("学生不能配置校内职务");
            }
            return null;
        }
        String value = trimToNull(requested);
        if (value == null) {
            return EduDuty.TEACHER.name();
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        Long exactCount = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictDataPo>()
                .eq(SysDictDataPo::getDictType, SCHOOL_DUTY_DICT)
                .eq(SysDictDataPo::getDictValue, normalized)
                .eq(SysDictDataPo::getStatus, 0));
        Long dictCount = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictDataPo>()
                .eq(SysDictDataPo::getDictType, SCHOOL_DUTY_DICT)
                .eq(SysDictDataPo::getStatus, 0));
        if ((dictCount != null && dictCount > 0 && (exactCount == null || exactCount == 0))
                || (dictCount == null || dictCount == 0) && EduDuty.of(normalized) == null) {
            throw new BusinessException("校内职务取值不合法: " + value);
        }
        return normalized;
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

    private static String requiredPhone(String value) {
        String phone = trimToNull(value);
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("手机号格式不正确");
        }
        return phone;
    }

    private Account createAccount(Long tenantId, EducationForms.Person form, String personType, String phone) {
        SysUserPo detachedAccount = userMapper.selectOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, tenantId)
                .eq(SysUserPo::getPhone, phone)
                .eq(SysUserPo::getStatus, 1)
                .likeRight(SysUserPo::getRemark, "教育人员")
                .last("LIMIT 1"));
        if (detachedAccount != null) {
            return reactivateAccount(tenantId, detachedAccount, form, personType, phone);
        }
        // 新增第二身份：同租户已有活跃账号（手机号匹配）时只新增 edu_person 绑定，
        // 不建新 sys_user、不重置密码、不停用/启用、不清账号管理角色。
        SysUserPo existingAccount = userMapper.selectOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, tenantId)
                .eq(SysUserPo::getPhone, phone)
                .eq(SysUserPo::getStatus, 0)
                .last("LIMIT 1"));
        if (existingAccount != null) {
            return linkExistingAccount(tenantId, existingAccount, form);
        }
        return createNewAccount(tenantId, form, personType, phone);
    }

    /**
     * 严格建新号（{@code CREATE} 模式）：手机号/用户名已存在即冲突，
     * 不静默改成关联已有账号；支持初始密码与管理端角色。
     */
    private Account createNewAccount(Long tenantId, EducationForms.Person form, String personType, String phone) {
        String username = trimToNull(form.username());
        if (username == null) {
            username = "u_" + phone;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException("登录名必须以字母开头，长度 4~30 位，只能包含字母、数字、下划线、点或连字符");
        }
        if (userMapper.checkUsernameUnique(username, tenantId, null) > 0) {
            throw new ConflictException("登录名“" + username + "”已存在，请更换后重试");
        }
        if (userMapper.checkPhoneUnique(phone, tenantId, null) > 0) {
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

    private Account reactivateAccount(Long tenantId, SysUserPo user, EducationForms.Person form, String personType, String phone) {
        requireNoDuplicateIdentity(tenantId, form.schoolId(), user.getId());
        Set<Long> roleIds = resolveRoles(form.roleIds(), personType);
        String rawPassword = trimToNull(form.password());
        boolean generated = rawPassword == null;
        if (generated) {
            rawPassword = PasswordUtil.generatePassword(GENERATED_PASSWORD_LENGTH);
        } else {
            PasswordUtil.validate(rawPassword);
        }
        user.setNickname(truncate(form.personName().trim(), NICKNAME_MAX_LENGTH));
        user.setPassword(PasswordUtil.encode(rawPassword));
        user.setStatus(normalStatus(form.status()));
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setPwdResetFlag(generated ? 1 : 0);
        user.setRemark("教育人员重新绑定账号");
        userMapper.updateById(user);
        accountIdentityService.syncFromPerson(user.getId(), form.personName(), phone);
        clearAccountRoles(user.getId());
        for (Long roleId : roleIds) {
            userRoleMapper.insert(new SysUserRolePo(user.getId(), roleId));
        }
        return new Account(user.getId(), user.getUsername(), generated ? rawPassword : null);
    }

    /**
     * 关联已有账号（新增第二身份）：只新增 {@code edu_person} 绑定，不建新号、不重置密码、
     * 不停用/启用账号、不清账号管理角色。禁止跨租户，同校同有效身份不得重复。
     */
    private Account linkExistingAccount(Long tenantId, SysUserPo user, EducationForms.Person form) {
        if (!Objects.equals(user.getTenantId(), tenantId)) {
            throw new BusinessException("不能关联其他租户的账号");
        }
        requireNoDuplicateIdentity(tenantId, form.schoolId(), user.getId());
        return new Account(user.getId(), user.getUsername(), null, user);
    }

    /** 同一账号在同一学校只能存在一条有效身份（任务书 12 节）。 */
    private void requireNoDuplicateIdentity(Long tenantId, Long schoolId, Long userId) {
        Long count = personMapper.selectCount(new LambdaQueryWrapper<EduPersonPo>()
                .eq(EduPersonPo::getTenantId, tenantId)
                .eq(EduPersonPo::getUserId, userId)
                .eq(EduPersonPo::getSchoolId, schoolId)
                .eq(EduPersonPo::getStatus, 0));
        if (count != null && count > 0) {
            throw new BusinessException("该账号在此学校已有有效身份，不能重复新增");
        }
    }

    /**
     * 已有账号只同步显示名、状态和角色，不改口令。
     *
     * <p>只有在调用方明确要改角色时才动 {@code sys_user_role}：空数组按"未提供"处理，
     * 否则前端漏回填一次就会静默清空该账号的全部角色。</p>
     *
     * <p>任务书 17：人员停用只使当前身份失效，由调用方在保存后统一撤销身份会话，
     * 这里不再把人员状态同步到账号状态。</p>
     */
    private AccountRefresh refreshAccount(Long userId, EducationForms.Person form, String phone, String duty,
                                          Long excludePersonId) {
        SysUserPo user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("人员关联的登录账号不存在，请先修复账号数据");
        }
        user.setNickname(truncate(form.personName().trim(), NICKNAME_MAX_LENGTH));
        if (!Objects.equals(phone, user.getPhone())
                && userMapper.checkPhoneUnique(phone, requireTenant(), userId) > 0) {
            throw new ConflictException("手机号“" + phone + "”已被其他账号使用");
        }
        user.setPhone(phone);
        userMapper.updateById(user);
        accountIdentityService.syncFromPerson(userId, form.personName(), phone);

        String personType = form.personType().trim().toUpperCase(Locale.ROOT);
        // 账号还关联其他学校的有效管理员身份时，普通教师/学生身份的编辑不得清空其角色（任务书 12 节）。
        boolean protectAdminRoles = !isSchoolAdmin(personType, duty)
                && accountHasSchoolAdminIdentity(userId, excludePersonId);
        boolean rolesCleared = false;
        if (STUDENT_TYPE.equals(personType)) {
            if (!protectAdminRoles) {
                clearAccountRoles(userId);
                rolesCleared = true;
            }
        } else if (form.wantsRoleChange() && !protectAdminRoles) {
            Set<Long> roleIds = resolveRoles(form.effectiveRoleIds(),
                    personType);
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRolePo>()
                    .eq(SysUserRolePo::getUserId, userId));
            for (Long roleId : roleIds) {
                userRoleMapper.insert(new SysUserRolePo(userId, roleId));
            }
            rolesCleared = true;
        }
        return new AccountRefresh(user.getUsername(), rolesCleared);
    }

    /**
     * {@code KEEP} 模式：保留绑定与口令/状态，同步账号显示资料；显式要求改角色时更新角色集合，
     * 角色真正变化时由调用方账号级撤销会话，未变化不撤销。
     */
    private KeepResult keepAccount(Long userId, EducationForms.Person form, String phone,
                                   String personType, String duty, Long excludePersonId) {
        SysUserPo user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("人员关联的登录账号不存在，请先修复账号数据");
        }
        user.setNickname(truncate(form.personName().trim(), NICKNAME_MAX_LENGTH));
        if (!Objects.equals(phone, user.getPhone())
                && userMapper.checkPhoneUnique(phone, requireTenant(), userId) > 0) {
            throw new ConflictException("手机号“" + phone + "”已被其他账号使用");
        }
        user.setPhone(phone);
        userMapper.updateById(user);
        accountIdentityService.syncFromPerson(userId, form.personName(), phone);

        // 账号还关联其他学校的有效管理员身份时，普通教师/学生身份的编辑不得清空其角色（任务书 12 节）。
        boolean protectAdminRoles = !isSchoolAdmin(personType, duty)
                && accountHasSchoolAdminIdentity(userId, excludePersonId);
        boolean rolesChanged = false;
        if (form.wantsRoleChange() && !protectAdminRoles) {
            Set<Long> roleIds = resolveRoles(form.effectiveRoleIds(), personType);
            if (!rolesEqual(currentRoleIds(userId), roleIds)) {
                clearAccountRoles(userId);
                for (Long roleId : roleIds) {
                    userRoleMapper.insert(new SysUserRolePo(userId, roleId));
                }
                rolesChanged = true;
            }
        }
        return new KeepResult(user.getUsername(), rolesChanged);
    }

    private Set<Long> currentRoleIds(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePo>()
                        .eq(SysUserRolePo::getUserId, userId))
                .stream().map(SysUserRolePo::getRoleId).collect(Collectors.toSet());
    }

    /** 角色集合比较：null 与空集合视为等价（都不代表任何角色）。 */
    private static boolean rolesEqual(Set<Long> before, Set<Long> after) {
        Set<Long> left = before == null ? Set.of() : before;
        Set<Long> right = after == null ? Set.of() : after;
        return left.equals(right);
    }

    /** {@code LINK} 模式的保存期复核：目标账号存在、属当前租户、正常且手机号一致。 */
    private SysUserPo requireLinkableAccount(Long tenantId, Long linkUserId, String phone) {
        if (linkUserId == null) {
            throw new BusinessException("关联已有账号必须传 linkUserId");
        }
        SysUserPo user = userMapper.selectById(linkUserId);
        if (user == null) {
            throw new BusinessException("要关联的账号不存在");
        }
        if (!Objects.equals(user.getTenantId(), tenantId)) {
            throw new BusinessException("不能关联其他租户的账号");
        }
        if (!Objects.equals(user.getStatus(), 0)) {
            throw new BusinessException("要关联的账号已停用或不可用");
        }
        if (!Objects.equals(user.getPhone(), phone)) {
            throw new BusinessException("要关联的账号手机号与人员手机号不一致");
        }
        return user;
    }

    /**
     * 删除/解绑/LINK 切绑等场景的统一账号处置：仅当该账号不再有其他有效教育身份、
     * 且账号本身是教育人员入口自动创建的客户端账号时，才停用账号、清角色并撤账号级会话。
     *
     * <p>独立系统账号即使失去最后一个教育身份也不停用、不清角色，只撤被删除/解绑身份的会话。</p>
     */
    private void disableAccountIfLastEducationAccount(Long userId, Long excludePersonId) {
        if (userId == null) {
            return;
        }
        if (hasOtherValidIdentity(userId, excludePersonId)) {
            return;
        }
        SysUserPo user = userMapper.selectById(userId);
        if (!isClientAccount(user)) {
            return;
        }
        disableAccount(user);
        clearAccountRoles(userId);
    }

    private void disableAccount(SysUserPo user) {
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
        // 账号级停用撤销该账号全部会话与课堂凭证。
        revokeAccountSession(user.getId());
    }

    private static boolean isClientAccount(SysUserPo user) {
        return user != null && user.getRemark() != null && user.getRemark().startsWith("教育人员");
    }

    private Set<Long> resolveRoles(List<Long> requested, String personType) {
        Set<Long> roleIds = new LinkedHashSet<>();
        if (requested != null) {
            requested.stream().filter(Objects::nonNull).forEach(roleIds::add);
        }
        if (roleIds.isEmpty()) {
            return roleIds;
        }
        if (STUDENT_TYPE.equals(personType)) {
            throw new BusinessException("学生账号不能分配管理端角色");
        }
        DataOwnerUtil.checkRolePermission(roleIds);
        for (Long roleId : roleIds) {
            SysRolePo role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException("角色不存在或不在当前数据范围: " + roleId);
            }
            if (ADMIN_ROLE_KEY.equals(role.getRoleKey()) && !SecurityContextHolder.isAdmin()) {
                throw new BusinessException("不能通过教育人员入口分配超级管理员角色");
            }
            if (!Objects.equals(role.getStatus(), 0)) {
                throw new BusinessException("角色已停用: " + role.getRoleName());
            }
            if (role.getRoleKey() != null) {
                String key = role.getRoleKey().toLowerCase(Locale.ROOT);
                if ("teacher".equals(key) || "student".equals(key)) {
                    throw new BusinessException("教师人员入口不能分配学生或历史教师角色");
                }
            }
        }
        return roleIds;
    }

    private void clearAccountRoles(Long userId) {
        if (userId != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRolePo>()
                    .eq(SysUserRolePo::getUserId, userId));
        }
    }

    /** 当前身份是否为校级管理员（身份类型教师 + 校内岗位管理员）。 */
    private static boolean isSchoolAdmin(String personType, String dutyCode) {
        return TEACHER_TYPE.equals(personType) && EduDuty.SCHOOL_ADMIN.name().equals(dutyCode);
    }

    /**
     * 账号是否还关联着其他学校的有效管理员身份（身份类型教师 + 校内岗位管理员，且身份有效、未离校）。
     *
     * <p>排除正在修改的人员 ID，只把<b>其他</b>有效 SCHOOL_ADMIN 身份算作保护条件；
     * 删除标志由 {@code @TableLogic} 自动过滤，无需重复拼装。</p>
     */
    private boolean accountHasSchoolAdminIdentity(Long userId, Long excludePersonId) {
        return validIdentitiesOf(userId, excludePersonId).stream()
                .anyMatch(person -> EduDuty.SCHOOL_ADMIN.name().equals(person.getDutyCode()));
    }

    /** 账号是否还有除 {@code excludePersonId} 外的其他有效绑定身份。 */
    private boolean hasOtherValidIdentity(Long userId, Long excludePersonId) {
        return !validIdentitiesOf(userId, excludePersonId).isEmpty();
    }

    /**
     * 统一的「有效教育身份」判断：{@code person.status=0}、未离校（{@code leave_flag IS NULL OR 0}）、
     * 未删除（{@code @TableLogic} 过滤）、学校存在且 status 正常且未删除、人员租户与学校租户一致。
     *
     * <p>{@code leave_flag=null} 按在校处理。解绑/删除最后身份判断、账号管理身份保护等
     * 全部复用这一处，避免多套口径分叉。</p>
     */
    private boolean isValidIdentity(EduPersonPo person) {
        if (person == null || person.getUserId() == null) {
            return false;
        }
        if (!Objects.equals(person.getStatus(), 0)) {
            return false;
        }
        Integer leave = person.getLeaveFlag();
        if (leave != null && leave != 0) {
            return false;
        }
        EduSchoolPo school = schoolMapper.selectById(person.getSchoolId());
        if (school == null || !Objects.equals(school.getStatus(), 0)) {
            return false;
        }
        return Objects.equals(person.getTenantId(), school.getTenantId());
    }

    /** 查询某账号除 {@code excludePersonId} 外的全部有效教育身份。 */
    private List<EduPersonPo> validIdentitiesOf(Long userId, Long excludePersonId) {
        if (userId == null) {
            return List.of();
        }
        return personMapper.selectList(new LambdaQueryWrapper<EduPersonPo>()
                        .eq(EduPersonPo::getUserId, userId)
                        .ne(excludePersonId != null, EduPersonPo::getId, excludePersonId))
                .stream().filter(this::isValidIdentity).toList();
    }

    // ---------------------------------------------------------------- 会话撤销

    /** 按身份粒度撤销会话与课堂 token；撤销失败抛出业务异常，由外层事务回滚身份变更。 */
    private void revokeIdentitySession(Long userId, Long identityId) {
        if (userId == null || identityId == null) {
            return;
        }
        SessionRevokeRequest request = new SessionRevokeRequest();
        request.setUserId(userId);
        request.setIdentityId(identityId);
        R<Void> result;
        try {
            result = authServiceClient.revokeSession(request);
        } catch (RuntimeException e) {
            log.error("撤销教育身份会话失败: userId={}, identityId={}", userId, identityId, e);
            throw new BusinessException("会话撤销失败，请稍后重试");
        }
        requireRevokeSuccess(result);
    }

    /** 账号级撤销全部会话与课堂凭证；撤销失败抛出业务异常，由外层事务回滚账号变更。 */
    private void revokeAccountSession(Long userId) {
        if (userId == null) {
            return;
        }
        SessionRevokeRequest request = new SessionRevokeRequest();
        request.setUserId(userId);
        R<Void> result;
        try {
            result = authServiceClient.revokeSession(request);
        } catch (RuntimeException e) {
            log.error("撤销账号会话失败: userId={}", userId, e);
            throw new BusinessException("会话撤销失败，请稍后重试");
        }
        requireRevokeSuccess(result);
    }

    /**
     * han-auth 内部异常由全局异常处理转成 HTTP 200 的 {@code R.fail(code,msg)}，调用方不会收到
     * 网络异常，因此必须按返回码判断撤销是否成功；失败抛出业务异常使外层事务回滚。
     */
    private void requireRevokeSuccess(R<Void> result) {
        if (result == null || !result.isSuccess()) {
            throw new BusinessException("会话撤销失败，请稍后重试");
        }
    }

    /**
     * 编辑后按身份级变更撤销旧会话：停用、离校、岗位 SCHOOL_ADMIN 降级、学校变更、管理角色清空。
     * 解绑/切绑已由 {@link #unbindIdentityState} 或 LINK 分支负责，这里只处理身份仍绑在同一账号的变更。
     */
    private void revokeOnIdentityEdit(Long oldUserId, Long newUserId, Long identityId,
                                      String oldDuty, String newDuty, Long oldSchoolId, Long newSchoolId,
                                      Integer oldLeaveFlag, Integer newLeaveFlag, Integer oldStatus, Integer newStatus,
                                      boolean rolesCleared) {
        if (identityId == null || oldUserId == null || !Objects.equals(oldUserId, newUserId)) {
            return;
        }
        boolean invalidated =
                (statusOf(oldStatus) == 0 && statusOf(newStatus) == 1)
                || (flagOf(oldLeaveFlag) == 0 && flagOf(newLeaveFlag) == 1)
                || (EduDuty.SCHOOL_ADMIN.name().equals(oldDuty) && !EduDuty.SCHOOL_ADMIN.name().equals(newDuty))
                || (oldSchoolId != null && !Objects.equals(oldSchoolId, newSchoolId))
                || rolesCleared;
        if (invalidated) {
            revokeIdentitySession(oldUserId, identityId);
        }
    }

    private static int statusOf(Integer status) {
        return status == null ? 0 : status;
    }

    private static int flagOf(Integer flag) {
        return flag == null ? 0 : flag;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? null : "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private static EducationForms.AccountMode parseAccountMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EducationForms.AccountMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("账号绑定模式取值不合法: " + value);
        }
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
            if (!"CLASS".equals(item.getNodeType())) {
                throw new BusinessException("人员归班只能选择最末级班级节点");
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
            if (!Objects.equals(subject.getSchoolId(), person.getSchoolId())) {
                throw new BusinessException("任教科目不属于人员所在学校");
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
        if (value == null || "EDU_BUREAU".equals(value.getOrgType())) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(value.getId());
        return value;
    }

    private EduPersonPo requirePerson(Long id) {
        EduPersonPo value = id != null ? personMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("人员不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(value.getSchoolId());
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

    private record Account(Long userId, String username, String initialPassword, SysUserPo linkedUser) {
        Account(Long userId, String username, String initialPassword) {
            this(userId, username, initialPassword, null);
        }
    }

    private record AccountRefresh(String username, boolean rolesCleared) {
    }

    private record KeepResult(String username, boolean rolesChanged) {
    }
}
