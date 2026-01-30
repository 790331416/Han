package com.xuman.tenant.service;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.web.service.IBaseService;
import com.xuman.tenant.domain.dto.TenantDTO;
import com.xuman.tenant.domain.query.TenantQuery;
import com.xuman.tenant.domain.vo.TenantVO;

import java.util.List;

/**
 * 租户服务接口
 * 
 * <p>继承 {@link IBaseService} 获得通用CRUD能力
 * 
 * @author XuMan Team
 */
public interface TenantService extends IBaseService<TenantQuery, TenantDTO> {

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
     * 统计租户用户数
     */
    int countTenantUsers(Long tenantId);

    /**
     * 检查租户用户数是否超限
     */
    boolean checkUserLimit(Long tenantId);
}
