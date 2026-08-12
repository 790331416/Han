package com.han.api.ai.domain;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * Text-to-speech generation response. Audio bytes are Base64-encoded for service-to-service transport.
 */
@Data
public class AiTtsGenerateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String providerRequestId;

    private String mimeType;

    private String extension;

    private Integer durationMs;

    /**
     * Base64 编码的整段音频。
     *
     * <p>内存放大约 4 倍（byte[] → Base64 字符串 → JSON 缓冲，收发两侧各一份），
     * 几分钟的语音就是几十 MB，几个并发即可打爆堆。已排除出 {@code toString()}。
     * 目标形态是由 han-ai 直接落 han-file 后只回 fileId + url（与
     * {@link AiImageGenerateResponse} 的做法一致），本字段是过渡期方案。
     */
    @ToString.Exclude
    private String audioBase64;

    private Long modelId;

    private String provider;

    private String modelCode;
}
