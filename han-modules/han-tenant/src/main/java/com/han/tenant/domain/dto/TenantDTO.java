package com.han.tenant.domain.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.tenant.domain.po.TenantPo;
import lombok.Data;

import java.io.Serializable;

/**
 * 租户DTO（采用组合模式）
 * 
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>组合Tenant实体，而非继承</li>
 *   <li>使用@JsonUnwrapped自动展开Tenant字段</li>
 *   <li>扩展字段：管理员信息等</li>
 * </ul>
 * 
 * @author han Team
 */
@Data
public class TenantDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 组合Tenant实体（自动展开所有字段）
     */
    @JsonUnwrapped
    private TenantPo base;

    // ==================== 扩展字段 ====================

    /**
     * 管理员用户名(新增租户时创建)
     */
    private String adminUsername;

    /**
     * 管理员密码(新增租户时创建)
     *
     * <p>明文承载，仅用于创建租户时初始化管理员账号。
     * 使用该字段的接口必须标注 {@code @OperLog(saveParams = false)}，否则会被操作日志切面
     * 序列化进 sys_oper_log.oper_param。
     */
    private String adminPassword;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 已使用用户数
     */
    private Integer userCount;

    // ==================== 核心业务字段便捷访问 ====================

    public Long getTenantId() {
        return base != null ? base.getId() : null;
    }

    public void setTenantId(Long tenantId) {
        if (base == null) {
            base = new TenantPo();
        }
        base.setId(tenantId);
    }
}
