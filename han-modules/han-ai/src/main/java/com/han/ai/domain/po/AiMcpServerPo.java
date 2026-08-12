package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.web.sensitive.Sensitive;
import com.han.common.web.sensitive.SensitiveType;
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

    /**
     * 附加请求头/环境变量（JSON），按设计承载 {@code Authorization: Bearer xxx} 等外部服务凭据。
     * 与 {@code AiModelPo.apiKey} 同口径脱敏，禁止明文出现在任何查询接口的返回体里。
     */
    @Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 4, suffixKeep = 4)
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
