package com.han.system.service;

import com.han.system.domain.dto.SysDeptDto;
import com.han.system.domain.po.SysDeptPo;
import com.han.system.domain.query.SysDeptQuery;

import java.util.List;

/**
 * 部门服务接口
 */
public interface ISysDeptService {

    /**
     * 查询部门列表（平铺）
     */
    List<SysDeptPo> selectDeptList(SysDeptQuery query);

    /**
     * 查询部门树形结构
     */
    List<SysDeptPo> selectDeptTree(SysDeptQuery query);

    /**
     * 根据ID查询部门
     */
    SysDeptPo selectDeptById(Long deptId);

    /**
     * 查询部门及所有下级部门ID列表
     */
    List<Long> selectDeptAndChildIds(Long deptId);

    /**
     * 新增部门
     */
    void insertDept(SysDeptDto dto);

    /**
     * 修改部门
     */
    void updateDept(SysDeptDto dto);

    /**
     * 删除部门
     */
    void deleteDeptById(Long deptId);

    /**
     * 校验部门名称唯一
     */
    boolean checkDeptNameUnique(String deptName, Long parentId, Long deptId);

    /**
     * 是否存在子部门
     */
    boolean hasChildByDeptId(Long deptId);

    /**
     * 部门下是否存在用户
     */
    boolean checkDeptExistUser(Long deptId);
}
