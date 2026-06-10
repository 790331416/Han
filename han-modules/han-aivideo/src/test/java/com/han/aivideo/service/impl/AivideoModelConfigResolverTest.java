package com.han.aivideo.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AivideoModelConfigResolverTest {

    @Test
    void resolveTtsConfigPrefersEnabledAiModelJsonCredential() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row(
                "base_url", "https://tts.example.com/api/v1/tts",
                "api_key", "{\"appId\":\"db-app\",\"accessToken\":\"db-token\",\"cluster\":\"db-cluster\",\"defaultVoiceType\":\"voice-001\"}"
        )));

        AivideoModelConfigResolver resolver = new AivideoModelConfigResolver(jdbcTemplate, new MockEnvironment());

        AivideoModelConfigResolver.TtsConfig config = resolver.resolveTtsConfig();

        assertEquals("https://tts.example.com/api/v1/tts", config.endpoint());
        assertEquals("db-app", config.appId());
        assertEquals("db-token", config.accessToken());
        assertEquals("db-cluster", config.cluster());
        assertEquals("voice-001", config.defaultVoiceType());
    }

    @Test
    void resolveVodEditConfigPrefersEnabledAiModelJsonCredential() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row(
                "base_url", "https://vod.volcengineapi.com",
                "api_key", "{\"accessKey\":\"db-ak\",\"secretKey\":\"db-sk\",\"space\":\"space-db\",\"application\":\"AppDb\",\"region\":\"cn-north-1\"}"
        )));

        AivideoModelConfigResolver resolver = new AivideoModelConfigResolver(jdbcTemplate, new MockEnvironment());

        AivideoModelConfigResolver.VodEditConfig config = resolver.resolveVodEditConfig();

        assertEquals("db-ak", config.accessKey());
        assertEquals("db-sk", config.secretKey());
        assertEquals("space-db", config.space());
        assertEquals("AppDb", config.application());
        assertEquals("cn-north-1", config.region());
    }

    @Test
    void resolveConfigsFallBackToLegacyEnvironmentWhenAiModelMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        MockEnvironment environment = new MockEnvironment()
                .withProperty("AIVIDEO_TTS_VOLC_APP_ID", "env-app")
                .withProperty("AIVIDEO_TTS_VOLC_ACCESS_TOKEN", "env-token")
                .withProperty("VOLCENGINE_VOD_ACCESS_KEY_ID", "env-ak")
                .withProperty("VOLCENGINE_VOD_SECRET_ACCESS_KEY", "env-sk")
                .withProperty("AIVIDEO_VOD_SPACE", "space-env");

        AivideoModelConfigResolver resolver = new AivideoModelConfigResolver(jdbcTemplate, environment);

        assertEquals("env-app", resolver.resolveTtsConfig().appId());
        assertEquals("env-token", resolver.resolveTtsConfig().accessToken());
        assertEquals("env-ak", resolver.resolveVodEditConfig().accessKey());
        assertEquals("env-sk", resolver.resolveVodEditConfig().secretKey());
        assertEquals("space-env", resolver.resolveVodEditConfig().space());
    }

    private static Map<String, Object> row(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(firstKey, firstValue);
        row.put(secondKey, secondValue);
        return row;
    }
}
