package com.han.ai.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * Structured MCP tool trace summary for chat message insight.
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
}
