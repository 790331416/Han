package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP server config.
 */
@Data
@TableName("ai_mcp_server")
public class AiMcpServerPo {

    @TableId(value = "mcp_id", type = IdType.AUTO)
    private Long mcpId;

    private String serverName;

    private String description;

    private String transportType;

    private String command;

    private String args;

    private String envVars;

    private String url;

    private String tools;

    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
