package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 授权申请/变更审批持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_authorization_request")
public class OpenAuthorizationRequestPo extends BizEntity {

    /** 应用ID */
    private Long appId;

    /** 关联授权ID，新增申请为空 */
    private Long grantId;

    /** 环境：SANDBOX沙箱、PROD生产 */
    private String environment;

    /** 请求类型：0新增授权 1变更授权 2撤销授权 */
    private Integer requestType;

    /** 状态：0待审核 1已通过 2已驳回 3已撤销 */
    private Integer status;

    /** 申请数据快照 */
    private String requestData;

    /** 申请理由 */
    private String reason;

    /** 审核原因 */
    private String reviewReason;

    /** 申请人ID */
    private Long applicantId;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewTime;
}
