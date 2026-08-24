package com.han.open.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 厂商详情VO
 */
@Data
public class VendorDetailVO {

    /** 厂商ID */
    private Long id;

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

    /** 厂商关联的用户列表 */
    private List<VendorUserVO> users;

    /** 厂商下的应用列表 */
    private List<VendorAppVO> apps;

    /**
     * 厂商用户VO
     */
    @Data
    public static class VendorUserVO {
        private Long userId;
        private String userName;
        private String phone;
        private String role;
        private Integer status;
    }

    /**
     * 厂商应用VO
     */
    @Data
    public static class VendorAppVO {
        private Long appId;
        private String appName;
        private String appType;
        private Integer lifecycleStatus;
    }
}
