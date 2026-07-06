package com.han.ai.controller;

import com.han.ai.domain.po.AiAgentPo;
import com.han.ai.service.IAiAgentService;
import com.han.ai.service.IAiChatService;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用公开分享接口（免登录，网关白名单 /ai/share/）。
 * <p>
 * 安全边界：仅暴露已发布且启用的智能体；shareKey 无效一律 404；
 * profile 不返回模型/凭据等敏感配置；对话无状态不落库，历史由前端携带。
 * 限流（单 key QPS/日调用量）属网关级配套，本期未实现，上 95 前需补齐。
 */
@RestController
@RequestMapping("/ai/share")
@RequiredArgsConstructor
public class AiShareController {

    private final IAiAgentService aiAgentService;
    private final IAiChatService aiChatService;

    /**
     * 公开应用信息（名称/头像/开场白）。
     */
    @GetMapping("/{shareKey}/profile")
    @PermissionExempt("公开分享入口，仅暴露已发布应用的展示信息")
    public R<Map<String, Object>> profile(@PathVariable String shareKey) {
        AiAgentPo agent = requirePublished(shareKey);
        Map<String, Object> profile = new HashMap<>();
        profile.put("agentName", agent.getAgentName());
        profile.put("avatar", agent.getAvatar());
        profile.put("prologue", agent.getPrologue());
        profile.put("description", agent.getDescription());
        return R.ok(profile);
    }

    /**
     * 公开对话（无状态、不落库）。
     */
    @PostMapping("/{shareKey}/chat")
    @PermissionExempt("公开分享对话入口，按 shareKey 校验已发布应用")
    public R<Map<String, Object>> chat(@PathVariable String shareKey, @RequestBody ShareChatRequest request) {
        AiAgentPo agent = requirePublished(shareKey);
        String reply = aiChatService.shareChat(agent,
                request != null ? request.getMessage() : null,
                request != null ? request.getHistory() : null);
        return R.ok(Map.of("reply", reply));
    }

    private AiAgentPo requirePublished(String shareKey) {
        AiAgentPo agent = aiAgentService.selectPublishedByShareKey(shareKey);
        if (agent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分享链接无效或应用未发布");
        }
        return agent;
    }

    @Data
    public static class ShareChatRequest {

        private String message;

        /**
         * 前端携带的最近对话轮次 [{role:user|assistant, content}]
         */
        private List<Map<String, String>> history;
    }
}
