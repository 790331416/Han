package com.xuman.common.core.domain.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 基础模型 - 纯 POJO，无 ORM 框架依赖
 * <p>
 * 适用场景：DTO、VO、API客户端对象、非持久化业务对象
 * <p>
 * 如需持久化，请使用 xuman-common-mybatis 中的 BaseEntity
 */
@Data
public abstract class BaseModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 删除标志（0存在 1删除） */
    private Integer delFlag;

    /** 搜索值（非数据库字段） */
    private String searchValue;

    /** 请求参数（非数据库字段） */
    private Map<String, Object> params;
}
