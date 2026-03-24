package com.han.ai.service;

import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.domain.query.AiMcpServerQuery;
import com.han.common.core.domain.PageResult;

import java.util.List;

/**
 * MCP server service.
 */
public interface IAiMcpServerService {

    /**
     * Query paged MCP server list.
     *
     * @param query query params
     * @return page result
     */
    PageResult<AiMcpServerPo> selectPage(AiMcpServerQuery query);

    /**
     * Query MCP server detail.
     *
     * @param mcpId server id
     * @return detail
     */
    AiMcpServerPo selectById(Long mcpId);

    /**
     * Query all enabled MCP servers.
     *
     * @return enabled list
     */
    List<AiMcpServerPo> selectAll();

    /**
     * Insert MCP server.
     *
     * @param server server data
     */
    void insert(AiMcpServerPo server);

    /**
     * Update MCP server.
     *
     * @param server server data
     */
    void update(AiMcpServerPo server);

    /**
     * Delete MCP server.
     *
     * @param mcpId server id
     */
    void deleteById(Long mcpId);

    /**
     * Refresh MCP tool metadata.
     *
     * @param mcpId server id
     * @return refresh summary
     */
    String refreshTools(Long mcpId);
}
