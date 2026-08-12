package com.han.common.core.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * 如需持久化，请使用 han-common-mybatis 中的 BaseEntity
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

    /**
     * 搜索值（非数据库字段）
     * <p>查询专用字段，不参与 JSON 出入参：出参会把内部查询条件回显给前端。
     */
    @JsonIgnore
    private String searchValue;

    /**
     * 请求参数（非数据库字段）
     * <p>不参与 JSON 出入参：作为入参它是一条绕过全部字段级校验的任意键值通道。
     * <b>禁止</b>把本字段的内容用于任何 SQL 拼接。
     */
    @JsonIgnore
    private Map<String, Object> params;
}
