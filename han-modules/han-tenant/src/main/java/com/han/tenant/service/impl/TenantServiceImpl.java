package com.han.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.api.system.SystemClient;
import com.han.api.system.domain.TenantInitDto;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.mybatis.util.PageHelper;
import com.han.tenant.converter.TenantApiConverter;
import com.han.tenant.converter.TenantConverter;
import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.po.TenantPackagePo;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.query.TenantQuery;
import com.han.tenant.domain.vo.TenantOptionVO;
import com.han.tenant.domain.vo.TenantVO;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.mapper.TenantPackageMapper;
import com.han.tenant.service.ITenantService;
import com.han.tenant.service.support.TenantRoleMenuSynchronizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 租户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements ITenantService {

    private static final int STATUS_ENABLED = 0;
    private static final int DEFAULT_USER_LIMIT = -1;
    private static final int DEFAULT_ACCOUNT_LIMIT = -1;
    private static final String DEFAULT_ISOLATION_TYPE = "logical";
    private static final long PLATFORM_TENANT_ID = 1L;

    private final TenantMapper tenantMapper;
    private final TenantPackageMapper packageMapper;
    private final TenantConverter tenantConverter;
    private final TenantApiConverter tenantApiConverter;
    private final TenantRoleMenuSynchronizer roleMenuSynchronizer;
    private final SystemClient systemClient;

    @Override
    public PageResult<TenantDTO> selectPage(TenantQuery query) {
        TenantQuery safeQuery = query != null ? query : new TenantQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<TenantPo> page = TenantHelper.ignore(() ->
                tenantMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery))
        );
        Map<Long, String> packageNameMap = loadPackageNameMap(page.getRecords());
        Map<Long, Integer> userCountMap = loadUserCountMap(page.getRecords());
        return PageHelper.build(page, po -> enrichTenantDto(po, packageNameMap, userCountMap));
    }

    @Override
    public List<TenantDTO> selectListScope(TenantQuery query) {
        return selectList(query);
    }

    @Override
    public List<TenantDTO> selectList(TenantQuery query) {
        List<TenantPo> list = TenantHelper.ignore(() -> tenantMapper.selectList(buildQueryWrapper(query)));
        Map<Long, String> packageNameMap = loadPackageNameMap(list);
        Map<Long, Integer> userCountMap = loadUserCountMap(list);
        return list.stream()
                .map(po -> enrichTenantDto(po, packageNameMap, userCountMap))
                .toList();
    }

    @Override
    public TenantDTO selectById(Long id) {
        TenantPo po = TenantHelper.ignore(() -> tenantMapper.selectById(id));
        List<TenantPo> singleTenant = wrapSingle(po);
        return enrichTenantDto(po, loadPackageNameMap(singleTenant), loadUserCountMap(singleTenant));
    }

    @Override
    public List<TenantDTO> selectByIds(List<Long> ids) {
        List<TenantPo> list = TenantHelper.ignore(() -> tenantMapper.selectByIds(ids));
        Map<Long, String> packageNameMap = loadPackageNameMap(list);
        Map<Long, Integer> userCountMap = loadUserCountMap(list);
        return list.stream()
                .map(po -> enrichTenantDto(po, packageNameMap, userCountMap))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(TenantDTO dto) {
        TenantPo po = requireTenantBody(dto);
        TenantPackagePo pkg = requireEnabledPackage(po.getPackageId());
        applyTenantDefaults(po);

        TenantHelper.ignore(() -> tenantMapper.insert(po));
        log.info("创建租户成功: tenantId={}, tenantName={}", po.getId(), po.getTenantName());

        try {
            TenantInitDto initDTO = TenantInitDto.builder()
                    .tenantId(po.getId())
                    .tenantName(po.getTenantName())
                    .adminUsername(dto.getAdminUsername())
                    .adminPassword(dto.getAdminPassword())
                    .build();
            initDTO.setMenuIds(requireMenuIds(pkg));
            // R.fail 不抛异常，必须显式检查返回值，否则初始化失败时租户事务照常提交产生空壳租户
            R<Void> initResult = systemClient.initTenantData(initDTO);
            if (initResult == null || initResult.isFail()) {
                String reason = initResult != null && initResult.getMsg() != null ? initResult.getMsg() : "初始化服务无响应";
                throw new BusinessException(reason);
            }
            log.info("租户[{}]基础数据初始化完成", po.getId());
        } catch (BusinessException e) {
            log.error("租户[{}]基础数据初始化失败: {}", po.getId(), e.getMessage());
            throw new BusinessException("租户初始化失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("租户[{}]基础数据初始化失败", po.getId(), e);
            throw new BusinessException("租户初始化失败: " + e.getMessage());
        }

        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(TenantDTO dto) {
        TenantPo source = requireTenantBody(dto);
        if (source.getId() == null) {
            throw new BusinessException("租户ID不能为空");
        }

        TenantPo existing = TenantHelper.ignore(() -> tenantMapper.selectById(source.getId()));
        if (existing == null) {
            throw new BusinessException("租户不存在");
        }

        Long previousPackageId = existing.getPackageId();
        TenantPackagePo nextPackage = findEnabledPackage(source.getPackageId());

        tenantConverter.updateFromBase(source, existing);
        applyTenantDefaults(existing);
        TenantHelper.ignore(() -> tenantMapper.updateById(existing));

        if (!Objects.equals(previousPackageId, existing.getPackageId())) {
            syncTenantRoleMenus(existing.getId(), nextPackage);
        }
        return 1;
    }

    @Override
    public int deleteById(Long id) {
        throw new BusinessException("租户不允许直接删除，请走安全删除流程");
    }

    @Override
    public int deleteByIds(List<Long> ids) {
        throw new BusinessException("租户不允许批量删除，请走安全删除流程");
    }

    @Override
    public TenantVO getTenantByDomain(String domain) {
        TenantPo po = findEnabledByDomain(domain);
        List<TenantPo> singleTenant = wrapSingle(po);
        return enrichTenantVo(po, loadPackageNameMap(singleTenant), loadUserCountMap(singleTenant));
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
        if (po.getStatus() != null && po.getStatus() != STATUS_ENABLED) {
            return false;
        }
        return po.getExpireTime() == null || !po.getExpireTime().isBefore(LocalDateTime.now());
    }

    @Override
    public void syncTenantPackage(Long tenantId, Long packageId) {
        TenantPo tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }

        TenantPackagePo pkg = requireEnabledPackage(packageId);
        tenant.setPackageId(packageId);
        TenantHelper.ignore(() -> tenantMapper.updateById(tenant));
        syncTenantRoleMenus(tenantId, pkg);
        log.info("租户套餐同步完成: tenantId={}, packageId={}", tenantId, packageId);
    }

    @Override
    public List<TenantVO> listAllValidTenants() {
        List<TenantPo> list = selectEnabledTenants();
        Map<Long, String> packageNameMap = loadPackageNameMap(list);
        Map<Long, Integer> userCountMap = loadUserCountMap(list);
        return list.stream()
                .map(po -> enrichTenantVo(po, packageNameMap, userCountMap))
                .toList();
    }

    @Override
    public List<TenantOptionVO> listTenantOptions() {
        // 匿名场景：不加载套餐名与用户数，既避免暴露经营信息，也避免按租户逐个远程调用
        return tenantConverter.toOptionVOList(selectEnabledTenants());
    }

    @Override
    public TenantOptionVO getTenantOptionByDomain(String domain) {
        return tenantConverter.toOptionVO(findEnabledByDomain(domain));
    }

    @Override
    public com.han.api.tenant.domain.TenantVO getApiTenantById(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        TenantPo po = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        return tenantApiConverter.toApiVO(po);
    }

    @Override
    public List<com.han.api.tenant.domain.TenantVO> listApiValidTenants() {
        // 内部调用方（han-auth 的租户下拉与租户名解析）只需要租户主数据，不触发用户数远程调用
        return tenantApiConverter.toApiVOList(selectEnabledTenants());
    }

    @Override
    public int countTenantUsers(Long tenantId) {
        try {
            R<Integer> result = systemClient.countUsersByTenantId(tenantId);
            if (result == null || result.getData() == null) {
                return 0;
            }
            return result.getData();
        } catch (Exception e) {
            log.warn("查询租户[{}]用户数失败，按 0 返回", tenantId, e);
            return 0;
        }
    }

    @Override
    public boolean checkUserLimit(Long tenantId) {
        TenantPo tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null) {
            return false;
        }
        if (tenant.getUserLimit() == null || tenant.getUserLimit() == DEFAULT_USER_LIMIT) {
            return true;
        }
        return countTenantUsers(tenantId) < tenant.getUserLimit();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenant(Long tenantId) {
        if (tenantId == null || tenantId == PLATFORM_TENANT_ID) {
            throw new BusinessException("平台租户不允许删除");
        }

        TenantPo tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }

        try {
            systemClient.cleanupTenantData(tenantId);
        } catch (Exception e) {
            log.error("清理租户[{}]业务数据失败", tenantId, e);
            throw new BusinessException("清理租户业务数据失败: " + e.getMessage());
        }

        TenantHelper.ignore(() -> tenantMapper.deleteById(tenantId));
        log.info("租户安全删除完成: tenantId={}, tenantName={}", tenantId, tenant.getTenantName());
    }

    private List<TenantPo> selectEnabledTenants() {
        return TenantHelper.ignore(() ->
                tenantMapper.selectList(new LambdaQueryWrapper<TenantPo>()
                        .eq(TenantPo::getStatus, STATUS_ENABLED)
                        .orderByDesc(TenantPo::getCreateTime))
        );
    }

    private TenantPo findEnabledByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return TenantHelper.ignore(() ->
                tenantMapper.selectOne(new LambdaQueryWrapper<TenantPo>()
                        .eq(TenantPo::getDomain, domain)
                        .eq(TenantPo::getStatus, STATUS_ENABLED)
                        .last("LIMIT 1"))
        );
    }

    private void applyTenantDefaults(TenantPo po) {
        if (po.getStatus() == null) {
            po.setStatus(STATUS_ENABLED);
        }
        if (po.getUserLimit() == null) {
            po.setUserLimit(DEFAULT_USER_LIMIT);
        }
        if (po.getAccountLimit() == null) {
            po.setAccountLimit(DEFAULT_ACCOUNT_LIMIT);
        }
        if (po.getIsolationType() == null || po.getIsolationType().isBlank()) {
            po.setIsolationType(DEFAULT_ISOLATION_TYPE);
        }
    }

    private TenantPo requireTenantBody(TenantDTO dto) {
        TenantPo po = dto != null ? dto.getBase() : null;
        if (po == null) {
            throw new BusinessException("租户信息不能为空");
        }
        return po;
    }

    /**
     * 解析套餐，套餐必须存在且启用。
     * <p>
     * 原实现在 packageId 为空时直接返回 null，租户会被创建成「一个菜单都没有」的空白后台，
     * 与「租户初始化失败要响亮失败」的既定口径相悖，这里改为拒绝。
     */
    private TenantPackagePo requireEnabledPackage(Long packageId) {
        if (packageId == null) {
            throw new BusinessException("租户套餐不能为空");
        }
        return findEnabledPackage(packageId);
    }

    /**
     * 解析套餐；packageId 为空表示本次不涉及套餐变更，返回 null。
     */
    private TenantPackagePo findEnabledPackage(Long packageId) {
        if (packageId == null) {
            return null;
        }
        TenantPackagePo pkg = packageMapper.selectById(packageId);
        if (pkg == null) {
            throw new BusinessException("租户套餐不存在");
        }
        if (pkg.getStatus() != null && pkg.getStatus() != STATUS_ENABLED) {
            throw new BusinessException("租户套餐已停用");
        }
        return pkg;
    }

    private void syncTenantRoleMenus(Long tenantId, TenantPackagePo pkg) {
        roleMenuSynchronizer.sync(tenantId, requireMenuIds(pkg));
    }

    /**
     * 解析套餐菜单，并要求结果非空。
     * <p>
     * 原实现在 JSON 解析失败时只打 warn 并返回空集，而空集会经 syncRoleMenus 把租户下
     * 所有角色的菜单清空（远端是先删后插），属于静默的破坏性降级，这里改为响亮失败。
     */
    private Set<Long> requireMenuIds(TenantPackagePo pkg) {
        if (pkg == null) {
            throw new BusinessException("租户套餐不存在，无法确定菜单范围");
        }
        if (pkg.getMenuIds() == null || pkg.getMenuIds().isBlank()) {
            throw new BusinessException("租户套餐[" + pkg.getId() + "]未配置菜单，拒绝下发");
        }
        Set<Long> menuIds;
        try {
            menuIds = new HashSet<>(XuJsonUtil.parseList(pkg.getMenuIds(), Long.class));
        } catch (Exception ex) {
            log.error("解析租户套餐菜单失败: packageId={}", pkg.getId(), ex);
            throw new BusinessException("租户套餐[" + pkg.getId() + "]菜单数据已损坏，拒绝下发");
        }
        if (menuIds.isEmpty()) {
            throw new BusinessException("租户套餐[" + pkg.getId() + "]未配置菜单，拒绝下发");
        }
        return menuIds;
    }

    private TenantDTO enrichTenantDto(TenantPo po, Map<Long, String> packageNameMap, Map<Long, Integer> userCountMap) {
        if (po == null) {
            return null;
        }
        TenantDTO dto = new TenantDTO();
        dto.setBase(po);
        dto.setPackageName(packageNameMap.get(po.getPackageId()));
        dto.setUserCount(userCountMap.getOrDefault(po.getId(), 0));
        return dto;
    }

    private TenantVO enrichTenantVo(TenantPo po, Map<Long, String> packageNameMap, Map<Long, Integer> userCountMap) {
        if (po == null) {
            return null;
        }
        TenantVO vo = tenantConverter.toVO(po);
        vo.setPackageName(packageNameMap.get(po.getPackageId()));
        vo.setUserCount(userCountMap.getOrDefault(po.getId(), 0));
        return vo;
    }

    private Map<Long, String> loadPackageNameMap(List<TenantPo> tenants) {
        List<Long> packageIds = tenants.stream()
                .filter(Objects::nonNull)
                .map(TenantPo::getPackageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (packageIds.isEmpty()) {
            return Map.of();
        }
        return packageMapper.selectByIds(packageIds).stream()
                .collect(HashMap::new, (map, pkg) -> map.put(pkg.getId(), pkg.getPackageName()), HashMap::putAll);
    }

    private Map<Long, Integer> loadUserCountMap(List<TenantPo> tenants) {
        Map<Long, Integer> result = new HashMap<>();
        tenants.stream()
                .filter(Objects::nonNull)
                .map(TenantPo::getId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(tenantId -> result.put(tenantId, countTenantUsers(tenantId)));
        return result;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private LambdaQueryWrapper<TenantPo> buildQueryWrapper(TenantQuery query) {
        TenantQuery safeQuery = query != null ? query : new TenantQuery();
        return new LambdaQueryWrapper<TenantPo>()
                .like(safeQuery.getTenantName() != null && !safeQuery.getTenantName().isBlank(),
                        TenantPo::getTenantName, safeQuery.getTenantName())
                .like(safeQuery.getContactName() != null && !safeQuery.getContactName().isBlank(),
                        TenantPo::getContactName, safeQuery.getContactName())
                .eq(safeQuery.getStatus() != null, TenantPo::getStatus, safeQuery.getStatus())
                .orderByDesc(TenantPo::getCreateTime);
    }

    private List<TenantPo> wrapSingle(TenantPo po) {
        return po == null ? List.of() : List.of(po);
    }
}
