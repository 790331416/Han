package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUserPo extends BizEntity {

    /** 部门ID */
    private Long deptId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 密码 */
    private String password;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别（0未知 1男 2女） */
    private Integer sex;

    /** 状态（0正常 1停用） */
    private Integer status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginTime;

    /** 密码最后修改时间 */
    private LocalDateTime pwdUpdateTime;

    /** 密码重置标记（1=需要修改密码） */
    private Integer pwdResetFlag;

    /** TOTP 密钥（2FA 绑定后存储，加密保存） */
    private String totpSecret;

    /** 是否启用 2FA（0=未启用 1=已启用） */
    private Integer totpEnabled;

    /**
     * 是否管理员
     */
    public boolean isAdmin() {
        return getId() != null && getId() == 1L;
    }
}
