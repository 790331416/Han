package com.han.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 租户服务运行参数。
 * <p>
 * 平台租户 ID、平台边界开关、套餐回灌策略、会话吊销策略原先分散硬编码在各实现类里，统一收敛到本配置。
 * 前缀取 {@code han.tenant}，与 han-common-mybatis 的 {@code tenant.*}（租户插件开关与排除表）区分。
 */
@Data
@Component
@ConfigurationProperties(prefix = "han.tenant")
public class HanTenantProperties {

    /** 平台租户 ID：只有该租户（或超管）可以管理其他租户 */
    private Long platformTenantId = 1L;

    /** 是否强制平台租户边界；出现误伤时可由运维临时关闭，默认开启 */
    private boolean enforcePlatformBoundary = true;

    private final PackageSync packageSync = new PackageSync();

    private final Session session = new Session();

    private final ExpireTask expireTask = new ExpireTask();

    /**
     * 套餐下发策略。
     */
    @Data
    public static class PackageSync {

        /**
         * 套餐菜单被裁剪后，是否自动回灌到已订阅该套餐的存量租户。
         * <p>
         * 默认关闭。开启的前提是 han-system 的 {@code /inner/system/tenant/syncRoleMenus}
         * 已由「覆盖为套餐全集」改成「与套餐取交集」（工单 S-68 的另一半）；
         * 在此之前自动回灌会把租户内所有角色的菜单拉平，且旧关联被物理删除不可恢复。
         * 关闭期间由管理员显式调用 {@code POST /tenant/package/resync/{packageId}} 下发。
         */
        private boolean autoResyncOnMenuShrink = false;
    }

    /**
     * 会话吊销策略。
     */
    @Data
    public static class Session {

        /** 租户停用 / 过期 / 删除时，是否立即吊销该租户下已签发的登录会话 */
        private boolean revokeOnDisable = true;

        /** 单次吊销扫描的 Redis 游标批量大小 */
        private int scanBatchSize = 500;
    }

    /**
     * 过期租户巡检任务参数。
     */
    @Data
    public static class ExpireTask {

        /** 是否启用过期租户自动停用巡检 */
        private boolean enabled = true;

        /** 多实例互斥锁的持有时长（秒） */
        private long lockSeconds = 300L;
    }
}
