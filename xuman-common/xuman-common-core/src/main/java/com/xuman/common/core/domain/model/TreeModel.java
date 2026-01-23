package com.xuman.common.core.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 树形模型 - 树形结构的纯 POJO
 * <p>
 * 适用场景：树形结构的 DTO、VO 等非持久化对象
 * <p>
 * 如需持久化，请使用 xuman-common-mybatis 中的 TreeEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TreeModel<T extends TreeModel<T>> extends BizModel {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父级ID */
    private Long parentId;

    /** 祖级列表（逗号分隔） */
    private String ancestors;

    /** 显示顺序 */
    private Integer sort;

    /** 层级 */
    private Integer level;

    /** 子节点 */
    private List<T> children = new ArrayList<>();
}
