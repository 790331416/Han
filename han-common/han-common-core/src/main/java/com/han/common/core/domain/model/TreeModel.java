package com.han.common.core.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TreeModel<T extends TreeModel<T>> extends BizModel {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long parentId;

    private String ancestors;

    private Integer sort;

    private Integer level;

    private List<T> children = new ArrayList<>();
}
