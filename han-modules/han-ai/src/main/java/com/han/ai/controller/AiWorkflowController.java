package com.han.ai.controller;

import com.han.ai.domain.dto.AiChatRequest;
import com.han.ai.domain.po.AiWorkflowPo;
import com.han.ai.domain.query.AiWorkflowQuery;
import com.han.ai.service.IAiWorkflowService;
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

/**
 * AI workflow controller.
 */
@AdminAuth
@RestController
@RequestMapping("/ai/workflow")
@RequiredArgsConstructor
public class AiWorkflowController {

    private final IAiWorkflowService aiWorkflowService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:list')")
    public R<PageResult<AiWorkflowPo>> list(AiWorkflowQuery query) {
        return R.ok(aiWorkflowService.selectPage(query));
    }

    @GetMapping("/{workflowId}")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:list')")
    public R<AiWorkflowPo> getInfo(@PathVariable Long workflowId) {
        return R.ok(aiWorkflowService.selectById(workflowId));
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('ai:workflow:add')")
    public R<Void> add(@Valid @RequestBody AiWorkflowPo workflow) {
        aiWorkflowService.insert(workflow);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:edit')")
    public R<Void> edit(@Valid @RequestBody AiWorkflowPo workflow) {
        aiWorkflowService.update(workflow);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove/{workflowId}")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:remove')")
    public R<Void> remove(@PathVariable Long workflowId) {
        aiWorkflowService.deleteById(workflowId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/publish/{workflowId}")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:edit')")
    public R<Void> publish(@PathVariable Long workflowId) {
        aiWorkflowService.publish(workflowId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/unpublish/{workflowId}")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:edit')")
    public R<Void> unpublish(@PathVariable Long workflowId) {
        aiWorkflowService.unpublish(workflowId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/chat/{workflowId}")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:list')")
    public R<String> chat(@PathVariable Long workflowId, @RequestBody(required = false) AiChatRequest request) {
        String message = request != null ? request.getMessage() : null;
        Long conversationId = request != null ? request.getConversationId() : null;
        return R.ok(aiWorkflowService.chat(workflowId, message, conversationId));
    }
}
