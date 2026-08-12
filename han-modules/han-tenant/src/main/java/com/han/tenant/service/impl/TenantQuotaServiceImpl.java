package com.han.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.po.TenantQuotaPo;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.mapper.TenantQuotaMapper;
import com.han.tenant.service.ITenantQuotaService;
import com.han.tenant.service.ITenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 租户资源配额服务实现。
 * <p>
 * {@code sys_tenant_quota} 的 {@code tenant_id} 表达的是「这条配额属于哪个被管理的租户」，
 * 而不是「数据归属于当前登录租户」。若不显式忽略租户过滤，插件会再追加一个
 * {@code AND tenant_id = 当前登录租户}，与方法入参的 {@code tenant_id = ?} 互斥：
 * 平台管理员查任何其他租户的配额都恒为空，保存时又因查不到而永远走 insert，
 * 第二次保存即撞 {@code idx_tenant_quota_tenant} 唯一索引报 500。
 * 因此本类所有读写都在 {@link TenantHelper#ignore} 内执行，并强制带上入参租户条件。
 * <p>
 * 调用方必须先通过平台租户断言（见 {@code PlatformTenantGuard}），本类不做身份校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaServiceImpl implements ITenantQuotaService {

    /** 不限 */
    private static final int UNLIMITED = -1;
    private static final String DEFAULT_RESET_CYCLE = "monthly";

    private final TenantQuotaMapper quotaMapper;
    private final TenantMapper tenantMapper;
    private final ITenantService tenantService;

    @Override
    public TenantQuotaPo getByTenantId(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return TenantHelper.ignore(() -> selectByTenantId(tenantId));
    }

    @Override
    public TenantQuotaPo getOrDefault(Long tenantId) {
        requireTenantExists(tenantId);
        TenantQuotaPo quota = getByTenantId(tenantId);
        if (quota == null) {
            // 走到这里只可能是「该租户确实没配过配额」：租户存在性已经校验过，
            // 查询也已忽略租户过滤，不再有「被过滤掉却当成未配置」的歧义。
            quota = buildUnlimited(tenantId);
        }
        quota.setUserUsed(tenantService.countTenantUsers(tenantId));
        return quota;
    }

    @Override
    public void saveOrUpdate(TenantQuotaPo quota) {
        if (quota == null || quota.getTenantId() == null) {
            throw new BusinessException("配额所属租户不能为空");
        }
        Long tenantId = quota.getTenantId();
        requireTenantExists(tenantId);
        normalizeLimits(quota);

        TenantHelper.ignore(() -> {
            TenantQuotaPo existing = selectByTenantId(tenantId);
            if (existing != null) {
                quota.setQuotaId(existing.getQuotaId());
                quotaMapper.updateById(quota);
                return;
            }
            // 入参 quotaId 由服务端决定，不接受调用方指定，避免改到别的租户的配额行
            quota.setQuotaId(null);
            try {
                quotaMapper.insert(quota);
            } catch (DuplicateKeyException e) {
                // 并发下两个请求同时判定为「不存在」时，唯一索引会挡住后写的一方，退化为更新
                TenantQuotaPo current = selectByTenantId(tenantId);
                if (current == null) {
                    throw e;
                }
                quota.setQuotaId(current.getQuotaId());
                quotaMapper.updateById(quota);
            }
        });
    }

    private TenantQuotaPo selectByTenantId(Long tenantId) {
        return quotaMapper.selectOne(
                new LambdaQueryWrapper<TenantQuotaPo>()
                        .eq(TenantQuotaPo::getTenantId, tenantId)
        );
    }

    private void requireTenantExists(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException("租户ID不能为空");
        }
        TenantPo tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
    }

    private TenantQuotaPo buildUnlimited(Long tenantId) {
        TenantQuotaPo quota = new TenantQuotaPo();
        quota.setTenantId(tenantId);
        quota.setUserLimit(UNLIMITED);
        quota.setStorageLimit((long) UNLIMITED);
        quota.setApiLimit((long) UNLIMITED);
        quota.setUserUsed(0);
        quota.setStorageUsed(0L);
        quota.setApiUsed(0L);
        quota.setResetCycle(DEFAULT_RESET_CYCLE);
        return quota;
    }

    private void normalizeLimits(TenantQuotaPo quota) {
        if (quota.getUserLimit() == null) {
            quota.setUserLimit(UNLIMITED);
        }
        if (quota.getStorageLimit() == null) {
            quota.setStorageLimit((long) UNLIMITED);
        }
        if (quota.getApiLimit() == null) {
            quota.setApiLimit((long) UNLIMITED);
        }
        if (quota.getUserLimit() < UNLIMITED || quota.getStorageLimit() < UNLIMITED || quota.getApiLimit() < UNLIMITED) {
            throw new BusinessException("配额上限只能为 -1（不限）或非负数");
        }
    }
}
