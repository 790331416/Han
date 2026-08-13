package com.han.ai.service;

import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.query.AiModelQuery;
import com.han.common.core.domain.PageResult;

import java.util.List;

/**
 * AI 模型服务。
 */
public interface IAiModelService {

    /**
     * 分页查询模型列表。
     *
     * @param query query params
     * @return page result
     */
    PageResult<AiModelPo> selectPage(AiModelQuery query);

    /**
     * 查询模型详情。
     *
     * @param modelId model id
     * @return model detail
     */
    AiModelPo selectById(Long modelId);

    /**
     * 查询全部已启用的模型。
     *
     * @param modelType model type filter
     * @return model list
     */
    List<AiModelPo> selectAll(String modelType);

    /**
     * 新增模型。
     *
     * @param model model data
     */
    void insert(AiModelPo model);

    /**
     * 修改模型。
     *
     * @param model model data
     */
    void update(AiModelPo model);

    /**
     * 按主键删除模型。
     *
     * @param modelId model id
     */
    void deleteById(Long modelId);

    /**
     * 校验模型配置连通性。
     *
     * @param modelId model id
     * @return test result message
     */
    String test(Long modelId);
}
