package com.han.tenant.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.common.core.constant.CacheConstants;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.tenant.config.HanTenantProperties;
import com.han.tenant.domain.enums.TenantStatus;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.mapper.TenantMapper;
import com.han.tenant.service.support.TenantSessionRevoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 租户过期自动停用定时任务。
 * <p>
 * 每小时检查一次，把已过期且仍处于正常状态的租户自动停用，并吊销其已签发的会话。
 * 多副本部署时用 Redis 互斥锁选主，避免每个实例都跑一遍。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpireTask {

    private static final String LOCK_KEY = CacheConstants.CACHE_PREFIX + "lock:tenant_expire";
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final TenantMapper tenantMapper;
    private final TenantSessionRevoker sessionRevoker;
    private final HanTenantProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 每小时执行一次（整点执行）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void disableExpiredTenants() {
        if (!properties.getExpireTask().isEnabled()) {
            return;
        }

        String lockValue = UUID.randomUUID().toString();
        if (!tryLock(lockValue)) {
            log.debug("过期租户巡检已被其他实例持有，本次跳过");
            return;
        }
        try {
            doDisableExpiredTenants();
        } finally {
            unlock(lockValue);
        }
    }

    private void doDisableExpiredTenants() {
        log.info("开始检查过期租户...");

        List<TenantPo> expiredTenants = TenantHelper.ignore(() -> {
            LocalDateTime now = LocalDateTime.now();
            return tenantMapper.selectList(
                    new LambdaQueryWrapper<TenantPo>()
                            .eq(TenantPo::getStatus, TenantStatus.NORMAL.getCode())
                            .isNotNull(TenantPo::getExpireTime)
                            .le(TenantPo::getExpireTime, now)
            );
        });

        if (expiredTenants.isEmpty()) {
            log.info("没有需要停用的过期租户");
            return;
        }

        // 一条批量 UPDATE 代替逐条更新：既去掉 N 次数据库往返，也消除「查询与更新之间状态被改」的竞态
        int disabled = TenantHelper.ignore(() -> tenantMapper.update(null,
                new LambdaUpdateWrapper<TenantPo>()
                        .eq(TenantPo::getStatus, TenantStatus.NORMAL.getCode())
                        .isNotNull(TenantPo::getExpireTime)
                        .le(TenantPo::getExpireTime, LocalDateTime.now())
                        .set(TenantPo::getStatus, TenantStatus.DISABLED.getCode())
        ));

        for (TenantPo tenant : expiredTenants) {
            log.info("租户[{}]({})已过期，自动停用", tenant.getId(), tenant.getTenantName());
            // 只改 status 拦不住已签发的 Token，必须同步吊销该租户的在线会话
            sessionRevoker.revokeByTenant(tenant.getId());
        }

        log.info("过期租户停用完成，共停用{}个租户", disabled);
    }

    private boolean tryLock(String lockValue) {
        try {
            Duration timeout = Duration.ofSeconds(properties.getExpireTask().getLockSeconds());
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, timeout));
        } catch (Exception e) {
            log.error("获取过期租户巡检锁失败，本次跳过", e);
            return false;
        }
    }

    private void unlock(String lockValue) {
        try {
            redisTemplate.execute(new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                    Collections.singletonList(LOCK_KEY), lockValue);
        } catch (Exception e) {
            log.warn("释放过期租户巡检锁失败，锁将在超时后自动释放", e);
        }
    }
}
