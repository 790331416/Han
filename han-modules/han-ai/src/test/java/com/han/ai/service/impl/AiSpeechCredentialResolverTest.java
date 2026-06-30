package com.han.ai.service.impl;

import com.han.ai.domain.po.AiModelPo;
import com.han.ai.mapper.AiModelMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSpeechCredentialResolverTest {

    @Test
    void resolveTtsCredentialPrefersAiModelJsonCredential() {
        AiModelMapper mapper = mock(AiModelMapper.class);
        AiModelPo model = new AiModelPo();
        model.setModelId(1L);
        model.setModelType("TTS");
        model.setApiKey("{\"appId\":\"json-app\",\"accessToken\":\"json-token\","
                + "\"cluster\":\"json-cluster\",\"endpoint\":\"https://json-endpoint\","
                + "\"defaultVoiceType\":\"json-voice\"}");
        when(mapper.selectById(1L)).thenReturn(model);

        AiSpeechCredentialResolver resolver = new AiSpeechCredentialResolver(mapper, new MockEnvironment());

        AiVolcSpeechClient.SpeechCredential credential = resolver.resolveTtsCredential(1L, null);

        assertEquals("json-app", credential.appId());
        assertEquals("json-token", credential.accessToken());
        assertEquals("json-cluster", credential.cluster());
        assertEquals("https://json-endpoint", credential.endpoint());
        assertEquals("json-voice", credential.defaultVoiceType());
        assertTrue(credential.configured());
    }

    @Test
    void resolveTtsCredentialUsesPlainApiKeyAsAccessToken() {
        AiModelMapper mapper = mock(AiModelMapper.class);
        AiModelPo model = new AiModelPo();
        model.setModelId(2L);
        model.setModelType("TTS");
        model.setApiKey("plain-token");
        model.setBaseUrl("https://model-endpoint");
        when(mapper.selectById(2L)).thenReturn(model);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("AIVIDEO_TTS_VOLC_APP_ID", "env-app");

        AiSpeechCredentialResolver resolver = new AiSpeechCredentialResolver(mapper, environment);

        AiVolcSpeechClient.SpeechCredential credential = resolver.resolveTtsCredential(2L, null);

        assertEquals("env-app", credential.appId());
        assertEquals("plain-token", credential.accessToken());
        assertEquals("https://model-endpoint", credential.endpoint());
        assertEquals("volcano_tts", credential.cluster());
        assertEquals("BV001_24k_streaming", credential.defaultVoiceType());
        assertTrue(credential.configured());
    }

    @Test
    void resolveTtsCredentialFallsBackToEnvironmentWhenModelMissing() {
        AiModelMapper mapper = mock(AiModelMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        MockEnvironment environment = new MockEnvironment()
                .withProperty("AIVIDEO_TTS_VOLC_ENDPOINT", "https://env-endpoint")
                .withProperty("AIVIDEO_TTS_VOLC_APP_ID", "env-app")
                .withProperty("AIVIDEO_TTS_VOLC_ACCESS_TOKEN", "env-token")
                .withProperty("AIVIDEO_TTS_VOLC_CLUSTER", "env-cluster")
                .withProperty("AIVIDEO_TTS_VOLC_DEFAULT_VOICE_TYPE", "env-voice");

        AiSpeechCredentialResolver resolver = new AiSpeechCredentialResolver(mapper, environment);

        AiVolcSpeechClient.SpeechCredential credential = resolver.resolveTtsCredential(null, null);

        assertEquals("env-app", credential.appId());
        assertEquals("env-token", credential.accessToken());
        assertEquals("env-cluster", credential.cluster());
        assertEquals("https://env-endpoint", credential.endpoint());
        assertEquals("env-voice", credential.defaultVoiceType());
        assertTrue(credential.configured());
    }

    @Test
    void resolveTtsCredentialAppliesDefaultsWhenNothingConfigured() {
        AiModelMapper mapper = mock(AiModelMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        AiSpeechCredentialResolver resolver = new AiSpeechCredentialResolver(mapper, new MockEnvironment());

        AiVolcSpeechClient.SpeechCredential credential = resolver.resolveTtsCredential(null, null);

        assertEquals("https://openspeech.bytedance.com/api/v1/tts", credential.endpoint());
        assertEquals("volcano_tts", credential.cluster());
        assertEquals("BV001_24k_streaming", credential.defaultVoiceType());
        assertEquals("", credential.appId());
        assertEquals("", credential.accessToken());
        assertFalse(credential.configured());
    }
}
