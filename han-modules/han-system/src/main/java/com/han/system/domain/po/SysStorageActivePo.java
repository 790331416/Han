package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 每个租户当前默认写入对象存储的活动指针。 */
@Data
@TableName("sys_storage_active")
public class SysStorageActivePo {

    @TableId(value = "tenant_id", type = IdType.INPUT)
    private Long tenantId;

    private Long ossConfigId;

    private Integer version;

    private Long updateBy;

    private LocalDateTime updateTime;
}
