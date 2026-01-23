package com.xuman.common.core.convert;

import java.util.List;

/**
 * 简单对象转换器接口 - 仅 Entity 和 VO 互转
 * <p>
 * 适用于不需要 DTO 的简单场景
 *
 * @param <E> Entity 实体类型
 * @param <V> VO 视图对象类型
 */
public interface SimpleConverter<E, V> {

    /**
     * Entity -> VO
     */
    V toVO(E entity);

    /**
     * Entity List -> VO List
     */
    List<V> toVOList(List<E> entities);

    /**
     * VO -> Entity
     */
    E toEntity(V vo);

    /**
     * VO List -> Entity List
     */
    List<E> toEntityList(List<V> vos);
}
