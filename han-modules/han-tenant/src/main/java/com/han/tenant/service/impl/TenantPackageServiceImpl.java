package com.han.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.domain.PageResult;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.mybatis.util.PageHelper;
import com.han.tenant.converter.TenantPackageConverter;
import com.han.tenant.domain.dto.TenantPackageDTO;
import com.han.tenant.domain.po.TenantPackagePo;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.vo.TenantPackageVO;
import com.han.tenant.mapper.TenantPackageMapper;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.service.ITenantPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 租户套餐服务实现
 */
@Service
@RequiredArgsConstructor
public class TenantPackageServiceImpl implements ITenantPackageService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Long PLATFORM_DEFAULT_PACKAGE_ID = 1L;

    private final TenantPackageMapper packageMapper;
    private final TenantMapper tenantMapper;
    private final TenantPackageConverter packageConverter;

    @Override
    public PageResult<TenantPackageVO> listPackages(String packageName, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<TenantPackagePo> wrapper = new LambdaQueryWrapper<TenantPackagePo>()
                .like(packageName != null && !packageName.isEmpty(), TenantPackagePo::getPackageName, packageName)
                .eq(status != null, TenantPackagePo::getStatus, status)
                .orderByDesc(TenantPackagePo::getCreateTime);
        Page<TenantPackagePo> page = packageMapper.selectPage(
                new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize)),
                wrapper
        );
        Map<Long, Integer> tenantCountMap = loadTenantCountMap(page.getRecords());
        return PageHelper.build(page, po -> enrichPackageVo(po, tenantCountMap));
    }

    @Override
    public List<TenantPackageVO> listAllValidPackages() {
        LambdaQueryWrapper<TenantPackagePo> wrapper = new LambdaQueryWrapper<TenantPackagePo>()
                .eq(TenantPackagePo::getStatus, 0);
        List<TenantPackagePo> packages = packageMapper.selectList(wrapper);
        Map<Long, Integer> tenantCountMap = loadTenantCountMap(packages);
        return packages.stream()
                .map(po -> enrichPackageVo(po, tenantCountMap))
                .toList();
    }

    @Override
    public TenantPackageVO getPackageById(Long packageId) {
        TenantPackagePo po = packageMapper.selectById(packageId);
        List<TenantPackagePo> singlePackage = po == null ? List.of() : List.of(po);
        return enrichPackageVo(po, loadTenantCountMap(singlePackage));
    }

    @Override
    public Long createPackage(TenantPackageDTO dto) {
        TenantPackagePo po = packageConverter.toPo(dto);
        packageMapper.insert(po);
        return po.getId();
    }

    @Override
    public void updatePackage(TenantPackageDTO dto) {
        TenantPackagePo po = packageMapper.selectById(dto.getPackageId());
        if (po != null) {
            packageConverter.updatePo(dto, po);
            packageMapper.updateById(po);
        }
    }

    @Override
    public void deletePackage(Long packageId) {
        if (Objects.equals(packageId, PLATFORM_DEFAULT_PACKAGE_ID)) {
            throw new BusinessException("默认套餐不允许删除");
        }
        int tenantCount = countTenantsByPackageId(packageId);
        if (tenantCount > 0) {
            throw new BusinessException("当前套餐下仍有关联租户，无法删除");
        }
        packageMapper.deleteById(packageId);
    }

    @Override
    public void updateStatus(Long packageId, Integer status) {
        TenantPackagePo po = packageMapper.selectById(packageId);
        if (po != null) {
            po.setStatus(status);
            packageMapper.updateById(po);
        }
    }

    @Override
    public Set<Long> getPackageMenuIds(Long packageId) {
        TenantPackagePo po = packageMapper.selectById(packageId);
        if (po != null) {
            return packageConverter.toVO(po).getMenuIds();
        }
        return Set.of();
    }

    @Override
    public void updatePackageMenus(Long packageId, Set<Long> menuIds) {
        TenantPackagePo po = packageMapper.selectById(packageId);
        if (po != null) {
            po.setMenuIds(packageConverter.setToJson(menuIds));
            packageMapper.updateById(po);
        }
    }

    private TenantPackageVO enrichPackageVo(TenantPackagePo po, Map<Long, Integer> tenantCountMap) {
        if (po == null) {
            return null;
        }
        TenantPackageVO vo = packageConverter.toVO(po);
        vo.setTenantCount(tenantCountMap.getOrDefault(po.getId(), 0));
        return vo;
    }

    private Map<Long, Integer> loadTenantCountMap(List<TenantPackagePo> packages) {
        List<Long> packageIds = packages.stream()
                .filter(Objects::nonNull)
                .map(TenantPackagePo::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (packageIds.isEmpty()) {
            return Map.of();
        }

        List<TenantPo> tenants = TenantHelper.ignore(() -> tenantMapper.selectList(
                new LambdaQueryWrapper<TenantPo>()
                        .in(TenantPo::getPackageId, packageIds)
        ));
        Map<Long, Integer> tenantCountMap = new HashMap<>();
        for (TenantPo tenant : tenants) {
            if (tenant.getPackageId() == null) {
                continue;
            }
            tenantCountMap.merge(tenant.getPackageId(), 1, Integer::sum);
        }
        return tenantCountMap;
    }

    private int countTenantsByPackageId(Long packageId) {
        if (packageId == null) {
            return 0;
        }
        Long count = TenantHelper.ignore(() -> tenantMapper.selectCount(
                new LambdaQueryWrapper<TenantPo>()
                        .eq(TenantPo::getPackageId, packageId)
        ));
        return count == null ? 0 : count.intValue();
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < DEFAULT_PAGE_NUM ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
