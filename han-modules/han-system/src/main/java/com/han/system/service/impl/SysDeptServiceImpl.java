package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanStrUtil;
import com.han.system.domain.dto.SysDeptDto;
import com.han.system.domain.po.SysDeptPo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.query.SysDeptQuery;
import com.han.system.converter.SysDeptConverter;
import com.han.system.mapper.SysDeptMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.service.ISysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门服务实现
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl implements ISysDeptService {

    /** ancestors 分隔符 */
    private static final String SEPARATOR = ",";

    /** 根节点的 ancestors 取值 */
    private static final String ROOT_ANCESTOR = "0";

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysDeptConverter deptConverter;

    @Override
    public List<SysDeptPo> selectDeptList(SysDeptQuery query) {
        return deptMapper.selectDeptListWithLeader(query.getDeptName(), query.getStatus());
    }

    @Override
    public List<SysDeptPo> selectDeptTree(SysDeptQuery query) {
        List<SysDeptPo> matched = selectDeptList(query);
        if (matched.isEmpty()) {
            return List.of();
        }
        // 带过滤条件时命中的多是非根节点，必须把它们的祖先补回来，否则建树时整支被丢弃
        boolean filtered = HanStrUtil.isNotBlank(query.getDeptName()) || query.getStatus() != null;
        return buildTree(filtered ? appendMissingAncestors(matched) : matched);
    }

    @Override
    public SysDeptPo selectDeptById(Long deptId) {
        SysDeptPo dept = deptMapper.selectById(deptId);
        if (dept != null) {
            // sys_dept 排序列是 post_sort，映射到 orderNum；sort 是非表字段，
            // 不回填的话编辑弹窗排序值恒为空，用户一保存就把排序清零
            dept.setSort(dept.getOrderNum());
        }
        return dept;
    }

    @Override
    public List<Long> selectDeptAndChildIds(Long deptId) {
        SysDeptPo dept = deptMapper.selectById(deptId);
        if (dept == null) {
            return List.of();
        }
        // 查询 ancestors 包含当前部门ID的所有子部门
        List<SysDeptPo> children = deptMapper.selectList(
                new LambdaQueryWrapper<SysDeptPo>()
                        .apply("position(',' || {0} || ',' in ',' || ancestors || ',') > 0", deptId)
        );
        List<Long> ids = new ArrayList<>();
        ids.add(deptId);
        for (SysDeptPo child : children) {
            ids.add(child.getId());
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertDept(SysDeptDto dto) {
        if (!checkDeptNameUnique(dto.getDeptName(), dto.getParentId(), null)) {
            throw new BusinessException("部门名称[" + dto.getDeptName() + "]已存在");
        }

        SysDeptPo dept = deptConverter.toPo(dto);
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        if (dept.getStatus() == null) {
            dept.setStatus(0);
        }

        // 计算 ancestors
        if (dept.getParentId() != 0L) {
            SysDeptPo parentDept = deptMapper.selectById(dept.getParentId());
            if (parentDept == null) {
                throw new BusinessException("父部门不存在");
            }
            if (parentDept.getStatus() != null && parentDept.getStatus() == 1) {
                throw new BusinessException("父部门已停用，不允许新增子部门");
            }
            dept.setAncestors(safeAncestors(parentDept) + SEPARATOR + dept.getParentId());
        } else {
            dept.setAncestors(ROOT_ANCESTOR);
        }

        deptMapper.insert(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(SysDeptDto dto) {
        if (dto.getDeptId() == null) {
            throw new BusinessException("部门ID不能为空");
        }
        if (!checkDeptNameUnique(dto.getDeptName(), dto.getParentId(), dto.getDeptId())) {
            throw new BusinessException("部门名称[" + dto.getDeptName() + "]已存在");
        }

        SysDeptPo oldDept = deptMapper.selectById(dto.getDeptId());
        if (oldDept == null) {
            throw new BusinessException("部门不存在");
        }

        SysDeptPo dept = new SysDeptPo();
        dept.setId(dto.getDeptId());
        deptConverter.updatePo(dto, dept);

        // 如果父部门变更，级联更新子部门 ancestors
        if (dto.getParentId() != null && !dto.getParentId().equals(oldDept.getParentId())) {
            String newAncestors;
            if (dto.getParentId() == 0L) {
                newAncestors = ROOT_ANCESTOR;
            } else {
                SysDeptPo newParent = deptMapper.selectById(dto.getParentId());
                if (newParent == null) {
                    throw new BusinessException("父部门不存在");
                }
                assertNotMovingIntoOwnSubtree(oldDept, newParent);
                newAncestors = safeAncestors(newParent) + SEPARATOR + dto.getParentId();
            }
            dept.setAncestors(newAncestors);
            moveSubtree(oldDept, newAncestors);
        }

        deptMapper.updateById(dept);
    }

    /**
     * 禁止把部门挂到自己或自己的子孙下。
     *
     * <p>成环之后所有树遍历都会死循环或栈溢出，且破坏后很难还原，因此在移动前就拦。
     */
    private void assertNotMovingIntoOwnSubtree(SysDeptPo current, SysDeptPo newParent) {
        if (newParent.getId().equals(current.getId())) {
            throw new BusinessException("上级部门不能是自己");
        }
        if (parseAncestorIds(safeAncestors(newParent)).contains(current.getId())) {
            throw new BusinessException("上级部门不能是自己的下级部门");
        }
    }

    /**
     * 把整棵子树的 ancestors 从旧前缀改写成新前缀。
     *
     * <p>旧实现用 {@code LIKE '0,100%'} 匹配，会连带命中兄弟部门 1001 的子树（{@code '0,1001,...'}
     * 同样以 {@code '0,100'} 开头）。这里补上分隔符边界：要么恰好等于旧前缀，要么以「旧前缀 + 逗号」开头。
     * 数据权限的「本部门及以下」正是按 ancestors 判定范围，写坏即越权，因此按安全问题处理。
     */
    private void moveSubtree(SysDeptPo oldDept, String newAncestors) {
        String oldPrefix = safeAncestors(oldDept) + SEPARATOR + oldDept.getId();
        String newPrefix = newAncestors + SEPARATOR + oldDept.getId();

        List<SysDeptPo> children = deptMapper.selectList(
                new LambdaQueryWrapper<SysDeptPo>()
                        .and(w -> w.eq(SysDeptPo::getAncestors, oldPrefix)
                                .or().likeRight(SysDeptPo::getAncestors, oldPrefix + SEPARATOR))
        );
        for (SysDeptPo child : children) {
            child.setAncestors(newPrefix + child.getAncestors().substring(oldPrefix.length()));
            deptMapper.updateById(child);
        }
    }

    @Override
    public void deleteDeptById(Long deptId) {
        if (hasChildByDeptId(deptId)) {
            throw new BusinessException("存在子部门，不允许删除");
        }
        if (checkDeptExistUser(deptId)) {
            throw new BusinessException("部门下存在用户，不允许删除");
        }
        deptMapper.deleteById(deptId);
    }

    @Override
    public boolean checkDeptNameUnique(String deptName, Long parentId, Long deptId) {
        LambdaQueryWrapper<SysDeptPo> wrapper = new LambdaQueryWrapper<SysDeptPo>()
                .eq(SysDeptPo::getDeptName, deptName)
                .eq(SysDeptPo::getParentId, parentId != null ? parentId : 0L);
        if (deptId != null) {
            wrapper.ne(SysDeptPo::getId, deptId);
        }
        return deptMapper.selectCount(wrapper) == 0;
    }

    @Override
    public boolean hasChildByDeptId(Long deptId) {
        return deptMapper.selectCount(
                new LambdaQueryWrapper<SysDeptPo>().eq(SysDeptPo::getParentId, deptId)
        ) > 0;
    }

    @Override
    public boolean checkDeptExistUser(Long deptId) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<SysUserPo>().eq(SysUserPo::getDeptId, deptId)
        ) > 0;
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<SysDeptPo> buildQueryWrapper(SysDeptQuery query) {
        return new LambdaQueryWrapper<SysDeptPo>()
                .like(query.getDeptName() != null && !query.getDeptName().isEmpty(),
                        SysDeptPo::getDeptName, query.getDeptName())
                .eq(query.getStatus() != null, SysDeptPo::getStatus, query.getStatus())
                .orderByAsc(SysDeptPo::getParentId)
                .orderByAsc(SysDeptPo::getSort);
    }

    /**
     * 把结果集中缺失的祖先节点补齐。
     *
     * <p>按名称或状态过滤时命中的往往是深层节点，它们的父节点不在结果里，
     * 建树时会被当成挂不上的孤儿丢掉，表现为「搜索有匹配但页面空白」。
     */
    private List<SysDeptPo> appendMissingAncestors(List<SysDeptPo> matched) {
        Set<Long> present = matched.stream().map(SysDeptPo::getId).collect(Collectors.toSet());
        Set<Long> missing = new LinkedHashSet<>();
        for (SysDeptPo dept : matched) {
            for (Long ancestorId : parseAncestorIds(safeAncestors(dept))) {
                if (!present.contains(ancestorId)) {
                    missing.add(ancestorId);
                }
            }
        }
        if (missing.isEmpty()) {
            return matched;
        }

        List<SysDeptPo> ancestors = deptMapper.selectList(
                new LambdaQueryWrapper<SysDeptPo>().in(SysDeptPo::getId, missing));
        List<SysDeptPo> merged = new ArrayList<>(matched.size() + ancestors.size());
        merged.addAll(matched);
        for (SysDeptPo ancestor : ancestors) {
            ancestor.setSort(ancestor.getOrderNum());
            merged.add(ancestor);
        }
        merged.sort(Comparator
                .comparing((SysDeptPo d) -> d.getParentId() == null ? 0L : d.getParentId())
                .thenComparing(d -> d.getOrderNum() == null ? Integer.MAX_VALUE : d.getOrderNum()));
        return merged;
    }

    /**
     * 解析 ancestors 字符串里的部门ID，跳过根标记 0 与非法片段。
     */
    private Set<Long> parseAncestorIds(String ancestors) {
        Set<Long> ids = new LinkedHashSet<>();
        if (HanStrUtil.isBlank(ancestors)) {
            return ids;
        }
        for (String part : ancestors.split(SEPARATOR)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || ROOT_ANCESTOR.equals(trimmed)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 历史脏数据，跳过即可，不能因为一条坏记录让整棵树不可用
            }
        }
        return ids;
    }

    private String safeAncestors(SysDeptPo dept) {
        String ancestors = dept.getAncestors();
        return HanStrUtil.isBlank(ancestors) ? ROOT_ANCESTOR : ancestors;
    }

    private List<SysDeptPo> buildTree(List<SysDeptPo> depts) {
        Map<Long, List<SysDeptPo>> grouped = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (SysDeptPo dept : depts) {
            ids.add(dept.getId());
            grouped.computeIfAbsent(parentIdOf(dept), key -> new ArrayList<>()).add(dept);
        }

        List<SysDeptPo> roots = new ArrayList<>();
        for (SysDeptPo dept : depts) {
            dept.setChildren(new ArrayList<>(grouped.getOrDefault(dept.getId(), List.of())));
            Long parentId = parentIdOf(dept);
            // 父节点不在结果集里的节点按根节点处理，而不是静默丢弃
            if (parentId == 0L || !ids.contains(parentId)) {
                roots.add(dept);
            }
        }
        return roots;
    }

    private long parentIdOf(SysDeptPo dept) {
        return dept.getParentId() == null ? 0L : dept.getParentId();
    }
}
