package com.han.ai.service;

import com.han.ai.domain.po.AiPromptTemplatePo;
import com.han.ai.domain.query.AiPromptTemplateQuery;
import com.han.common.core.domain.PageResult;

import java.util.List;
import java.util.Map;

/**
 * Prompt 模板服务接口。
 */
public interface IAiPromptTemplateService {

    /**
     * 分页查询 Prompt 模板。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<AiPromptTemplatePo> selectPage(AiPromptTemplateQuery query);

    /**
     * 查询 Prompt 模板详情。
     *
     * @param templateId 模板 ID
     * @return 模板详情
     */
    AiPromptTemplatePo selectById(Long templateId);

    /**
     * 查询全部启用模板。
     *
     * @return 模板列表
     */
    List<AiPromptTemplatePo> selectAll();

    /**
     * 新增 Prompt 模板。
     *
     * @param template 模板数据
     */
    void insert(AiPromptTemplatePo template);

    /**
     * 更新 Prompt 模板。
     *
     * @param template 模板数据
     */
    void update(AiPromptTemplatePo template);

    /**
     * 删除 Prompt 模板。
     *
     * @param templateId 模板 ID
     */
    void deleteById(Long templateId);

    /**
     * 使用变量渲染 Prompt 模板内容。
     *
     * @param templateId 模板 ID
     * @param variables 渲染变量
     * @return 渲染后的内容
     */
    String render(Long templateId, Map<String, String> variables);
}
