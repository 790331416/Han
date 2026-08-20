package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduRegionPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduUserScopePo;
import com.han.system.sdfz.education.domain.EducationRegionForms;
import com.han.system.sdfz.education.domain.EducationRegionNode;
import com.han.system.sdfz.education.domain.EducationRegionSearchOption;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduUserScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.han.system.sdfz.education.EducationSupport.LOCAL_SOURCE;

import static com.han.system.sdfz.education.EducationSupport.normalStatus;
import static com.han.system.sdfz.education.EducationSupport.requireTenant;
import static com.han.system.sdfz.education.EducationSupport.trimToNull;

/** 行政区和项目区域树；只用于组织归属和区域级授权。 */
@Service
@RequiredArgsConstructor
public class EducationRegionTreeService {
    private static final String NATIONAL_SOURCE = "NATIONAL";
    private final EduRegionMapper regionMapper;
    private final EduSchoolMapper schoolMapper;
    private final EduUserScopeMapper userScopeMapper;

    public List<EducationRegionNode> tree(Integer status) {
        EducationDataScopeService.requireTenantAdmin();
        List<EduRegionPo> regions = regionMapper.selectList(new LambdaQueryWrapper<EduRegionPo>()
                .eq(status != null, EduRegionPo::getStatus, status)
                .orderByAsc(EduRegionPo::getNodeLevel)
                .orderByAsc(EduRegionPo::getSort)
                .orderByAsc(EduRegionPo::getRegionName));
        Map<Long, EducationRegionNode> nodes = new LinkedHashMap<>();
        for (EduRegionPo region : regions) nodes.put(region.getId(), EducationRegionNode.from(region));
        List<EducationRegionNode> roots = new ArrayList<>();
        for (EducationRegionNode node : nodes.values()) {
            EducationRegionNode parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }

    /**
     * 区域管理树的单层节点。全国数据必须按层加载，避免一次性组装四级树。
     */
    public List<EduRegionPo> children(Long parentId, Integer status) {
        EducationDataScopeService.requireTenantAdmin();
        return selectChildren(parentId, status);
    }

    /** 组织表单的区域搜索或级联候选，避免把全国树一次性塞进新增学校弹窗。 */
    public List<EduRegionPo> options(String keyword, Long parentId) {
        requireTenant();
        if (parentId != null) {
            return selectChildren(parentId, 0);
        }
        String value = trimToNull(keyword);
        if (value == null) {
            return selectChildren(null, 0);
        }
        return regionMapper.selectList(new LambdaQueryWrapper<EduRegionPo>()
                .eq(EduRegionPo::getStatus, 0)
                .and(item -> item.like(EduRegionPo::getRegionName, value).or().like(EduRegionPo::getRegionCode, value))
                .orderByAsc(EduRegionPo::getNodeLevel).orderByAsc(EduRegionPo::getRegionCode)
                .last("LIMIT 100"));
    }

    /** 搜索结果只取匹配节点及其祖先名称，不向浏览器传输全国区域树。 */
    public List<EducationRegionSearchOption> searchOptions(String keyword) {
        requireTenant();
        String value = trimToNull(keyword);
        if (value == null) {
            return List.of();
        }
        List<EduRegionPo> matches = regionMapper.selectList(new LambdaQueryWrapper<EduRegionPo>()
                .eq(EduRegionPo::getStatus, 0)
                .and(item -> item.like(EduRegionPo::getRegionName, value).or().like(EduRegionPo::getRegionCode, value))
                .orderByAsc(EduRegionPo::getNodeLevel).orderByAsc(EduRegionPo::getRegionCode)
                .last("LIMIT 100"));
        if (matches.isEmpty()) {
            return List.of();
        }
        Set<Long> pathIds = new HashSet<>();
        for (EduRegionPo match : matches) {
            pathIds.addAll(pathIds(match));
        }
        Map<Long, EduRegionPo> regions = new LinkedHashMap<>();
        for (EduRegionPo region : regionMapper.selectList(new LambdaQueryWrapper<EduRegionPo>().in(EduRegionPo::getId, pathIds))) {
            regions.put(region.getId(), region);
        }
        return matches.stream().map(match -> new EducationRegionSearchOption(
                match.getId(), match.getParentId(), match.getRegionCode(), match.getRegionName(), match.getRegionLevel(), match.getNodeLevel(),
                pathIds(match).stream().map(regions::get).filter(Objects::nonNull).map(EduRegionPo::getRegionName).collect(java.util.stream.Collectors.joining(" > "))
        )).toList();
    }

    /** 返回根到目标区域的完整路径，供级联编辑值回填。 */
    public List<EduRegionPo> path(Long regionId) {
        requireTenant();
        List<EduRegionPo> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        EduRegionPo current = requireRegion(regionId);
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new BusinessException("区域树存在循环引用，请先修复历史数据");
            }
            result.add(current);
            current = current.getParentId() == null ? null : requireRegion(current.getParentId());
        }
        Collections.reverse(result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(EducationRegionForms.Region form) {
        EducationDataScopeService.requireTenantAdmin();
        EduRegionPo item = form.id() == null ? new EduRegionPo() : requireRegion(form.id());
        if (NATIONAL_SOURCE.equals(item.getSourceSystem())) {
            throw new BusinessException("全国行政区域基准数据不允许在管理端修改");
        }
        EduRegionPo parent = form.parentId() == null ? null : requireRegion(form.parentId());
        if (Objects.equals(item.getId(), form.parentId())) throw new BusinessException("区域上级不能是自身");
        TreePath path = pathOf(parent, item.getId());
        if (item.getId() == null) {
            item.setTenantId(requireTenant());
            item.setSourceSystem(LOCAL_SOURCE);
            item.setRegionCode(EducationCodeGenerator.unique("REGION", form.regionName(), candidate -> regionMapper.selectCount(
                    new LambdaQueryWrapper<EduRegionPo>().eq(EduRegionPo::getRegionCode, candidate)) > 0));
        }
        item.setParentId(form.parentId());
        item.setAncestors(path.ancestors());
        item.setNodeLevel(path.level());
        item.setRegionName(form.regionName().trim());
        item.setRegionLevel(form.regionLevel().trim().toUpperCase());
        item.setSort(form.sort());
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (item.getId() == null) regionMapper.insert(item);
        else {
            regionMapper.updateById(item);
            refreshDescendants(item.getId(), new HashSet<>());
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        EducationDataScopeService.requireTenantAdmin();
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要删除的区域");
        int removed = 0;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            EduRegionPo item = regionMapper.selectById(id);
            if (item == null) continue;
            if (NATIONAL_SOURCE.equals(item.getSourceSystem())) {
                throw new BusinessException("全国行政区域基准数据不允许删除");
            }
            rejectReference(regionMapper.selectCount(new LambdaQueryWrapper<EduRegionPo>().eq(EduRegionPo::getParentId, id)), "下级区域");
            rejectReference(schoolMapper.selectCount(new LambdaQueryWrapper<EduSchoolPo>().eq(EduSchoolPo::getRegionId, id)), "教育组织");
            rejectReference(userScopeMapper.selectCount(new LambdaQueryWrapper<EduUserScopePo>()
                    .eq(EduUserScopePo::getScopeType, "REGION").eq(EduUserScopePo::getScopeId, id)), "数据范围授权");
            removed += regionMapper.deleteById(id);
        }
        return removed;
    }

    private void refreshDescendants(Long parentId, Set<Long> visited) {
        if (!visited.add(parentId)) throw new BusinessException("区域树存在循环引用，请先修复历史数据");
        for (EduRegionPo child : regionMapper.selectList(new LambdaQueryWrapper<EduRegionPo>().eq(EduRegionPo::getParentId, parentId))) {
            TreePath path = pathOf(requireRegion(child.getParentId()), child.getId());
            child.setAncestors(path.ancestors());
            child.setNodeLevel(path.level());
            regionMapper.updateById(child);
            refreshDescendants(child.getId(), visited);
        }
    }

    private TreePath pathOf(EduRegionPo parent, Long forbiddenId) {
        if (parent == null) return new TreePath("0", 0);
        List<Long> reversed = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        EduRegionPo current = parent;
        while (current != null) {
            if (!visited.add(current.getId()) || Objects.equals(current.getId(), forbiddenId)) {
                throw new BusinessException("区域上级不能选择自身或其下级节点");
            }
            reversed.add(current.getId());
            current = current.getParentId() == null ? null : requireRegion(current.getParentId());
        }
        StringBuilder ancestors = new StringBuilder("0");
        for (int index = reversed.size() - 1; index >= 0; index--) ancestors.append(',').append(reversed.get(index));
        return new TreePath(ancestors.toString(), reversed.size());
    }

    private List<EduRegionPo> selectChildren(Long parentId, Integer status) {
        LambdaQueryWrapper<EduRegionPo> query = new LambdaQueryWrapper<EduRegionPo>()
                .eq(status != null, EduRegionPo::getStatus, status)
                .orderByAsc(EduRegionPo::getSort)
                .orderByAsc(EduRegionPo::getRegionName);
        if (parentId == null) {
            query.isNull(EduRegionPo::getParentId);
        } else {
            query.eq(EduRegionPo::getParentId, parentId);
        }
        return regionMapper.selectList(query);
    }

    private EduRegionPo requireRegion(Long id) {
        EduRegionPo value = id == null ? null : regionMapper.selectById(id);
        if (value == null) throw new BusinessException("区域不存在或不在当前租户");
        return value;
    }

    private static List<Long> pathIds(EduRegionPo region) {
        List<Long> result = new ArrayList<>();
        String ancestors = region.getAncestors();
        if (ancestors != null) {
            for (String value : ancestors.split(",")) {
                if (!"0".equals(value) && !value.isBlank()) {
                    result.add(Long.parseLong(value));
                }
            }
        }
        result.add(region.getId());
        return result;
    }

    private static void rejectReference(Long count, String name) {
        if (count != null && count > 0) throw new BusinessException("该区域下仍有 " + count + " 条" + name + "，请先处理后再删除");
    }

    private record TreePath(String ancestors, int level) {
    }
}
