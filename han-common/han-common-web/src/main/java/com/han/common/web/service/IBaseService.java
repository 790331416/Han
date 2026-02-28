package com.han.common.web.service;

import java.util.List;

/**
 * 基础服务接口
 * 
 * @param <Q> 查询参数类型
 * @param <D> DTO类型
 */
public interface IBaseService<Q, D> {

    /**
     * 查询列表（带数据权限）
     */
    List<D> selectListScope(Q query);

    /**
     * 查询列表
     */
    List<D> selectList(Q query);

    /**
     * 根据ID查询
     */
    D selectById(Long id);

    /**
     * 批量查询
     */
    List<D> selectByIds(List<Long> ids);

    /**
     * 新增
     */
    int insert(D dto);

    /**
     * 修改
     */
    int update(D dto);

    /**
     * 根据ID删除
     */
    int deleteById(Long id);

    /**
     * 批量删除
     */
    int deleteByIds(List<Long> ids);
}
