package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.api.system.AuthServiceClient;
import com.han.api.system.domain.SessionRevokeRequest;
import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduRegionPo;
import com.han.system.sdfz.education.domain.EducationOrganizationForms;
import com.han.system.sdfz.education.domain.EducationOrganizationNode;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.han.system.sdfz.education.EducationSupport.LOCAL_SOURCE;
import static com.han.system.sdfz.education.EducationSupport.normalStatus;
import static com.han.system.sdfz.education.EducationSupport.requireLocalSource;
import static com.han.system.sdfz.education.EducationSupport.requireTenant;
import static com.han.system.sdfz.education.EducationSupport.trimToNull;

/** 教育局、学校和校区的受约束树形管理。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EducationOrganizationTreeService {

    private static final String EDU_BUREAU = "EDU_BUREAU";
    private static final String SCHOOL = "SCHOOL";

    private final EduSchoolMapper schoolMapper;
    private final EduRegionMapper regionMapper;
    private final EduPersonMapper personMapper;
    private final EducationDataScopeService dataScopeService;
    private final AuthServiceClient authServiceClient;

    public List<EducationOrganizationNode> tree(Integer status) {
        requireTenant();
        EducationDataScopeService.Scope scope = dataScopeService.current();
        LambdaQueryWrapper<EduSchoolPo> query = new LambdaQueryWrapper<EduSchoolPo>()
                .eq(status != null, EduSchoolPo::getStatus, status)
                .orderByAsc(EduSchoolPo::getNodeLevel)
                .orderByAsc(EduSchoolPo::getSchoolName);
        if (!scope.all()) {
            if (scope.organizationIds().isEmpty()) {
                return List.of();
            }
            query.in(EduSchoolPo::getId, scope.organizationIds());
        }
        List<EduSchoolPo> schools = schoolMapper.selectList(query);
        if (!scope.all()) {
            schools = withBureauAncestors(schools);
        }
        Map<Long, String> regionNames = regionNames(schools);
        Map<Long, EducationOrganizationNode> nodes = new LinkedHashMap<>();
        for (EduSchoolPo school : schools) {
            String regionName = school.getRegionId() == null ? null : regionNames.get(school.getRegionId());
            nodes.put(school.getId(), EducationOrganizationNode.from(school, regionName));
        }
        List<EducationOrganizationNode> roots = new ArrayList<>();
        for (EducationOrganizationNode node : nodes.values()) {
            EducationOrganizationNode parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    private Map<Long, String> regionNames(List<EduSchoolPo> schools) {
        List<Long> ids = schools.stream().map(EduSchoolPo::getRegionId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (EduRegionPo region : regionMapper.selectList(new LambdaQueryWrapper<EduRegionPo>().in(EduRegionPo::getId, ids))) {
            result.put(region.getId(), region.getRegionName());
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(EducationOrganizationForms.Organization form) {
        requireTenant();
        if (form.id() == null && form.parentId() == null && !dataScopeService.current().all()) {
            throw new BusinessException("仅租户超级管理员可以新增根教育组织");
        }
        EduSchoolPo item = form.id() == null ? new EduSchoolPo() : requireSchool(form.id());
        Integer oldStatus = item.getStatus();
        if (form.id() != null) {
            requireLocalSource(item.getSourceSystem(), "教育组织");
        }
        String orgType = requireOrgType(form.orgType());
        EduSchoolPo parent = form.parentId() == null ? null : requireSchool(form.parentId());
        EduRegionPo region = requireRegion(form.regionId());
        if (parent != null) {
            EduRegionPo parentRegion = requireRegion(parent.getRegionId());
            if (!containsRegion(parentRegion.getId(), region)) {
                throw new BusinessException("下级组织区域必须属于上级组织区域");
            }
        }
        validateParent(item.getId(), parent, orgType);
        if (item.getId() != null) validateDescendantRegions(item.getId(), region);

        String code = item.getId() == null
                ? EducationCodeGenerator.unique("ORG", form.schoolName(), candidate -> schoolMapper.selectCount(
                        new LambdaQueryWrapper<EduSchoolPo>().eq(EduSchoolPo::getSchoolCode, candidate)) > 0)
                : item.getSchoolCode();
        TreePath path = pathOf(parent, item.getId());
        item.setParentId(form.parentId());
        item.setAncestors(path.ancestors());
        item.setNodeLevel(path.level());
        item.setSchoolCode(code);
        item.setSchoolName(form.schoolName().trim());
        item.setSchoolRole("NORMAL");
        item.setOrgType(orgType);
        item.setSchoolManageType(SCHOOL.equals(orgType) ? trimToNull(form.schoolManageType()) : null);
        item.setSchoolProperty(SCHOOL.equals(orgType) ? trimToNull(form.schoolProperty()) : null);
        item.setRegionId(region.getId());
        // 兼容仍读取 area_code 的旧三课堂接口；值只由所选区域派生，管理端不再接受手工输入。
        item.setAreaCode(region.getRegionCode());
        item.setAutoUpgradeEnabled(normalStatus(form.autoUpgradeEnabled()));
        int newStatus = normalStatus(form.status());
        item.setStatus(newStatus);
        item.setRemark(trimToNull(form.remark()));
        if (item.getId() == null) {
            item.setTenantId(requireTenant());
            item.setSourceSystem(LOCAL_SOURCE);
            schoolMapper.insert(item);
        } else {
            // 教育组织由正常停用：先撤销该校全部已绑定教育身份的会话，撤销失败事务回滚状态更新。
            if (Objects.equals(oldStatus, 0) && newStatus == 1) {
                revokeSchoolIdentitySessions(item.getId());
            }
            schoolMapper.updateById(item);
            refreshDescendants(item.getId(), new HashSet<>());
        }
        return item.getId();
    }

    /** 教育组织停用时逐条撤销该校全部已绑定教育身份的会话；撤销失败抛出异常回滚状态更新。 */
    private void revokeSchoolIdentitySessions(Long schoolId) {
        List<EduPersonPo> people = personMapper.selectList(new LambdaQueryWrapper<EduPersonPo>()
                .eq(EduPersonPo::getSchoolId, schoolId)
                .isNotNull(EduPersonPo::getUserId));
        for (EduPersonPo person : people) {
            if (person.getUserId() == null) {
                continue;
            }
            revokeIdentitySession(person.getUserId(), person.getId());
        }
    }

    private void revokeIdentitySession(Long userId, Long identityId) {
        try {
            SessionRevokeRequest request = new SessionRevokeRequest();
            request.setUserId(userId);
            request.setIdentityId(identityId);
            authServiceClient.revokeSession(request);
        } catch (RuntimeException e) {
            log.error("撤销教育身份会话失败: userId={}, identityId={}", userId, identityId, e);
            throw new BusinessException("会话撤销失败，请稍后重试");
        }
    }

    private void refreshDescendants(Long parentId, Set<Long> visited) {
        if (!visited.add(parentId)) {
            throw new BusinessException("教育组织存在循环引用，请先修复历史数据");
        }
        List<EduSchoolPo> children = schoolMapper.selectList(new LambdaQueryWrapper<EduSchoolPo>()
                .eq(EduSchoolPo::getParentId, parentId));
        for (EduSchoolPo child : children) {
            TreePath path = pathOf(requireSchool(child.getParentId()), child.getId());
            child.setAncestors(path.ancestors());
            child.setNodeLevel(path.level());
            schoolMapper.updateById(child);
            refreshDescendants(child.getId(), visited);
        }
    }

    /** 直属学校授权只补教育局祖先用于展示，不扩大可操作组织集合。 */
    private List<EduSchoolPo> withBureauAncestors(List<EduSchoolPo> scoped) {
        Set<Long> ancestorIds = new LinkedHashSet<>();
        for (EduSchoolPo item : scoped) {
            if (item.getAncestors() == null) continue;
            for (String value : item.getAncestors().split(",")) {
                if (!value.isBlank() && !"0".equals(value)) ancestorIds.add(Long.parseLong(value));
            }
        }
        if (ancestorIds.isEmpty()) return scoped;
        Map<Long, EduSchoolPo> visible = new LinkedHashMap<>();
        for (EduSchoolPo ancestor : schoolMapper.selectBatchIds(ancestorIds)) {
            if (EDU_BUREAU.equals(ancestor.getOrgType())) visible.put(ancestor.getId(), ancestor);
        }
        for (EduSchoolPo item : scoped) visible.put(item.getId(), item);
        return visible.values().stream()
                .sorted(java.util.Comparator.comparingInt(item -> item.getNodeLevel() == null ? 0 : item.getNodeLevel()))
                .toList();
    }

    private static boolean containsRegion(Long parentRegionId, EduRegionPo childRegion) {
        if (Objects.equals(parentRegionId, childRegion.getId())) return true;
        String ancestors = childRegion.getAncestors();
        if (ancestors == null) return false;
        return java.util.Arrays.stream(ancestors.split(","))
                .map(String::trim).anyMatch(value -> value.equals(String.valueOf(parentRegionId)));
    }

    private void validateParent(Long selfId, EduSchoolPo parent, String childType) {
        if (parent == null) {
            return;
        }
        if (Objects.equals(parent.getId(), selfId)) {
            throw new BusinessException("教育组织上级不能是自身");
        }
        String parentType = requireOrgType(parent.getOrgType());
        if (SCHOOL.equals(parentType) && EDU_BUREAU.equals(childType)) {
            throw new BusinessException("教育局不能挂在学校或校区下");
        }
        if (selfId != null) {
            pathOf(parent, selfId);
        }
    }

    private void validateDescendantRegions(Long organizationId, EduRegionPo parentRegion) {
        for (EduSchoolPo child : schoolMapper.selectList(new LambdaQueryWrapper<EduSchoolPo>()
                .apply("FIND_IN_SET({0}, ancestors)", organizationId))) {
            if (!containsRegion(parentRegion.getId(), requireRegion(child.getRegionId()))) {
                throw new BusinessException("所选区域不能覆盖现有下级组织：" + child.getSchoolName());
            }
        }
    }

    private TreePath pathOf(EduSchoolPo parent, Long forbiddenId) {
        if (parent == null) {
            return new TreePath("0", 0);
        }
        List<Long> reversed = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        EduSchoolPo current = parent;
        while (current != null) {
            if (!visited.add(current.getId()) || Objects.equals(current.getId(), forbiddenId)) {
                throw new BusinessException("教育组织上级不能选择自身或其下级节点");
            }
            reversed.add(current.getId());
            current = current.getParentId() == null ? null : requireSchool(current.getParentId());
        }
        StringBuilder ancestors = new StringBuilder("0");
        for (int index = reversed.size() - 1; index >= 0; index--) {
            ancestors.append(',').append(reversed.get(index));
        }
        return new TreePath(ancestors.toString(), reversed.size());
    }

    private EduSchoolPo requireSchool(Long id) {
        EduSchoolPo school = id == null ? null : schoolMapper.selectById(id);
        if (school == null) {
            throw new BusinessException("教育组织不存在或不在当前租户");
        }
        dataScopeService.requireOrganization(id);
        return school;
    }

    private EduRegionPo requireRegion(Long id) {
        EduRegionPo region = id == null ? null : regionMapper.selectById(id);
        if (region == null || region.getStatus() == null || region.getStatus() != 0) {
            throw new BusinessException("所选区域不存在、已停用或不在当前租户");
        }
        return region;
    }

    private static String requireOrgType(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("机构类型不能为空");
        }
        String type = value.trim().toUpperCase(Locale.ROOT);
        if (!EDU_BUREAU.equals(type) && !SCHOOL.equals(type)) {
            throw new BusinessException("机构类型只能是 EDU_BUREAU 或 SCHOOL");
        }
        return type;
    }

    private record TreePath(String ancestors, int level) {
    }
}
