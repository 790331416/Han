package com.han.tenant.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户资源配额持久化对象
 */
@Data
@TableName("sys_tenant_quota")
public class TenantQuotaPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配额ID */
    @TableId(type = IdType.AUTO)
    private Long quotaId;

    /** 租户ID */
    private Long tenantId;

    /** 用户数限制(-1不限) */
    private Integer userLimit;

    /** 存储空间限制(字节,-1不限) */
    private Long storageLimit;

    /** API调用次数限制(-1不限) */
    private Long apiLimit;

    /** 已使用用户数 */
    private Integer userUsed;

    /** 已使用存储(字节) */
    private Long storageUsed;

    /** 已使用API调用次数 */
    private Long apiUsed;

    /** 重置周期(monthly/yearly/never) */
    private String resetCycle;

    /** 上次重置时间 */
    private LocalDateTime lastResetTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
