package com.han.api.ai.domain;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 供应商返回的单张候选图片。
 */
@Data
public class AiImageCandidate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer index;

    private String url;

    /**
     * Base64 编码的图片内容（不含 {@code data:} 前缀）。
     *
     * <p>体积敏感：Base64 会让载荷膨胀约 33%，收发两侧还各要一份 JSON 缓冲。
     * 优先使用 {@link #url}，只有拿不到可访问地址时才回落到本字段。
     * 已排除出 {@code toString()}，避免整张图被打进日志。
     */
    @ToString.Exclude
    private String base64Data;

    private String mimeType;

    private String revisedPrompt;
}
