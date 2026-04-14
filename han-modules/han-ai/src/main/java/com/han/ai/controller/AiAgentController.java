package com.han.ai.controller;

import com.han.ai.domain.dto.AiChatRequest;
import com.han.ai.domain.po.AiAgentPo;
import com.han.ai.domain.query.AiAgentQuery;
import com.han.ai.service.IAiAgentService;
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
 * AI agent controller.
 */
@AdminAuth
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AiAgentController {

    private final IAiAgentService aiAgentService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:agent:list')")
    public R<PageResult<AiAgentPo>> list(AiAgentQuery query) {
        return R.ok(aiAgentService.selectPage(query));
    }

    @GetMapping("/{agentId}")
    @PreAuthorize("@ss.hasAuthority('ai:agent:list')")
    public R<AiAgentPo> getInfo(@PathVariable Long agentId) {
        return R.ok(aiAgentService.selectById(agentId));
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('ai:agent:add')")
    public R<Void> add(@Valid @RequestBody AiAgentPo agent) {
        aiAgentService.insert(agent);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:agent:edit')")
    public R<Void> edit(@Valid @RequestBody AiAgentPo agent) {
        aiAgentService.update(agent);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove/{agentId}")
    @PreAuthorize("@ss.hasAuthority('ai:agent:remove')")
    public R<Void> remove(@PathVariable Long agentId) {
        aiAgentService.deleteById(agentId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/publish/{agentId}")
    @PreAuthorize("@ss.hasAuthority('ai:agent:edit')")
    public R<Void> publish(@PathVariable Long agentId) {
        aiAgentService.publish(agentId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/unpublish/{agentId}")
    @PreAuthorize("@ss.hasAuthority('ai:agent:edit')")
    public R<Void> unpublish(@PathVariable Long agentId) {
        aiAgentService.unpublish(agentId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/chat/{agentId}")
    @PreAuthorize("@ss.hasAuthority('ai:agent:list')")
    public R<String> chat(@PathVariable Long agentId, @RequestBody(required = false) AiChatRequest request) {
        String message = request != null ? request.getMessage() : null;
        Long conversationId = request != null ? request.getConversationId() : null;
        return R.ok(aiAgentService.chat(agentId, message, conversationId));
    }
}
