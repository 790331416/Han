package com.han.ai.controller;

import com.han.ai.domain.dto.AiChatImageRequest;
import com.han.ai.domain.dto.AiChatRequest;
import com.han.ai.domain.dto.AiConversationRenameRequest;
import com.han.ai.domain.dto.AiMessageEditRequest;
import com.han.ai.domain.po.AiChatMessagePo;
import com.han.ai.domain.po.AiConversationPo;
import com.han.ai.domain.query.AiConversationQuery;
import com.han.ai.service.IAiChatService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
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

import java.util.List;

/**
 * AI 对话控制器。
 */
@AdminAuth
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final IAiChatService aiChatService;

    @GetMapping("/conversations")
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public R<PageResult<AiConversationPo>> listConversations(AiConversationQuery query) {
        return R.ok(aiChatService.selectConversationPage(query));
    }

    @GetMapping("/messages/{conversationId}")
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public R<List<AiChatMessagePo>> listMessages(@PathVariable Long conversationId) {
        return R.ok(aiChatService.selectMessages(conversationId));
    }

    @RepeatSubmit
    @PostMapping("/send")
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public R<AiChatMessagePo> send(@RequestBody AiChatRequest request) {
        return R.ok(aiChatService.send(request));
    }

    @RepeatSubmit
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public SseEmitter stream(@RequestBody AiChatRequest request) {
        return aiChatService.stream(request);
    }

    /**
     * 对话内文生图（IMAGE 模型），生成图转存文件服务后随 assistant 消息返回。
     */
    @RepeatSubmit
    @PostMapping("/image")
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public R<AiChatMessagePo> generateImage(@RequestBody AiChatImageRequest request) {
        return R.ok(aiChatService.generateImage(request));
    }

    @RepeatSubmit
    @PostMapping(value = "/regenerate/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public SseEmitter regenerate(@PathVariable Long conversationId) {
        return aiChatService.regenerate(conversationId);
    }

    @RepeatSubmit
    @PostMapping(value = "/edit-regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public SseEmitter editRegenerate(@RequestBody AiMessageEditRequest request) {
        return aiChatService.editRegenerate(request);
    }

    @RepeatSubmit
    @PostMapping("/conversations/remove/{conversationId}")
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public R<Void> deleteConversation(@PathVariable Long conversationId) {
        aiChatService.deleteConversation(conversationId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/conversations/clear/{conversationId}")
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public R<Void> clearConversation(@PathVariable Long conversationId) {
        aiChatService.clearConversation(conversationId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/conversations/rename/{conversationId}")
    @PreAuthorize("@ss.hasAuthority('ai:chat:list')")
    public R<Void> renameConversation(@PathVariable Long conversationId,
                                      @RequestBody(required = false) AiConversationRenameRequest request) {
        aiChatService.renameConversation(conversationId, request != null ? request.getTitle() : null);
        return R.ok();
    }
}
