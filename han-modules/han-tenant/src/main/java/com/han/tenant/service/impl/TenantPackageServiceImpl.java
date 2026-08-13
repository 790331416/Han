package com.han.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.domain.PageResult;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.mybatis.util.PageHelper;
import com.han.tenant.config.HanTenantProperties;
import com.han.tenant.converter.TenantPackageConverter;
import com.han.tenant.domain.dto.TenantPackageDTO;
import com.han.tenant.domain.po.TenantPackagePo;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.vo.TenantPackageVO;
import com.han.tenant.mapper.TenantPackageMapper;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.service.ITenantPackageService;
import com.han.tenant.service.support.TenantRoleMenuSynchronizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 租户套餐服务实现
 */
@Slf4j
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
    private final TenantRoleMenuSynchronizer roleMenuSynchronizer;
    private final HanTenantProperties tenantProperties;

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
        TenantPackagePo po = requirePackage(packageId);
        return enrichPackageVo(po, loadTenantCountMap(List.of(po)));
    }

    @Override
    public Long createPackage(TenantPackageDTO dto) {
        TenantPackagePo po = packageConverter.toPo(dto);
        packageMapper.insert(po);
        return po.getId();
    }

    @Override
    public void updatePackage(TenantPackageDTO dto) {
        if (dto == null || dto.getPackageId() == null) {
            throw new BusinessException("套餐ID不能为空");
        }
        TenantPackagePo po = requirePackage(dto.getPackageId());
        Set<Long> previousMenuIds = packageConverter.jsonToSet(po.getMenuIds());
        packageConverter.updatePo(dto, po);
        packageMapper.updateById(po);
        afterMenuChanged(po, previousMenuIds);
    }

    @Override
    public void deletePackage(Long packageId) {
        if (Objects.equals(packageId, PLATFORM_DEFAULT_PACKAGE_ID)) {
            throw new BusinessException("默认套餐不允许删除");
        }
        requirePackage(packageId);
        int tenantCount = countTenantsByPackageId(packageId);
        if (tenantCount > 0) {
            throw new BusinessException("当前套餐下仍有关联租户，无法删除");
        }
        packageMapper.deleteById(packageId);
    }

    @Override
    public void updateStatus(Long packageId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("套餐状态只能是 0（正常）或 1（停用）");
        }
        TenantPackagePo po = requirePackage(packageId);
        po.setStatus(status);
        packageMapper.updateById(po);
    }

    @Override
    public Set<Long> getPackageMenuIds(Long packageId) {
        return packageConverter.toVO(requirePackage(packageId)).getMenuIds();
    }

    @Override
    public void updatePackageMenus(Long packageId, Set<Long> menuIds) {
        TenantPackagePo po = requirePackage(packageId);
        Set<Long> previousMenuIds = packageConverter.jsonToSet(po.getMenuIds());
        po.setMenuIds(packageConverter.setToJson(menuIds));
        packageMapper.updateById(po);
        afterMenuChanged(po, previousMenuIds);
    }

    @Override
    public int resyncPackageToTenants(Long packageId) {
        TenantPackagePo po = requirePackage(packageId);
        Set<Long> menuIds = packageConverter.jsonToSet(po.getMenuIds());
        return resync(po.getId(), menuIds);
    }

    /**
     * 套餐菜单变更后的存量租户处置。
     * <p>
     * 菜单被裁剪时，存量租户仍然持有已移出套餐的菜单，属于「降级不生效」的越权面，需要回灌；
     * 菜单只是新增时不自动下发——远端 syncRoleMenus 目前是「所有角色覆盖为套餐全集」，
     * 自动下发会把租户内的权限分级一并抹平。默认只记录待办、由管理员显式触发回灌，
     * 打开 {@code han.tenant.package-sync.auto-resync-on-menu-shrink} 后才自动执行。
     */
    private void afterMenuChanged(TenantPackagePo po, Set<Long> previousMenuIds) {
        Set<Long> currentMenuIds = packageConverter.jsonToSet(po.getMenuIds());
        Set<Long> removed = new LinkedHashSet<>(previousMenuIds);
        removed.removeAll(currentMenuIds);
        if (removed.isEmpty()) {
            return;
        }

        int affected = countTenantsByPackageId(po.getId());
        if (affected == 0) {
            return;
        }
        if (!tenantProperties.getPackageSync().isAutoResyncOnMenuShrink()) {
            log.warn("套餐[{}]移除了 {} 个菜单，但有 {} 个存量租户未回灌，移除的菜单在这些租户内仍然可用；"
                            + "请调用 /tenant/package/resync/{} 显式下发",
                    po.getId(), removed.size(), affected, po.getId());
            return;
        }
        resync(po.getId(), currentMenuIds);
    }

    private int resync(Long packageId, Set<Long> menuIds) {
        List<TenantPo> tenants = selectTenantsByPackageId(packageId);
        if (tenants.isEmpty()) {
            return 0;
        }

        List<Long> failed = new ArrayList<>();
        int succeeded = 0;
        for (TenantPo tenant : tenants) {
            try {
                roleMenuSynchronizer.sync(tenant.getId(), menuIds);
                succeeded++;
            } catch (Exception ex) {
                log.error("回灌套餐[{}]到租户[{}]失败", packageId, tenant.getId(), ex);
                failed.add(tenant.getId());
            }
        }
        if (!failed.isEmpty()) {
            throw new BusinessException("套餐菜单回灌部分失败，成功 " + succeeded
                    + " 个，失败租户: " + failed);
        }
        log.info("套餐[{}]菜单已回灌到 {} 个租户", packageId, succeeded);
        return succeeded;
    }

    private TenantPackagePo requirePackage(Long packageId) {
        if (packageId == null) {
            throw new BusinessException("套餐ID不能为空");
        }
        TenantPackagePo po = packageMapper.selectById(packageId);
        if (po == null) {
            throw new BusinessException("租户套餐不存在");
        }
        return po;
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

    private List<TenantPo> selectTenantsByPackageId(Long packageId) {
        if (packageId == null) {
            return List.of();
        }
        return TenantHelper.ignore(() -> tenantMapper.selectList(
                new LambdaQueryWrapper<TenantPo>()
                        .eq(TenantPo::getPackageId, packageId)
        ));
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
