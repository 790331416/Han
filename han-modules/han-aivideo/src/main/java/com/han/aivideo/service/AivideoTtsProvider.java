package com.han.aivideo.service;

import java.math.BigDecimal;

/**
 * Text-to-speech provider abstraction for post-production audio assets.
 */
public interface AivideoTtsProvider {

    TtsAudio synthesize(TtsRequest request);

    record TtsRequest(String text,
                      String voiceType,
                      BigDecimal speedRatio,
                      BigDecimal volumeRatio,
                      BigDecimal pitchRatio,
                      String requestId) {
    }

    record TtsAudio(String providerRequestId,
                    String mimeType,
                    String extension,
                    Integer durationMs,
                    byte[] bytes) {
    }
}
