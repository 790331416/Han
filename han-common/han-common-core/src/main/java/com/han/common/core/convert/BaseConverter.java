package com.han.common.core.convert;

import java.util.List;

/**
 * 基础对象转换器接口 - MapStruct 统一规范
 * <p>
 * 所有模块的 Mapper 接口应继承此接口
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Mapper(componentModel = "spring")
 * public interface UserConverter extends BaseConverter&lt;User, UserVO, UserDTO&gt; {
 *     // 复杂映射可在此添加 @Mapping 注解
 * }
 * </pre>
 *
 * @param <E> Entity 实体类型
 * @param <V> VO 视图对象类型
 * @param <D> DTO 数据传输对象类型
 */
public interface BaseConverter<E, V, D> {

    /**
     * Entity -> VO
     */
    V toVO(E entity);

    /**
     * Entity List -> VO List
     */
    List<V> toVOList(List<E> entities);

    /**
     * DTO -> Entity
     */
    E toEntity(D dto);

    /**
     * DTO List -> Entity List
     */
    List<E> toEntityList(List<D> dtos);

    /**
     * VO -> Entity
     */
    E voToEntity(V vo);

    /**
     * VO List -> Entity List
     */
    List<E> voToEntityList(List<V> vos);
}
