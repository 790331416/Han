package com.han.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 编排调试入参（flowConfig v2）：params 对应 start 节点自定义入参取值，
 * 未提供的以位于节点定义里的 defaultValue 兜底。
 */
@Data
public class AiFlowDebugRequest {

    @NotBlank(message = "调试输入不能为空")
    @Size(max = 20000, message = "调试输入不能超过 20000 字符")
    private String message;

    /**
     * start 节点自定义入参取值（参数名 -> 值），可空
     */
    @Size(max = 50, message = "自定义入参最多 50 个")
    private Map<String, String> params;
}
