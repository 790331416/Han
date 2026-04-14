package com.han.ai.service;

import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.query.AiModelQuery;
import com.han.common.core.domain.PageResult;

import java.util.List;

/**
 * AI model service.
 */
public interface IAiModelService {

    /**
     * Query paged model list.
     *
     * @param query query params
     * @return page result
     */
    PageResult<AiModelPo> selectPage(AiModelQuery query);

    /**
     * Query model detail.
     *
     * @param modelId model id
     * @return model detail
     */
    AiModelPo selectById(Long modelId);

    /**
     * Query all enabled models.
     *
     * @param modelType model type filter
     * @return model list
     */
    List<AiModelPo> selectAll(String modelType);

    /**
     * Insert model.
     *
     * @param model model data
     */
    void insert(AiModelPo model);

    /**
     * Update model.
     *
     * @param model model data
     */
    void update(AiModelPo model);

    /**
     * Delete model by id.
     *
     * @param modelId model id
     */
    void deleteById(Long modelId);

    /**
     * Validate model config.
     *
     * @param modelId model id
     * @return test result message
     */
    String test(Long modelId);
}
