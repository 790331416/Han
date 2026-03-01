package com.han.tenant.service;

import com.han.common.core.domain.PageResult;
import com.han.tenant.domain.dto.TenantPackageDTO;
import com.han.tenant.domain.vo.TenantPackageVO;

import java.util.List;
import java.util.Set;

/**
 * 租户套餐服务接口
 */
public interface ITenantPackageService {

    /**
     * 分页查询套餐列表
     */
    PageResult<TenantPackageVO> listPackages(String packageName, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 查询所有有效套餐
     */
    List<TenantPackageVO> listAllValidPackages();

    /**
     * 根据ID查询套餐详情
     */
    TenantPackageVO getPackageById(Long packageId);

    /**
     * 创建套餐
     */
    Long createPackage(TenantPackageDTO dto);

    /**
     * 更新套餐
     */
    void updatePackage(TenantPackageDTO dto);

    /**
     * 删除套餐
     */
    void deletePackage(Long packageId);

    /**
     * 修改套餐状态
     */
    void updateStatus(Long packageId, Integer status);

    /**
     * 获取套餐菜单ID列表
     */
    Set<Long> getPackageMenuIds(Long packageId);

    /**
     * 更新套餐菜单
     */
    void updatePackageMenus(Long packageId, Set<Long> menuIds);
}
