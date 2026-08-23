package com.han.open.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 应用授权详情VO
 */
@Data
public class AppGrantDetailVO {

    /** 授权ID */
    private Long id;

    /** 应用ID */
    private Long appId;

    /** 资源ID */
    private Long resourceId;

    /** 授权环境：SANDBOX/PROD */
    private String environment;

    /** 资源编码 */
    private String resourceCode;

    /** 资源名称 */
    private String resourceName;

    /** 资源版本ID */
    private Long versionId;

    /** 版本号 */
    private String version;

    /** 授权Scope列表，逗号分隔 */
    private String scopes;

    /** 数据范围配置 */
    private String dataScope;

    /** 调用配额，0表示不限制 */
    private Long quota;

    /** 已调用次数 */
    private Long usedCount;

    /** 过期时间，空表示永久有效 */
    private LocalDateTime expiresAt;

    /** 状态：0待审核 1已生效 2已驳回 3已过期 4已撤销 */
    private Integer status;

    /** 申请理由 */
    private String applyReason;

    /** 审核原因 */
    private String reviewReason;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
