package com.han.ai.controller;

import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.domain.query.AiMcpServerQuery;
import com.han.ai.service.IAiMcpServerService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP server controller.
 */
@AdminAuth
@RestController
@RequestMapping("/ai/mcp")
@RequiredArgsConstructor
public class AiMcpController {

    private final IAiMcpServerService aiMcpServerService;

    /**
     * Query paged MCP server list.
     *
     * @param query query params
     * @return page result
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:list')")
    public R<PageResult<AiMcpServerPo>> list(AiMcpServerQuery query) {
        return R.ok(aiMcpServerService.selectPage(query));
    }

    /**
     * Query MCP server detail.
     *
     * @param mcpId server id
     * @return detail
     */
    @GetMapping("/{mcpId}")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:query')")
    public R<AiMcpServerPo> getInfo(@PathVariable Long mcpId) {
        return R.ok(aiMcpServerService.selectById(mcpId));
    }

    /**
     * Query all enabled MCP servers.
     *
     * @return server list
     */
    @GetMapping("/all")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:list')")
    public R<List<AiMcpServerPo>> listAll() {
        return R.ok(aiMcpServerService.selectAll());
    }

    /**
     * Create MCP server.
     *
     * @param server server data
     * @return result
     */
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('ai:mcp:add')")
    public R<Void> add(@Valid @RequestBody AiMcpServerPo server) {
        aiMcpServerService.insert(server);
        return R.ok();
    }

    /**
     * Update MCP server.
     *
     * @param server server data
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:edit')")
    public R<Void> edit(@Valid @RequestBody AiMcpServerPo server) {
        aiMcpServerService.update(server);
        return R.ok();
    }

    /**
     * Delete MCP server.
     *
     * @param mcpId server id
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/remove/{mcpId}")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:remove')")
    public R<Void> remove(@PathVariable Long mcpId) {
        aiMcpServerService.deleteById(mcpId);
        return R.ok();
    }

    /**
     * Refresh MCP tool metadata.
     *
     * @param mcpId server id
     * @return refresh message
     */
    @PostMapping("/refresh/{mcpId}")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:edit')")
    public R<String> refresh(@PathVariable Long mcpId) {
        return R.ok(aiMcpServerService.refreshTools(mcpId));
    }
}
