package com.han.ai.service;

import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.domain.query.AiMcpServerQuery;
import com.han.common.core.domain.PageResult;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务管理服务。
 */
public interface IAiMcpServerService {

    /**
     * 分页查询 MCP 服务列表。
     *
     * @param query query params
     * @return page result
     */
    PageResult<AiMcpServerPo> selectPage(AiMcpServerQuery query);

    /**
     * 查询 MCP 服务详情。
     *
     * @param mcpId server id
     * @return detail
     */
    AiMcpServerPo selectById(Long mcpId);

    /**
     * 查询全部已启用的 MCP 服务。
     *
     * @return enabled list
     */
    List<AiMcpServerPo> selectAll();

    /**
     * 新增 MCP 服务。
     *
     * @param server server data
     */
    void insert(AiMcpServerPo server);

    /**
     * 修改 MCP 服务。
     *
     * @param server server data
     */
    void update(AiMcpServerPo server);

    /**
     * 删除 MCP 服务。
     *
     * @param mcpId server id
     */
    void deleteById(Long mcpId);

    /**
     * 刷新 MCP 工具元数据。
     *
     * @param mcpId server id
     * @return refresh summary
     */
    String refreshTools(Long mcpId);

    /**
     * Test connection (initialize + tools/list, no persistence).
     *
     * @param mcpId server id
     * @return test summary with tool count
     */
    String testConnection(Long mcpId);

    /**
     * 查询已存储的工具元数据列表（名称 / 描述 / inputSchema）。
     *
     * @param mcpId server id
     * @return tool metadata list
     */
    List<Map<String, Object>> listToolMetadata(Long mcpId);
}
