package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OSS storage configuration.
 */
@Data
@TableName("sys_oss_config")
public class SysOssConfigPo {

    @TableId(value = "oss_config_id", type = IdType.AUTO)
    private Long ossConfigId;

    private String configKey;

    private String configName;

    private String providerType;

    /** 仅接收管理端输入或以脱敏形式返回，不映射明文数据库列。 */
    @TableField(exist = false)
    private String accessKey;

    /** 仅接收管理端输入，不得返回原文。 */
    @TableField(exist = false)
    private String secretKey;

    @JsonIgnore
    private String accessKeyCiphertext;

    @JsonIgnore
    private String secretKeyCiphertext;

    private Integer keyVersion;

    private String bucketName;

    private String prefix;

    private String endpoint;

    private String publicEndpoint;

    private String region;

    private String isHttps;

    private Boolean pathStyle;

    private Integer configVersion;

    private String status;

    private String remark;

    private Long tenantId;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
