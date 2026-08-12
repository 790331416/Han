package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.web.sensitive.Sensitive;
import com.han.common.web.sensitive.SensitiveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "服务名称不能为空")
    @Size(max = 200, message = "服务名称不能超过 200 字符")
    private String serverName;

    @Size(max = 1000, message = "服务描述不能超过 1000 字符")
    private String description;

    @NotBlank(message = "传输类型不能为空")
    @Size(max = 30, message = "传输类型不能超过 30 字符")
    private String transportType;

    @Size(max = 500, message = "启动命令不能超过 500 字符")
    private String command;

    @Size(max = 10000, message = "参数 JSON 不能超过 10000 字符")
    private String args;

    /**
     * 附加请求头/环境变量（JSON），按设计承载 {@code Authorization: Bearer xxx} 等外部服务凭据。
     * 与 {@code AiModelPo.apiKey} 同口径脱敏，禁止明文出现在任何查询接口的返回体里。
     */
    @Size(max = 10000, message = "环境变量 JSON 不能超过 10000 字符")
    @Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 4, suffixKeep = 4)
    private String envVars;

    @Size(max = 500, message = "服务URL不能超过 500 字符")
    private String url;

    private String tools;

    @Pattern(regexp = "^[01]$", message = "MCP服务状态只能是 0（启用）或 1（停用）")
    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
