package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 厂商入驻申请持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_vendor_application")
public class OpenVendorApplicationPo extends BizEntity {

    /** 关联厂商ID */
    private Long vendorId;

    /** 申请人用户ID */
    private Long applicantUserId;

    /** 申请编号 */
    private String applicationNo;

    /** 状态：0待提交 1待审核 2审核通过 3审核驳回 */
    private Integer status;

    /** 申请数据快照 */
    private String applyData;

    /** 审核原因/驳回说明 */
    private String reason;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewTime;
}
