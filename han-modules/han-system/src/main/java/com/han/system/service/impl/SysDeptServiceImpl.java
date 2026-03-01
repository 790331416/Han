package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门服务实现
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl implements ISysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysDeptConverter deptConverter;

    @Override
    public List<SysDeptPo> selectDeptList(SysDeptQuery query) {
        return deptMapper.selectList(buildQueryWrapper(query));
    }

    @Override
    public List<SysDeptPo> selectDeptTree(SysDeptQuery query) {
        List<SysDeptPo> depts = selectDeptList(query);
        return buildTree(depts);
    }

    @Override
    public SysDeptPo selectDeptById(Long deptId) {
        return deptMapper.selectById(deptId);
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
            dept.setAncestors(parentDept.getAncestors() + "," + dept.getParentId());
        } else {
            dept.setAncestors("0");
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
                newAncestors = "0";
            } else {
                SysDeptPo newParent = deptMapper.selectById(dto.getParentId());
                if (newParent == null) {
                    throw new BusinessException("父部门不存在");
                }
                newAncestors = newParent.getAncestors() + "," + dto.getParentId();
            }
            dept.setAncestors(newAncestors);

            // 级联更新子部门 ancestors
            String oldAncestors = oldDept.getAncestors() + "," + oldDept.getId();
            List<SysDeptPo> children = deptMapper.selectList(
                    new LambdaQueryWrapper<SysDeptPo>()
                            .apply("ancestors LIKE {0}", oldAncestors + "%")
            );
            for (SysDeptPo child : children) {
                child.setAncestors(child.getAncestors().replace(
                        oldDept.getAncestors() + "," + oldDept.getId(),
                        newAncestors + "," + dto.getDeptId()
                ));
                deptMapper.updateById(child);
            }
        }

        deptMapper.updateById(dept);
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

    private List<SysDeptPo> buildTree(List<SysDeptPo> depts) {
        Map<Long, List<SysDeptPo>> grouped = depts.stream()
                .collect(Collectors.groupingBy(SysDeptPo::getParentId));

        List<SysDeptPo> roots = new ArrayList<>();
        for (SysDeptPo dept : depts) {
            List<SysDeptPo> children = grouped.getOrDefault(dept.getId(), List.of());
            dept.setChildren(new ArrayList<>(children));
            if (dept.getParentId() == 0L) {
                roots.add(dept);
            }
        }
        return roots;
    }
}
