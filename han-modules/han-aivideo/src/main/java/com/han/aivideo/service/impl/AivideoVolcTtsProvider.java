package com.han.aivideo.service.impl;

import com.han.aivideo.service.AivideoTtsProvider;
import com.han.api.ai.AiServiceClient;
import com.han.api.ai.domain.AiTtsGenerateRequest;
import com.han.api.ai.domain.AiTtsGenerateResponse;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;

/**
 * Text-to-speech provider backed by the shared han-ai speech capability.
 * <p>
 * The Volcengine speech client now lives in han-ai; this provider only adapts the
 * aivideo business contract ({@link TtsRequest}/{@link TtsAudio}) to the inner AI service call,
 * so generic model access no longer lives in the business module.
 */
@Service
@RequiredArgsConstructor
public class AivideoVolcTtsProvider implements AivideoTtsProvider {

    private final AiServiceClient aiServiceClient;

    @Override
    public TtsAudio synthesize(TtsRequest request) {
        if (request == null || !StringUtils.hasText(request.text())) {
            throw new BusinessException("TTS文本不能为空");
        }
        AiTtsGenerateRequest generateRequest = new AiTtsGenerateRequest();
        generateRequest.setText(request.text());
        generateRequest.setVoiceType(request.voiceType());
        generateRequest.setSpeedRatio(request.speedRatio());
        generateRequest.setVolumeRatio(request.volumeRatio());
        generateRequest.setPitchRatio(request.pitchRatio());
        generateRequest.setRequestId(request.requestId());

        R<AiTtsGenerateResponse> result = aiServiceClient.generateTts(generateRequest);
        if (result == null || result.isFail()) {
            throw new BusinessException(result == null ? "火山语音合成服务无响应" : result.getMsg());
        }
        AiTtsGenerateResponse data = result.getData();
        if (data == null || !StringUtils.hasText(data.getAudioBase64())) {
            throw new BusinessException("火山语音合成未返回音频数据");
        }
        byte[] bytes = Base64.getDecoder().decode(data.getAudioBase64());
        return new TtsAudio(
                StringUtils.hasText(data.getProviderRequestId()) ? data.getProviderRequestId() : request.requestId(),
                StringUtils.hasText(data.getMimeType()) ? data.getMimeType() : "audio/mpeg",
                StringUtils.hasText(data.getExtension()) ? data.getExtension() : "mp3",
                data.getDurationMs(),
                bytes);
    }
}
