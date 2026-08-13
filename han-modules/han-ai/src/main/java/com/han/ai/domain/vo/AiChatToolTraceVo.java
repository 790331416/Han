package com.han.ai.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 对话消息洞察用的结构化 MCP 工具调用轨迹。
 * <p>
 * 两种形态：配置摘要（绑定的 MCP 服务与可用工具，兜底展示）与真实调用记录
 * （toolName/callArgs/callResult/costMs/callStatus 非空时为一次真实 tools/call）。
 */
@Data
public class AiChatToolTraceVo {

    private Long mcpId;

    private String serverName;

    private String transportType;

    private String status;

    private Integer toolCount;

    private List<String> toolNames;

    private String summary;

    /**
     * 真实调用的工具名（tools/call）
     */
    private String toolName;

    /**
     * 真实调用入参 JSON 摘要
     */
    private String callArgs;

    /**
     * 真实调用出参摘要
     */
    private String callResult;

    /**
     * 真实调用耗时（毫秒）
     */
    private Long costMs;

    /**
     * 真实调用状态：succeeded / failed
     */
    private String callStatus;
}
