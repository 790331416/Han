package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 应用-接口授权关系持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_app_resource_grant")
public class OpenAppResourceGrantPo extends BizEntity {

    /** 应用ID */
    private Long appId;

    /** 资源ID */
    private Long resourceId;

    /** 环境：SANDBOX沙箱、PROD生产 */
    private String environment;

    /** 资源版本ID，空表示最新版本 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long versionId;

    /** 授权Scope列表，逗号分隔 */
    private String scopes;

    /** 数据范围配置：学校、字段、脱敏级别等 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dataScope;

    /** 调用配额，0表示不限制 */
    private Long quota;

    /** 过期时间，空表示永久有效 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expiresAt;

    /** 状态：0待审核 1已生效 2已驳回 3已过期 4已撤销 */
    private Integer status;

    /** 申请理由 */
    private String applyReason;

    /** 审核原因 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewReason;

    /** 审核人ID */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reviewerId;

    /** 审核时间 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime reviewTime;
}
