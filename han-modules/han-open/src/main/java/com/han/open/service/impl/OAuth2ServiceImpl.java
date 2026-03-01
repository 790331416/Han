package com.han.open.service.impl;

import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.IOAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2授权服务实现（占位实现，待完善）
 */
@Service
@RequiredArgsConstructor
public class OAuth2ServiceImpl implements IOAuth2Service {

    private final Map<String, Long> authCodeStore = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();

    @Override
    public String authorize(OAuth2AuthorizeDTO dto, Long userId) {
        String code = UUID.randomUUID().toString().replace("-", "");
        authCodeStore.put(code, userId);
        return code;
    }

    @Override
    public OAuth2TokenVO token(OAuth2TokenDTO dto) {
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(accessToken, 1L);
        return OAuth2TokenVO.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .refreshToken(refreshToken)
                .scope(dto.getScope())
                .build();
    }

    @Override
    public OAuth2TokenVO refreshToken(String refreshToken, String clientId, String clientSecret) {
        return token(new OAuth2TokenDTO());
    }

    @Override
    public void revokeToken(String token, String tokenTypeHint) {
        tokenStore.remove(token);
    }

    @Override
    public Object introspectToken(String token) {
        boolean active = tokenStore.containsKey(token);
        return Map.of("active", active);
    }

    @Override
    public OAuth2UserInfoVO getUserInfo(String accessToken) {
        return OAuth2UserInfoVO.builder()
                .sub("1")
                .name("admin")
                .nickname("管理员")
                .build();
    }

    @Override
    public Long validateAuthorizationCode(String code, String clientId, String redirectUri, String codeVerifier) {
        return authCodeStore.remove(code);
    }
}
