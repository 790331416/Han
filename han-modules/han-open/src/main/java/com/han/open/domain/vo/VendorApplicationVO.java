package com.han.open.domain.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 厂商入驻申请VO
 */
@Data
public class VendorApplicationVO {

    /** 厂商名称 */
    @NotBlank(message = "厂商名称不能为空")
    private String name;

    /** 统一社会信用代码 */
    @NotBlank(message = "统一社会信用代码不能为空")
    private String qualificationNo;

    /** 所属行业 */
    private String industry;

    /** 联系人姓名 */
    @NotBlank(message = "联系人姓名不能为空")
    private String contactName;

    /** 联系电话 */
    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 官网地址 */
    private String website;

    /** 申请说明 */
    private String applyReason;
}
