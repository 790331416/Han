package com.han.api.open.domain;

import lombok.Data;
import java.time.LocalDateTime;

/** 对外公开的厂商申请状态，禁止携带账号、密码或内部用户 ID。 */
@Data
public class OpenVendorApplicationStatusVO {

    private String applicationNo;
    private Integer status;
    private String statusName;
    private String reason;
    private String vendorName;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
}
