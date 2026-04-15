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
 * 租户订阅记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_tenant_subscription")
public class SysTenantSubscriptionPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long packageId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 0正常 1已过期 2已取消 */
    private Integer status;

    private BigDecimal amount;
    private String paymentMethod;
    private String paymentNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
