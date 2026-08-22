package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 厂商用户关联持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_vendor_user")
public class OpenVendorUserPo extends TenantEntity {

    /** 厂商ID */
    private Long vendorId;

    /** 用户ID（关联sys_user） */
    private Long userId;

    /** 角色：OWNER所有者、DEVELOPER开发者、VIEWER查看者 */
    private String role;

    /** 状态：0正常 1停用 */
    private Integer status;
}
