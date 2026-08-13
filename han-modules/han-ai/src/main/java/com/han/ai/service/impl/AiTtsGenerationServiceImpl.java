package com.han.ai.service.impl;

import com.han.ai.service.IAiTtsGenerationService;
import com.han.api.ai.domain.AiTtsGenerateRequest;
import com.han.api.ai.domain.AiTtsGenerateResponse;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;

/**
 * 基于火山引擎的语音合成实现。
 */
@Service
@RequiredArgsConstructor
public class AiTtsGenerationServiceImpl implements IAiTtsGenerationService {

    private static final String PROVIDER_VOLCENGINE = "VOLCENGINE";

    private final AiSpeechCredentialResolver speechCredentialResolver;
    private final AiVolcSpeechClient volcSpeechClient;

    @Override
    public AiTtsGenerateResponse synthesize(AiTtsGenerateRequest request) {
        if (request == null || !StringUtils.hasText(request.getText())) {
            throw new BusinessException("TTS文本不能为空");
        }
        AiVolcSpeechClient.SpeechCredential credential =
                speechCredentialResolver.resolveTtsCredential(request.getModelId(), request.getTenantId());
        AiVolcSpeechClient.SpeechResult result = volcSpeechClient.synthesize(
                credential,
                new AiVolcSpeechClient.SpeechRequest(
                        request.getText(),
                        request.getVoiceType(),
                        request.getSpeedRatio(),
                        request.getVolumeRatio(),
                        request.getPitchRatio(),
                        request.getRequestId()));
        AiTtsGenerateResponse response = new AiTtsGenerateResponse();
        response.setProviderRequestId(result.providerRequestId());
        response.setMimeType(result.mimeType());
        response.setExtension(result.extension());
        response.setDurationMs(result.durationMs());
        response.setAudioBase64(result.bytes() == null ? null : Base64.getEncoder().encodeToString(result.bytes()));
        response.setModelId(request.getModelId());
        response.setProvider(PROVIDER_VOLCENGINE);
        return response;
    }
}
