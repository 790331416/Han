package com.han.ai.controller;

import com.han.ai.domain.po.AiPromptTemplatePo;
import com.han.ai.domain.query.AiPromptTemplateQuery;
import com.han.ai.service.IAiPromptTemplateService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Prompt template controller.
 */
@AdminAuth
@RestController
@RequestMapping("/ai/prompt")
@RequiredArgsConstructor
public class AiPromptController {

    private final IAiPromptTemplateService aiPromptTemplateService;

    /**
     * Query paged template list.
     *
     * @param query query params
     * @return page result
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:list')")
    public R<PageResult<AiPromptTemplatePo>> list(AiPromptTemplateQuery query) {
        return R.ok(aiPromptTemplateService.selectPage(query));
    }

    /**
     * Query template detail.
     *
     * @param templateId template id
     * @return detail
     */
    @GetMapping("/{templateId}")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:query')")
    public R<AiPromptTemplatePo> getInfo(@PathVariable Long templateId) {
        return R.ok(aiPromptTemplateService.selectById(templateId));
    }

    /**
     * Query all enabled templates.
     *
     * @return template list
     */
    @GetMapping("/all")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:list')")
    public R<List<AiPromptTemplatePo>> listAll() {
        return R.ok(aiPromptTemplateService.selectAll());
    }

    /**
     * Create template.
     *
     * @param template template data
     * @return result
     */
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('ai:prompt:add')")
    public R<Void> add(@Valid @RequestBody AiPromptTemplatePo template) {
        aiPromptTemplateService.insert(template);
        return R.ok();
    }

    /**
     * Update template.
     *
     * @param template template data
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:edit')")
    public R<Void> edit(@Valid @RequestBody AiPromptTemplatePo template) {
        aiPromptTemplateService.update(template);
        return R.ok();
    }

    /**
     * Delete template.
     *
     * @param templateId template id
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/remove/{templateId}")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:remove')")
    public R<Void> remove(@PathVariable Long templateId) {
        aiPromptTemplateService.deleteById(templateId);
        return R.ok();
    }

    /**
     * Render template with variables.
     *
     * @param templateId template id
     * @param variables variables
     * @return rendered content
     */
    @PostMapping("/render/{templateId}")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:list')")
    public R<String> render(@PathVariable Long templateId, @RequestBody(required = false) Map<String, String> variables) {
        return R.ok(aiPromptTemplateService.render(templateId, variables));
    }
}
