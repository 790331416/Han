package com.han.common.mybatis.config;

import com.han.common.tenant.enums.MissingTenantContextStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 租户配置属性
 */
@Data
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /**
     * 是否启用多租户
     */
    private Boolean enable = true;

    /**
     * 排除的表（不需要租户过滤）
     */
    private List<String> excludes = List.of();

    /**
     * 无租户上下文时的处置策略。
     *
     * <p>默认 {@link MissingTenantContextStrategy#IGNORE}，与历史行为完全一致。
     * 翻转为 FILTER / REJECT 之前必须先完成：内部调用透传租户、定时任务建立租户上下文、
     * 合法的无租户场景全部显式标注。开关支持按服务独立配置，回退只需改配置。</p>
     */
    private MissingTenantContextStrategy missingContext = MissingTenantContextStrategy.IGNORE;

    /**
     * 是否开启无租户上下文观测（只记录日志与计数，不改变任何过滤行为）
     */
    private Boolean observeMissingContext = true;

    /**
     * 同一「操作 + 表 + 调用点」的观测日志最小输出间隔（毫秒），防止日志洪水
     */
    private Long observeLogIntervalMillis = 60_000L;

    /**
     * 共享表配置（租户私有数据 + 平台共享数据的双语义）
     */
    private Shared shared = new Shared();

    /**
     * 共享表配置。
     *
     * <p>命中 {@link #tables} 的表在注入租户条件时会额外放行平台共享数据，生成
     * {@code (tenant_id = 当前租户 OR tenant_id IS NULL)} 这类条件，
     * 从而不必再把这类表整张塞进排除清单或在业务侧手写隔离。</p>
     *
     * <p>默认 {@link #tables} 为空，即不启用共享语义，行为与历史一致。
     * 哪些表归为共享表需要按业务语义逐张确认后再配置。</p>
     */
    @Data
    public static class Shared {

        /**
         * 启用共享语义的表名清单（不区分大小写）
         */
        private List<String> tables = List.of();

        /**
         * 是否把 {@code tenant_id IS NULL} 的行视为平台共享数据
         */
        private Boolean matchNull = true;

        /**
         * 额外视为平台共享数据的 tenant_id 取值（部分 AI 表用 0 表示共享）
         */
        private List<Long> tenantIds = List.of();
    }
}
