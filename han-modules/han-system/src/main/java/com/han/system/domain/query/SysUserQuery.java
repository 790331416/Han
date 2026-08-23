package com.han.system.domain.query;

import com.han.common.core.domain.query.TenantQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户查询对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserQuery extends TenantQuery {

    /** 用户名 */
    @Schema(description = "用户名")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;

    /** 状态 */
    @Schema(description = "状态（0正常 1停用）")
    private Integer status;

    /** 部门ID */
    @Schema(description = "部门ID")
    private Long deptId;

    /** 账号类型：SYSTEM=系统用户，CLIENT=关联教育人员的客户端用户。 */
    @Schema(description = "账号类型（SYSTEM系统用户 CLIENT客户端用户）")
    private String accountType;

    /** 开始时间（范围查询） */
    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    /** 结束时间（范围查询） */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /** 部门ID列表（数据权限） */
    @Schema(description = "数据权限部门ID列表", hidden = true)
    private List<Long> deptIds;
}
