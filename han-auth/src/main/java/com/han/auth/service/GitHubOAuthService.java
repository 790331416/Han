package com.han.auth.service;

import com.han.auth.domain.SocialUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * GitHub OAuth2 服务
 * <p>OAuth2 流程：
 * 1. 前端跳转 GitHub 授权页
 * 2. 用户授权后 GitHub 回调到前端，携带 code
 * 3. 前端将 code 发送到后端
 * 4. 后端用 code 换 access_token，再获取用户信息
 */
@Slf4j
@Service
public class GitHubOAuthService {

    @Value("${social.github.client-id:}")
    private String clientId;

    @Value("${social.github.client-secret:}")
    private String clientSecret;

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_API = "https://api.github.com/user";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 获取 GitHub OAuth 授权 URL
     */
    public String getAuthorizeUrl(String redirectUri) {
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=read:user user:email";
    }

    /**
     * 用授权码换取用户信息
     */
    @SuppressWarnings("unchecked")
    public SocialUser getUserByCode(String code) {
        // 1. 用 code 换 access_token
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code
        );
        ResponseEntity<Map> tokenResp = restTemplate.exchange(
                TOKEN_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> tokenData = tokenResp.getBody();
        if (tokenData == null || !tokenData.containsKey("access_token")) {
            log.error("GitHub OAuth token exchange failed: {}", tokenData);
            throw new RuntimeException("GitHub 授权失败");
        }
        String accessToken = (String) tokenData.get("access_token");

        // 2. 用 access_token 获取用户信息
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        ResponseEntity<Map> userResp = restTemplate.exchange(
                USER_API, HttpMethod.GET,
                new HttpEntity<>(userHeaders), Map.class);

        Map<String, Object> userData = userResp.getBody();
        if (userData == null || !userData.containsKey("id")) {
            log.error("GitHub user info fetch failed");
            throw new RuntimeException("获取 GitHub 用户信息失败");
        }

        return SocialUser.builder()
                .provider("github")
                .openId(String.valueOf(userData.get("id")))
                .nickname((String) userData.getOrDefault("login", ""))
                .avatar((String) userData.getOrDefault("avatar_url", ""))
                .email((String) userData.get("email"))
                .accessToken(accessToken)
                .build();
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
