package com.han.api.open.domain;

import lombok.Data;

/** auth 完成验证码与密码解密后，交给 han-open 创建厂商申请的内部 DTO。 */
@Data
public class OpenVendorApplicationCreateDTO {

    private Long accountUserId;
    private String name;
    private String qualificationNo;
    private String industry;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String website;
    private String applyReason;
}
