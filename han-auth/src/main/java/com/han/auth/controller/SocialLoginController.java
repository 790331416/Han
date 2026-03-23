package com.han.auth.controller;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.UserVO;
import com.han.auth.domain.SocialUser;
import com.han.auth.service.GitHubOAuthService;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 社交登录控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth/social")
@RequiredArgsConstructor
public class SocialLoginController {

    private final GitHubOAuthService gitHubOAuthService;
    private final SystemServiceClient systemServiceClient;

    /**
     * 获取 GitHub OAuth 授权 URL
     */
    @GetMapping("/github/authorize")
    public R<Map<String, String>> githubAuthorize(@RequestParam String redirectUri) {
        if (!gitHubOAuthService.isConfigured()) {
            throw new BusinessException("GitHub OAuth 未配置");
        }
        String url = gitHubOAuthService.getAuthorizeUrl(redirectUri);
        return R.ok(Map.of("authorizeUrl", url));
    }

    /**
     * GitHub OAuth 回调 — 用授权码登录或绑定
     */
    @PostMapping("/github/callback")
    public R<Map<String, Object>> githubCallback(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            throw new BusinessException("授权码不能为空");
        }

        // 用 code 换取 GitHub 用户信息
        SocialUser socialUser = gitHubOAuthService.getUserByCode(code);

        // 查询是否已绑定系统用户
        R<Long> bindResult = systemServiceClient.getSocialBindUserId("github", socialUser.getOpenId());
        if (bindResult.getCode() == Constants.SUCCESS && bindResult.getData() != null) {
            // 已绑定 → 直接登录（返回用户信息，让前端决定后续操作）
            Long userId = bindResult.getData();
            return R.ok(Map.of(
                    "bound", true,
                    "userId", userId,
                    "provider", "github",
                    "nickname", socialUser.getNickname(),
                    "avatar", socialUser.getAvatar()
            ));
        }

        // 未绑定 → 返回社交用户信息，让前端选择绑定已有账号
        // 注意：不返回 accessToken 到前端，仅返回安全的用户展示信息
        return R.ok(Map.of(
                "bound", false,
                "provider", "github",
                "openId", socialUser.getOpenId(),
                "nickname", socialUser.getNickname(),
                "avatar", socialUser.getAvatar() != null ? socialUser.getAvatar() : "",
                "email", socialUser.getEmail() != null ? socialUser.getEmail() : ""
        ));
    }

    /**
     * 绑定社交账号到已有系统用户
     */
    @PostMapping("/bind")
    public R<Void> bind(@RequestBody Map<String, String> body) {
        String provider = body.get("provider");
        String openId = body.get("openId");
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.get("nickname");
        String avatar = body.get("avatar");

        if (provider == null || openId == null || username == null || password == null) {
            throw new BusinessException("参数不完整");
        }

        // 验证用户密码
        R<UserVO> userResult = systemServiceClient.getUserByUsername(username);
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            throw new BusinessException("用户名或密码错误");
        }
        UserVO user = userResult.getData();
        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 保存绑定关系（accessToken 不从前端传入，安全考虑置为 null）
        systemServiceClient.bindSocialUser(user.getUserId(), provider, openId, null, nickname, avatar);
        log.info("用户[{}]绑定社交账号: provider={}, openId={}", username, provider, openId);
        return R.ok();
    }

    /**
     * 获取支持的社交登录方式
     */
    @GetMapping("/providers")
    public R<Map<String, Boolean>> providers() {
        return R.ok(Map.of(
                "github", gitHubOAuthService.isConfigured()
        ));
    }
}
