package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduUserScopePo;
import com.han.system.sdfz.education.domain.EducationScopeForms;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduUserScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.han.system.sdfz.education.EducationSupport.requireTenant;

/** 管理端教育数据范围的唯一解析入口；前端 schoolId 只能筛选，不能扩大本结果。 */
@Service
@RequiredArgsConstructor
public class EducationDataScopeService {
    private static final String ORG = "ORG";

    private final EduUserScopeMapper userScopeMapper;
    private final EduSchoolMapper schoolMapper;

    public Scope current() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null || user.getUserId() == null || user.getTenantId() == null) {
            throw new BusinessException("缺少登录租户上下文");
        }
        if (isTenantAdmin(user)) {
            return Scope.tenantWide();
        }
        List<EduUserScopePo> grants = userScopeMapper.selectList(new LambdaQueryWrapper<EduUserScopePo>()
                .eq(EduUserScopePo::getUserId, user.getUserId())
                .eq(EduUserScopePo::getStatus, 0));
        if (grants.isEmpty()) {
            return Scope.denyAll();
        }
        Set<Long> organizationIds = new LinkedHashSet<>();
        for (EduUserScopePo grant : grants) {
            if (grant.getScopeId() == null) {
                continue;
            }
            String type = normalize(grant.getScopeType());
            if (ORG.equals(type)) {
                organizationIds.addAll(organizationsUnder(grant.getScopeId(), includesChildren(grant)));
            }
        }
        if (organizationIds.isEmpty()) {
            return Scope.denyAll();
        }
        List<EduSchoolPo> organizations = schoolMapper.selectBatchIds(organizationIds);
        Set<Long> schoolIds = new LinkedHashSet<>();
        for (EduSchoolPo organization : organizations) {
            if (!"EDU_BUREAU".equals(organization.getOrgType())) {
                schoolIds.add(organization.getId());
            }
        }
        return new Scope(false, Set.copyOf(organizationIds), Set.copyOf(schoolIds));
    }

    public void requireSchool(Long schoolId) {
        Scope scope = current();
        if (!scope.all() && (schoolId == null || !scope.schoolIds().contains(schoolId))) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
    }

    public void requireOrganization(Long organizationId) {
        Scope scope = current();
        if (!scope.all() && (organizationId == null || !scope.organizationIds().contains(organizationId))) {
            throw new BusinessException("教育组织不存在或不在当前数据范围");
        }
    }

    public List<EduUserScopePo> listForUser(Long userId) {
        requireTenant();
        return userScopeMapper.selectList(new LambdaQueryWrapper<EduUserScopePo>()
                .eq(EduUserScopePo::getUserId, userId)
                .eq(EduUserScopePo::getStatus, 0)
                .orderByAsc(EduUserScopePo::getScopeType)
                .orderByAsc(EduUserScopePo::getScopeId));
    }

    /** 替换一名管理员的全部教育范围；空集合即撤销全部授权。 */
    @Transactional(rollbackFor = Exception.class)
    public int replaceForUser(EducationScopeForms.Replace form) {
        Long tenantId = requireTenant();
        Set<String> keys = new LinkedHashSet<>();
        for (EducationScopeForms.Item item : form.items()) {
            String type = normalize(item.scopeType());
            if (!ORG.equals(type)) {
                throw new BusinessException("数据范围只能授权教育局或学校");
            }
            if (!keys.add(type + ':' + item.scopeId())) {
                throw new BusinessException("同一用户不能重复授予相同教育范围");
            }
            if (ORG.equals(type) && schoolMapper.selectById(item.scopeId()) == null) {
                throw new BusinessException("授权教育组织不存在或不在当前租户");
            }
        }
        for (EduUserScopePo previous : userScopeMapper.selectList(new LambdaQueryWrapper<EduUserScopePo>()
                .eq(EduUserScopePo::getUserId, form.userId()))) {
            userScopeMapper.deleteById(previous.getId());
        }
        for (EducationScopeForms.Item item : form.items()) {
            EduUserScopePo value = new EduUserScopePo();
            value.setTenantId(tenantId);
            value.setUserId(form.userId());
            value.setScopeType(normalize(item.scopeType()));
            value.setScopeId(item.scopeId());
            value.setIncludeChildren(item.includeChildren() ? 1 : 0);
            value.setStatus(0);
            value.setRemark(item.remark());
            userScopeMapper.insert(value);
        }
        return form.items().size();
    }

    private Set<Long> organizationsUnder(Long organizationId, boolean includeChildren) {
        LambdaQueryWrapper<EduSchoolPo> query = new LambdaQueryWrapper<EduSchoolPo>().eq(EduSchoolPo::getId, organizationId);
        if (includeChildren) {
            query.or(item -> item.apply("FIND_IN_SET({0}, ancestors)", organizationId));
        }
        return schoolMapper.selectList(query).stream().map(EduSchoolPo::getId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean includesChildren(EduUserScopePo value) {
        return value.getIncludeChildren() == null || value.getIncludeChildren() == 1;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isTenantAdmin(LoginUser user) {
        return user.isAdmin() || user.hasRole("admin") || user.hasRole("tenantAdmin");
    }

    public record Scope(boolean all, Set<Long> organizationIds, Set<Long> schoolIds) {
        static Scope tenantWide() { return new Scope(true, Set.of(), Set.of()); }
        static Scope denyAll() { return new Scope(false, Set.of(), Set.of()); }
    }
}
