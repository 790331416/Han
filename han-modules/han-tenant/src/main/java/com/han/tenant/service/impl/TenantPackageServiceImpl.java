package com.han.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.tenant.converter.TenantPackageConverter;
import com.han.tenant.domain.dto.TenantPackageDTO;
import com.han.tenant.domain.po.TenantPackagePo;
import com.han.tenant.domain.vo.TenantPackageVO;
import com.han.tenant.mapper.TenantPackageMapper;
import com.han.tenant.service.ITenantPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 租户套餐服务实现
 */
@Service
@RequiredArgsConstructor
public class TenantPackageServiceImpl implements ITenantPackageService {

    private final TenantPackageMapper packageMapper;
    private final TenantPackageConverter packageConverter;

    @Override
    public PageResult<TenantPackageVO> listPackages(String packageName, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<TenantPackagePo> wrapper = new LambdaQueryWrapper<TenantPackagePo>()
                .like(packageName != null && !packageName.isEmpty(), TenantPackagePo::getPackageName, packageName)
                .eq(status != null, TenantPackagePo::getStatus, status)
                .orderByDesc(TenantPackagePo::getCreateTime);
        Page<TenantPackagePo> page = packageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(packageConverter.toVOList(page.getRecords()), page.getTotal());
    }

    @Override
    public List<TenantPackageVO> listAllValidPackages() {
        LambdaQueryWrapper<TenantPackagePo> wrapper = new LambdaQueryWrapper<TenantPackagePo>()
                .eq(TenantPackagePo::getStatus, 0);
        return packageConverter.toVOList(packageMapper.selectList(wrapper));
    }

    @Override
    public TenantPackageVO getPackageById(Long packageId) {
        TenantPackagePo po = packageMapper.selectById(packageId);
        return po != null ? packageConverter.toVO(po) : null;
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
}
