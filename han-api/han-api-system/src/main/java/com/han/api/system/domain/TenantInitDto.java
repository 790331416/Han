package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户初始化参数（创建租户时由 han-tenant 调用 han-system 初始化基础数据）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInitDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 租户名称（用作默认部门名称） */
    private String tenantName;

    /** 管理员用户名 */
    private String adminUsername;

    /**
     * 管理员密码（明文，由 han-system 加密存储）。
     *
     * <p><b>风险已知、暂未消除</b>：这份明文经 {@code POST /inner/system/tenant/init} 传输，
     * 而服务间是纯 HTTP（{@code HttpClientFactoryBean} 的 baseUrl 是 {@code http://<serviceName>}），
     * 任何链路抓包、请求体日志、APM body 采样都会拿到租户管理员的初始密码。
     * 目标形态是由 han-system 生成随机初始密码并回一次性重置凭据；若必须由调用方指定，
     * 则用平台公钥加密后传输（han-auth 已有 {@code /auth/publicKey} 机制可复用）。
     * 已排除出 {@code toString()}，先把「误打日志」这一半堵上。
     */
    @ToString.Exclude
    private String adminPassword;

    /** 管理员昵称 */
    private String adminNickname;

    /** 管理员手机号 */
    private String adminPhone;

    /** 套餐菜单ID列表（可选，用于初始化角色菜单） */
    private java.util.Set<Long> menuIds;
}
