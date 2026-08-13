package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对象存储配置。
 */
@Data
@TableName("sys_oss_config")
public class SysOssConfigPo {

    @TableId(value = "oss_config_id", type = IdType.AUTO)
    private Long ossConfigId;

    private String configKey;

    private String accessKey;

    private String secretKey;

    private String bucketName;

    private String prefix;

    private String endpoint;

    private String region;

    private String isHttps;

    private String status;

    private String remark;

    private Long tenantId;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
