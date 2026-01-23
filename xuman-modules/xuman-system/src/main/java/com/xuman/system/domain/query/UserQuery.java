package com.xuman.system.domain.query;

import com.xuman.common.core.domain.query.TenantQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends TenantQuery {

    /** 部门ID */
    private Long deptId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** 状态 */
    private Integer status;
}
