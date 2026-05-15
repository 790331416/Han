package com.han.auth.service;

import com.han.auth.domain.SocialUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * GitHub OAuth2 服务。
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

    private final RestClient restClient = RestClient.create();

    /**
     * 获取 GitHub OAuth 授权 URL。
     */
    public String getAuthorizeUrl(String redirectUri) {
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=read:user user:email";
    }

    /**
     * 用授权码换取用户信息。
     */
    @SuppressWarnings("unchecked")
    public SocialUser getUserByCode(String code) {
        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code
        );
        Map<String, Object> tokenData = restClient.post()
                .uri(TOKEN_URL)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (tokenData == null || !tokenData.containsKey("access_token")) {
            log.error("GitHub OAuth token exchange failed: {}", tokenData);
            throw new RuntimeException("GitHub 授权失败");
        }
        String accessToken = (String) tokenData.get("access_token");

        Map<String, Object> userData = restClient.get()
                .uri(USER_API)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Map.class);

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
