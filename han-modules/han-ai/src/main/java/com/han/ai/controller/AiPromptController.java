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
 * Prompt 模板管理控制器。
 */
@AdminAuth
@RestController
@RequestMapping("/ai/prompt")
@RequiredArgsConstructor
public class AiPromptController {

    private final IAiPromptTemplateService aiPromptTemplateService;

    /**
     * 分页查询 Prompt 模板列表。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:list')")
    public R<PageResult<AiPromptTemplatePo>> list(AiPromptTemplateQuery query) {
        return R.ok(aiPromptTemplateService.selectPage(query));
    }

    /**
     * 查询 Prompt 模板详情。
     *
     * @param templateId 模板 ID
     * @return 模板详情
     */
    @GetMapping("/{templateId}")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:query')")
    public R<AiPromptTemplatePo> getInfo(@PathVariable Long templateId) {
        return R.ok(aiPromptTemplateService.selectById(templateId));
    }

    /**
     * 查询全部启用模板。
     *
     * @return 模板列表
     */
    @GetMapping("/all")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:list')")
    public R<List<AiPromptTemplatePo>> listAll() {
        return R.ok(aiPromptTemplateService.selectAll());
    }

    /**
     * 新增 Prompt 模板。
     *
     * @param template 模板数据
     * @return 操作结果
     */
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('ai:prompt:add')")
    public R<Void> add(@Valid @RequestBody AiPromptTemplatePo template) {
        aiPromptTemplateService.insert(template);
        return R.ok();
    }

    /**
     * 更新 Prompt 模板。
     *
     * @param template 模板数据
     * @return 操作结果
     */
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:edit')")
    public R<Void> edit(@Valid @RequestBody AiPromptTemplatePo template) {
        aiPromptTemplateService.update(template);
        return R.ok();
    }

    /**
     * 删除 Prompt 模板。
     *
     * @param templateId 模板 ID
     * @return 操作结果
     */
    @RepeatSubmit
    @PostMapping("/remove/{templateId}")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:remove')")
    public R<Void> remove(@PathVariable Long templateId) {
        aiPromptTemplateService.deleteById(templateId);
        return R.ok();
    }

    /**
     * 使用变量渲染 Prompt 模板。
     *
     * @param templateId 模板 ID
     * @param variables 渲染变量
     * @return 渲染后的内容
     */
    @PostMapping("/render/{templateId}")
    @PreAuthorize("@ss.hasAuthority('ai:prompt:list')")
    public R<String> render(@PathVariable Long templateId, @RequestBody(required = false) Map<String, String> variables) {
        return R.ok(aiPromptTemplateService.render(templateId, variables));
    }
}
