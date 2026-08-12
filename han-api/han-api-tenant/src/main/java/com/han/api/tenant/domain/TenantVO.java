package com.han.api.tenant.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户信息VO（跨服务契约类型）。
 *
 * <p>本类是 han-tenant I 层接口的对外返回类型。han-tenant 内部的
 * {@code TenantDTO} / {@code com.han.tenant.domain.vo.TenantVO} 字段更多
 * （contactEmail、domain、packageName、userCount、remark、createTime 等），
 * 服务端需要显式转换成本类，未在此声明的字段一律不跨服务传输。
 */
@Data
public class TenantVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 租户名称 */
    private String tenantName;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 套餐ID */
    private Long packageId;

    /** 用户数量限制（-1不限制） */
    private Integer userLimit;

    /**
     * 过期时间。
     *
     * <p>格式与 {@code UserVO.loginTime} 对齐：服务端全局 Jackson 定制把 {@code LocalDateTime}
     * 写成 {@code yyyy-MM-dd HH:mm:ss}，而声明式客户端用的是未经定制的 ObjectMapper（默认按
     * ISO-8601 解析）。这里显式声明格式，保证契约自描述、收发两侧一致。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 状态 */
    private Integer status;

    /** 数据隔离类型（logical/physical/hybrid） */
    private String isolationType;
}
