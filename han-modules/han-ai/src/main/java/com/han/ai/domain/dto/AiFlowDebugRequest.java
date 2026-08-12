package com.han.ai.domain.dto;

import lombok.Data;

import java.util.Map;

/**
 * 编排调试运行请求（flowConfig v2）：params 对应 start 节点自定义入参取值，
 * 未提供的入参回落节点定义的 defaultValue。
 */
@Data
public class AiFlowDebugRequest {

    private String message;

    /**
     * start 节点自定义入参取值（参数名 -> 值），可空
     */
    private Map<String, String> params;
}
