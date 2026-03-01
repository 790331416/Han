package com.han.tenant.domain.query;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.common.core.domain.query.BaseQuery;
import com.han.tenant.domain.po.TenantPo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 租户查询对象（采用组合模式）
 *
 * @author han Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 组合Tenant实体
     */
    @JsonUnwrapped
    private TenantPo base;

    /** 租户名称（模糊查询） */
    @Schema(description = "租户名称")
    private String tenantName;

    /** 联系人（模糊查询） */
    @Schema(description = "联系人")
    private String contactName;

    /** 状态 */
    @Schema(description = "状态(0正常 1停用)")
    private Integer status;
}
