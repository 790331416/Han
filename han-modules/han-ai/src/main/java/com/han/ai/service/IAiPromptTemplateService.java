package com.han.ai.service;

import com.han.ai.domain.po.AiPromptTemplatePo;
import com.han.ai.domain.query.AiPromptTemplateQuery;
import com.han.common.core.domain.PageResult;

import java.util.List;
import java.util.Map;

/**
 * Prompt template service.
 */
public interface IAiPromptTemplateService {

    /**
     * Query paged template list.
     *
     * @param query query params
     * @return page result
     */
    PageResult<AiPromptTemplatePo> selectPage(AiPromptTemplateQuery query);

    /**
     * Query template detail.
     *
     * @param templateId template id
     * @return detail
     */
    AiPromptTemplatePo selectById(Long templateId);

    /**
     * Query all enabled templates.
     *
     * @return template list
     */
    List<AiPromptTemplatePo> selectAll();

    /**
     * Insert template.
     *
     * @param template template data
     */
    void insert(AiPromptTemplatePo template);

    /**
     * Update template.
     *
     * @param template template data
     */
    void update(AiPromptTemplatePo template);

    /**
     * Delete template.
     *
     * @param templateId template id
     */
    void deleteById(Long templateId);

    /**
     * Render template with variables.
     *
     * @param templateId template id
     * @param variables render variables
     * @return rendered content
     */
    String render(Long templateId, Map<String, String> variables);
}
