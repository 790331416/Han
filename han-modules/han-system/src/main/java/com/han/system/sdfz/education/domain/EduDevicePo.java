package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_device")
public class EduDevicePo extends BizEntity {
    private Long schoolId;
    private Long roomId;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    /** 设备应用类型字典编码，逗号分隔，所有值都必须隶属当前设备类型。 */
    private String applicationTypes;
    private String model;
    private String serialNumber;
    private String sourceSystem;
    private String externalId;
    private String assetStatus;
    private Integer status;
    private String syncHash;
    private LocalDateTime lastSyncTime;
}
