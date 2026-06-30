package com.han.ai.service.impl;

import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiVolcSpeechClientTest {

    private final AiVolcSpeechClient client = new AiVolcSpeechClient();

    @Test
    void synthesizeRejectsUnconfiguredCredential() {
        AiVolcSpeechClient.SpeechRequest request =
                new AiVolcSpeechClient.SpeechRequest("hello", null, null, null, null, null);

        assertThrows(BusinessException.class, () -> client.synthesize(null, request));

        AiVolcSpeechClient.SpeechCredential blank =
                new AiVolcSpeechClient.SpeechCredential("https://endpoint", "", "", "cluster", "voice");
        assertThrows(BusinessException.class, () -> client.synthesize(blank, request));
    }

    @Test
    void synthesizeRejectsBlankText() {
        AiVolcSpeechClient.SpeechCredential credential =
                new AiVolcSpeechClient.SpeechCredential("https://endpoint", "app", "token", "cluster", "voice");
        AiVolcSpeechClient.SpeechRequest request =
                new AiVolcSpeechClient.SpeechRequest("   ", null, null, null, null, null);

        assertThrows(BusinessException.class, () -> client.synthesize(credential, request));
    }

    @Test
    void speechCredentialConfiguredRequiresAppIdTokenEndpoint() {
        assertTrue(new AiVolcSpeechClient.SpeechCredential("https://endpoint", "app", "token", "cluster", "voice").configured());
        assertFalse(new AiVolcSpeechClient.SpeechCredential("https://endpoint", "", "token", "cluster", "voice").configured());
        assertFalse(new AiVolcSpeechClient.SpeechCredential("https://endpoint", "app", "", "cluster", "voice").configured());
        assertFalse(new AiVolcSpeechClient.SpeechCredential("", "app", "token", "cluster", "voice").configured());
    }
}
