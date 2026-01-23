package com.xuman.common.mybatis.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 树形实体 - 树形结构持久化实体
 * <p>
 * 如需纯 POJO，请使用 xuman-common-core 中的 TreeModel
 * 
 * @see com.xuman.common.core.domain.model.TreeModel
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TreeEntity<T extends TreeEntity<T>> extends BizEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父级ID */
    private Long parentId;

    /** 祖级列表（逗号分隔） */
    private String ancestors;

    /** 显示顺序 */
    private Integer sort;

    /** 层级（非数据库字段） */
    @TableField(exist = false)
    private Integer level;

    /** 子节点（非数据库字段） */
    @TableField(exist = false)
    private List<T> children = new ArrayList<>();
}
