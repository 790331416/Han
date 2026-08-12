package com.han.api.system.domain;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    /**
     * 密码（BCrypt 哈希）。
     *
     * @deprecated 哈希跟着 {@code getUserById} / {@code getUserByUsername} 这类高频查询接口
     *         跨进程传输，会进入两端的访问日志、链路追踪 payload 与抓包，而服务间是纯 HTTP 明文。
     *         请改用 {@code SystemServiceClient#verifyPassword}，比对在 han-system 内部完成。
     *         <p><b>本字段暂不能删也不能加 {@code @JsonIgnore}</b>：han-auth 的
     *         {@code AuthServiceImpl#login} 与 {@code TotpController#unbindTotp} 目前仍靠它取哈希
     *         做 {@code PasswordUtil.matches}，改成 {@code @JsonIgnore} 会当场让登录失败。
     *         删除动作必须排在这两个调用点切到 {@code verifyPassword} 之后。
     */
    @Deprecated
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

    /** 角色ID列表 */
    private Set<Long> roleIds;

    /** 角色Key列表 */
    private Set<String> roleKeys;

    /** 权限列表 */
    private Set<String> permissions;

    /**
     * 是否管理员。
     *
     * <p>派生方法，不是数据字段。加 {@code @JsonIgnore} 是因为 Jackson 会把这个无参 boolean
     * getter 当成属性序列化出一个没有 setter 的 {@code admin} 字段：只出不进、每个响应都多传，
     * 还容易让调用方误以为后端下发了权威的管理员标志 —— 它的判断依据只是 {@code userId == 1}，
     * 并不代表真实权限。
     */
    @JsonIgnore
    public boolean isAdmin() {
        return userId != null && userId == 1L;
    }
}
