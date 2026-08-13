package com.han.api.ai.domain;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 视频生成任务查询响应。
 */
@Data
public class AiVideoTaskQueryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String provider;

    private String modelCode;

    private String providerTaskId;

    private String taskStatus;

    private Integer progress;

    private String videoUrl;

    private String lastFrameUrl;

    /**
     * 上游厂商的原始应答报文。
     *
     * <p>约定同 {@link AiVideoGenerateResponse#getRawResponse()}：可能含带签名的临时 URL 与
     * 厂商侧账号信息，不得整段落库或打进日志（已排除出 {@code toString()}），服务端应由开关
     * 控制是否下发。
     */
    @ToString.Exclude
    private String rawResponse;
}
