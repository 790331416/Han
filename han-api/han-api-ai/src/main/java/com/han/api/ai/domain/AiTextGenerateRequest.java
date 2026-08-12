package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Text generation request.
 */
@Data
public class AiTextGenerateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 调用方自报的租户ID。
     *
     * @deprecated 服务端必须以内部签名覆盖的 {@code X-Tenant-Id} 请求头为准；本字段只在头透传
     *         能力就位前作为回退，服务端无法校验其真伪。两者都取不到时必须 fail-close，
     *         只放行平台级模型。完整约定见 {@link com.han.api.ai.AiServiceClient}。
     */
    @Deprecated
    private Long tenantId;

    /** 模型ID。为空时服务端按租户可见范围挑选默认文本模型。 */
    private Long modelId;

    private Long promptTemplateId;

    private String systemPrompt;

    private String userPrompt;

    private String customPrompt;

    private Map<String, String> variables;

    private Integer maxTokens;
}
