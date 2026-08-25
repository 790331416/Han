package com.han.open.domain.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 厂商可维护的基础资料；资质、租户和审核字段不开放修改。 */
@Data
public class VendorProfileUpdateVO {

    @NotBlank(message = "厂商名称不能为空")
    private String name;

    private String industry;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private String website;
}
