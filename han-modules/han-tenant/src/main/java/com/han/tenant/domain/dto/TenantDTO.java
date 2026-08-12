package com.han.tenant.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
     * <p>WRITE_ONLY：只接收、不序列化。TenantController 的 add/edit 带 @OperLog 且默认 saveParams=true，
     * 不加这个注解会把租户超管的初始明文口令完整写进 sys_oper_log，而读操作日志只需要低得多的权限。
     * 与 SysUserDto.password 保持同一口径。</p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String adminPassword;

    /**
     * 濂楅鍚嶇О
     */
    private String packageName;

    /**
     * 宸蹭娇鐢ㄧ敤鎴锋暟
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
