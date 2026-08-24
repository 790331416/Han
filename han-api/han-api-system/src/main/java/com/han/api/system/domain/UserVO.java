package com.han.api.system.domain;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户信息VO
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 租户ID */
    private Long tenantId;

    /** 部门ID */
    private Long deptId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 密码（加密后） */
    private String password;

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;

    /** 密码最后修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pwdUpdateTime;

    /** 密码重置标记（1=需要修改密码） */
    private Integer pwdResetFlag;

    /** 是否启用 2FA（0=未启用 1=已启用） */
    private Integer totpEnabled;

    /** 是否教育账号（sys_user.remark 以「教育人员」开头） */
    private boolean educationAccount;

    /** 是否已绑定教育人员（edu_person 存在 user_id = userId 的未删除记录，含停用/离校） */
    private boolean educationBound;

    /** 角色ID列表 */
    private Set<Long> roleIds;

    /** 角色Key列表 */
    private Set<String> roleKeys;

    /** 权限列表 */
    private Set<String> permissions;

    /** 是否管理员 */
    public boolean isAdmin() {
        return userId != null && userId == 1L;
    }
}
