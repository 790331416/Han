package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 厂商主体持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_vendor")
public class OpenVendorPo extends BizEntity {

    /** 厂商名称 */
    private String name;

    /** 统一社会信用代码 */
    private String qualificationNo;

    /** 所属行业 */
    private String industry;

    /** 联系人姓名 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 官网地址 */
    private String website;

    /** 状态：0待提交 1待验证 2待审核 3补充材料 4审核通过 5审核驳回 6暂停 7注销 */
    private Integer status;

    /** 审核信息/驳回原因 */
    private String reviewInfo;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 审核人ID */
    private Long reviewerId;
}
