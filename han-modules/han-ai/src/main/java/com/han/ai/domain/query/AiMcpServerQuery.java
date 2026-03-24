package com.han.ai.domain.query;

import lombok.Data;

/**
 * MCP query.
 */
@Data
public class AiMcpServerQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String serverName;

    private String transportType;

    private String status;
}
