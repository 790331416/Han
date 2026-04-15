package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租户账单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_tenant_bill")
public class SysTenantBillPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long subscriptionId;

    /** subscribe/renew/upgrade */
    private String billType;

    private BigDecimal amount;

    /** 0待支付 1已支付 2已取消 */
    private Integer status;

    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
}
