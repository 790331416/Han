package com.han.ai.controller;

import com.han.ai.domain.dto.AiChatRequest;
import com.han.ai.domain.po.AiWorkflowPo;
import com.han.ai.domain.query.AiWorkflowQuery;
import com.han.ai.domain.vo.AiFlowDebugVo;
import com.han.ai.service.IAiWorkflowService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    /**
     * 编排调试运行（设计器右侧调试抽屉）：不要求已发布、不落会话消息。
     */
    @RepeatSubmit
    @PostMapping("/debug/{workflowId}")
    @PreAuthorize("@ss.hasAuthority('ai:workflow:edit')")
    public R<AiFlowDebugVo> debug(@PathVariable Long workflowId, @RequestBody AiChatRequest request) {
        return R.ok(aiWorkflowService.debug(workflowId, request != null ? request.getMessage() : null));
    }

    /**
     * 编排调试流式运行：SSE 实时下发 node_start / node_delta / node_end 节点事件
     * 与最终回复；语义同 /debug（不要求已发布、不落会话消息）。
     */
    @RepeatSubmit
    @PostMapping(value = "/debug-stream/{workflowId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasAuthority('ai:workflow:edit')")
    public SseEmitter debugStream(@PathVariable Long workflowId, @RequestBody AiChatRequest request) {
        return aiWorkflowService.debugStream(workflowId, request != null ? request.getMessage() : null);
    }
}
