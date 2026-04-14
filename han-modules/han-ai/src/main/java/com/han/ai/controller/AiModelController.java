package com.han.ai.controller;

import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.query.AiModelQuery;
import com.han.ai.service.IAiModelService;
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

/**
 * AI model controller.
 */
@AdminAuth
@RestController
@RequestMapping("/ai/model")
@RequiredArgsConstructor
public class AiModelController {

    private final IAiModelService aiModelService;

    /**
     * Query paged model list.
     *
     * @param query query params
     * @return page result
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:model:list')")
    public R<PageResult<AiModelPo>> list(AiModelQuery query) {
        return R.ok(aiModelService.selectPage(query));
    }

    /**
     * Query model detail.
     *
     * @param modelId model id
     * @return detail
     */
    @GetMapping("/{modelId}")
    @PreAuthorize("@ss.hasAuthority('ai:model:query')")
    public R<AiModelPo> getInfo(@PathVariable Long modelId) {
        return R.ok(aiModelService.selectById(modelId));
    }

    /**
     * Query all enabled models.
     *
     * @param modelType optional model type
     * @return model list
     */
    @GetMapping("/all")
    @PreAuthorize("@ss.hasAuthority('ai:model:list')")
    public R<List<AiModelPo>> listAll(String modelType) {
        return R.ok(aiModelService.selectAll(modelType));
    }

    /**
     * Create model.
     *
     * @param model model data
     * @return result
     */
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('ai:model:add')")
    public R<Void> add(@Valid @RequestBody AiModelPo model) {
        aiModelService.insert(model);
        return R.ok();
    }

    /**
     * Update model.
     *
     * @param model model data
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:model:edit')")
    public R<Void> edit(@Valid @RequestBody AiModelPo model) {
        aiModelService.update(model);
        return R.ok();
    }

    /**
     * Delete model.
     *
     * @param modelId model id
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/remove/{modelId}")
    @PreAuthorize("@ss.hasAuthority('ai:model:remove')")
    public R<Void> remove(@PathVariable Long modelId) {
        aiModelService.deleteById(modelId);
        return R.ok();
    }

    /**
     * Validate model config.
     *
     * @param modelId model id
     * @return validation result
     */
    @PostMapping("/test/{modelId}")
    @PreAuthorize("@ss.hasAuthority('ai:model:test')")
    public R<String> test(@PathVariable Long modelId) {
        return R.ok(aiModelService.test(modelId));
    }
}
