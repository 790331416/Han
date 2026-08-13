package com.han.tenant.service;

import com.han.common.core.domain.PageResult;
import com.han.common.web.service.IBaseService;
import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.query.TenantQuery;
import com.han.tenant.domain.vo.TenantOptionVO;
import com.han.tenant.domain.vo.TenantVO;

import java.util.List;

/**
 * 租户服务接口
 */
public interface ITenantService extends IBaseService<TenantQuery, TenantDTO> {

    /**
     * 分页查询租户列表
     */
    PageResult<TenantDTO> selectPage(TenantQuery query);

    /**
     * 根据域名查询租户
     */
    TenantVO getTenantByDomain(String domain);

    /**
     * 修改租户状态
     */
    void updateStatus(Long tenantId, Integer status);

    /**
     * 检查租户是否有效(未停用、未过期)
     */
    boolean checkTenantValid(Long tenantId);

    /**
     * 同步租户套餐菜单
     */
    void syncTenantPackage(Long tenantId, Long packageId);

    /**
     * 查询所有有效租户
     */
    List<TenantVO> listAllValidTenants();

    /**
     * 查询所有有效租户的下拉选项（只含租户 ID 与名称，供匿名登录页使用）
     */
    List<TenantOptionVO> listTenantOptions();

    /**
     * 按域名查询租户下拉选项（只含租户 ID 与名称，供匿名登录页使用）
     */
    TenantOptionVO getTenantOptionByDomain(String domain);

    /**
     * 按租户 ID 查询契约层租户信息，供 {@code /inner/tenant/{tenantId}} 使用
     */
    com.han.api.tenant.domain.TenantVO getApiTenantById(Long tenantId);

    /**
     * 查询所有有效租户的契约层信息，供 {@code /inner/tenant/listAllValid} 使用
     */
    List<com.han.api.tenant.domain.TenantVO> listApiValidTenants();

    /**
     * 统计租户用户数
     */
    int countTenantUsers(Long tenantId);

    /**
     * 检查租户用户数是否超限
     */
    boolean checkUserLimit(Long tenantId);

    /**
     * 安全删除租户（逻辑删除 + 级联清理）
     */
    void deleteTenant(Long tenantId);
}
