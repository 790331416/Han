package com.han.tenant.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户过期自动停用定时任务
 * <p>
 * 每小时检查一次，将已过期且仍处于正常状态的租户自动停用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpireTask {

    private final TenantMapper tenantMapper;

    /**
     * 每小时执行一次（整点执行）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void disableExpiredTenants() {
        log.info("开始检查过期租户...");

        TenantHelper.ignore(() -> {
            LocalDateTime now = LocalDateTime.now();

            // 查询已过期且仍为正常状态的租户
            List<TenantPo> expiredTenants = tenantMapper.selectList(
                    new LambdaQueryWrapper<TenantPo>()
                            .eq(TenantPo::getStatus, 0)
                            .isNotNull(TenantPo::getExpireTime)
                            .le(TenantPo::getExpireTime, now)
            );

            if (expiredTenants.isEmpty()) {
                log.info("没有需要停用的过期租户");
                return;
            }

            for (TenantPo tenant : expiredTenants) {
                tenantMapper.update(null,
                        new LambdaUpdateWrapper<TenantPo>()
                                .eq(TenantPo::getId, tenant.getId())
                                .set(TenantPo::getStatus, 1)
                );
                log.info("租户[{}]({})已过期，自动停用", tenant.getId(), tenant.getTenantName());
            }

            log.info("过期租户停用完成，共停用{}个租户", expiredTenants.size());
        });
    }
}
