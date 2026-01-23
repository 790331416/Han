package com.xuman.system.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户VO
 */
@Data
public class UserVO {

    /** 用户ID */
    private Long userId;

    /** 租户ID */
    private Long tenantId;

    /** 部门ID */
    private Long deptId;

    /** 部门名称 */
    private String deptName;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别 */
    private Integer sex;

    /** 状态 */
    private Integer status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 角色ID列表 */
    private Set<Long> roleIds;

    /** 角色名称列表 */
    private Set<String> roleNames;

    /** 岗位ID列表 */
    private Set<Long> postIds;
}
