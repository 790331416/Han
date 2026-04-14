package com.han.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.TenantInitDto;
import com.han.tenant.converter.TenantConverter;
import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.po.TenantPackagePo;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.query.TenantQuery;
import com.han.tenant.domain.vo.TenantVO;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.mapper.TenantPackageMapper;
import com.han.tenant.service.ITenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 租户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements ITenantService {

    private final TenantMapper tenantMapper;
    private final TenantPackageMapper packageMapper;
    private final TenantConverter tenantConverter;
    private final SystemServiceClient systemServiceClient;

    @Override
    public List<TenantDTO> selectListScope(TenantQuery query) {
        return selectList(query);
    }

    @Override
    public List<TenantDTO> selectList(TenantQuery query) {
        // 租户表本身不走租户过滤
        List<TenantPo> list = TenantHelper.ignore(() ->
                tenantMapper.selectList(buildQueryWrapper(query))
        );
        return list.stream().map(po -> {
            TenantDTO dto = new TenantDTO();
            dto.setBase(po);
            return dto;
        }).toList();
    }

    @Override
    public TenantDTO selectById(Long id) {
        TenantPo po = TenantHelper.ignore(() -> tenantMapper.selectById(id));
        if (po == null) {
            return null;
        }
        TenantDTO dto = new TenantDTO();
        dto.setBase(po);
        return dto;
    }

    @Override
    public List<TenantDTO> selectByIds(List<Long> ids) {
        List<TenantPo> list = TenantHelper.ignore(() -> tenantMapper.selectByIds(ids));
        return list.stream().map(po -> {
            TenantDTO dto = new TenantDTO();
            dto.setBase(po);
            return dto;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(TenantDTO dto) {
        TenantPo po = dto.getBase();
        if (po == null) {
            throw new BusinessException("租户信息不能为空");
        }

        // 校验套餐
        if (po.getPackageId() != null) {
            TenantPackagePo pkg = packageMapper.selectById(po.getPackageId());
            if (pkg == null) {
                throw new BusinessException("租户套餐不存在");
            }
        }

        if (po.getStatus() == null) {
            po.setStatus(0);
        }
        if (po.getUserLimit() == null) {
            po.setUserLimit(-1);
        }
        if (po.getAccountLimit() == null) {
            po.setAccountLimit(-1);
        }
        if (po.getIsolationType() == null) {
            po.setIsolationType("logical");
        }

        // 租户表不走租户过滤
        TenantHelper.ignore(() -> tenantMapper.insert(po));

        log.info("创建租户成功: tenantId={}, tenantName={}", po.getId(), po.getTenantName());

        // 调用 han-system 初始化租户基础数据（管理员用户、默认角色、默认部门）
        try {
            TenantInitDto initDTO = TenantInitDto.builder()
                    .tenantId(po.getId())
                    .tenantName(po.getTenantName())
                    .adminUsername(dto.getAdminUsername())
                    .adminPassword(dto.getAdminPassword())
                    .build();

            // 如果有套餐，获取套餐菜单ID传递给初始化接口
            if (po.getPackageId() != null) {
                TenantPackagePo pkg = packageMapper.selectById(po.getPackageId());
                if (pkg != null && pkg.getMenuIds() != null && !pkg.getMenuIds().isBlank()) {
                    try {
                        Set<Long> menuIds = new java.util.HashSet<>(com.han.common.core.util.XuJsonUtil.parseList(pkg.getMenuIds(), Long.class));
                        initDTO.setMenuIds(menuIds);
                    } catch (Exception ex) {
                        log.warn("解析套餐菜单ID失败: packageId={}", po.getPackageId(), ex);
                    }
                }
            }
            systemServiceClient.initTenantData(initDTO);
            log.info("租户[{}]基础数据初始化请求已发送", po.getId());
        } catch (Exception e) {
            log.error("租户[{}]基础数据初始化失败", po.getId(), e);
            throw new BusinessException("租户创建成功但初始化失败: " + e.getMessage());
        }

        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(TenantDTO dto) {
        TenantPo po = dto.getBase();
        if (po == null || po.getId() == null) {
            throw new BusinessException("租户ID不能为空");
        }

        TenantPo existing = TenantHelper.ignore(() -> tenantMapper.selectById(po.getId()));
        if (existing == null) {
            throw new BusinessException("租户不存在");
        }

        tenantConverter.updateFromBase(dto.getBase(), existing);
        TenantHelper.ignore(() -> tenantMapper.updateById(existing));
        return 1;
    }

    @Override
    public int deleteById(Long id) {
        throw new BusinessException("租户不允许删除，只能停用");
    }

    @Override
    public int deleteByIds(List<Long> ids) {
        throw new BusinessException("租户不允许删除，只能停用");
    }

    @Override
    public TenantVO getTenantByDomain(String domain) {
        TenantPo po = TenantHelper.ignore(() ->
                tenantMapper.selectOne(
                        new LambdaQueryWrapper<TenantPo>()
                                .eq(TenantPo::getDomain, domain)
                                .eq(TenantPo::getStatus, 0)
                                .last("LIMIT 1")
                )
        );
        return po != null ? tenantConverter.toVO(po) : null;
    }

    @Override
    public void updateStatus(Long tenantId, Integer status) {
        TenantPo po = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (po == null) {
            throw new BusinessException("租户不存在");
        }
        po.setStatus(status);
        TenantHelper.ignore(() -> tenantMapper.updateById(po));
    }

    @Override
    public boolean checkTenantValid(Long tenantId) {
        TenantPo po = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (po == null) {
            return false;
        }
        if (po.getStatus() != null && po.getStatus() == 1) {
            return false;
        }
        if (po.getExpireTime() != null && po.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    @Override
    public void syncTenantPackage(Long tenantId, Long packageId) {
        TenantPo tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        TenantPackagePo pkg = packageMapper.selectById(packageId);
        if (pkg == null) {
            throw new BusinessException("套餐不存在");
        }

        tenant.setPackageId(packageId);
        TenantHelper.ignore(() -> tenantMapper.updateById(tenant));

        // 获取套餐菜单ID并同步到租户角色
        if (pkg.getMenuIds() != null && !pkg.getMenuIds().isBlank()) {
            try {
                java.util.Set<Long> menuIds = new java.util.HashSet<>(com.han.common.core.util.XuJsonUtil.parseList(pkg.getMenuIds(), Long.class));
                systemServiceClient.syncRoleMenusByTenantId(tenantId, menuIds);
            } catch (Exception ex) {
                log.warn("同步租户[{}]角色菜单失败", tenantId, ex);
            }
        }

        log.info("租户套餐同步完成: tenantId={}, packageId={}", tenantId, packageId);
    }

    @Override
    public List<TenantVO> listAllValidTenants() {
        List<TenantPo> list = TenantHelper.ignore(() ->
                tenantMapper.selectList(
                        new LambdaQueryWrapper<TenantPo>().eq(TenantPo::getStatus, 0)
                )
        );
        return tenantConverter.toVOList(list);
    }

    @Override
    public int countTenantUsers(Long tenantId) {
        try {
            com.han.common.core.domain.R<Integer> result = systemServiceClient.countUsersByTenantId(tenantId);
            return result.getData() != null ? result.getData() : 0;
        } catch (Exception e) {
            log.warn("查询租户[{}]用户数失败，返回0", tenantId, e);
            return 0;
        }
    }

    @Override
    public boolean checkUserLimit(Long tenantId) {
        TenantPo tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null) {
            return false;
        }
        if (tenant.getUserLimit() == null || tenant.getUserLimit() == -1) {
            return true;
        }
        int currentCount = countTenantUsers(tenantId);
        return currentCount < tenant.getUserLimit();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenant(Long tenantId) {
        if (tenantId == null || tenantId == 1L) {
            throw new BusinessException("平台租户不允许删除");
        }

        TenantPo tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }

        // 1. 调用 han-system Inner 接口清理租户业务数据（用户/角色/部门/岗位及关联表）
        try {
            systemServiceClient.cleanupTenantData(tenantId);
        } catch (Exception e) {
            log.error("清理租户[{}]业务数据失败", tenantId, e);
            throw new BusinessException("清理租户业务数据失败: " + e.getMessage());
        }

        // 2. 逻辑删除租户本体
        TenantHelper.ignore(() -> tenantMapper.deleteById(tenantId));

        log.info("租户安全删除完成: tenantId={}, tenantName={}", tenantId, tenant.getTenantName());
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<TenantPo> buildQueryWrapper(TenantQuery query) {
        return new LambdaQueryWrapper<TenantPo>()
                .like(query.getTenantName() != null && !query.getTenantName().isEmpty(),
                        TenantPo::getTenantName, query.getTenantName())
                .like(query.getContactName() != null && !query.getContactName().isEmpty(),
                        TenantPo::getContactName, query.getContactName())
                .eq(query.getStatus() != null, TenantPo::getStatus, query.getStatus())
                .orderByDesc(TenantPo::getCreateTime);
    }
}
