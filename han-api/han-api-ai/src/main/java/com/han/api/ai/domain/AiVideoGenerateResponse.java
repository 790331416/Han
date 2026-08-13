package com.han.api.ai.domain;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 内部服务的视频生成响应。
 */
@Data
public class AiVideoGenerateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String provider;

    private String modelCode;

    private String prompt;

    private String providerTaskId;

    private String taskStatus;

    private Integer progress;

    private String videoUrl;

    private String lastFrameUrl;

    /**
     * 上游厂商的原始应答报文。
     *
     * <p>可能含带签名的临时 URL、厂商 requestId、账号维度的配额信息。调用方不得整段落库或
     * 打进日志（已排除出 {@code toString()}）。服务端应当由开关控制是否下发，默认不返回，
     * 只在排障时按需打开。
     */
    @ToString.Exclude
    private String rawResponse;
}
