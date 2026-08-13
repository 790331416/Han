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
import java.util.Map;

/**
 * MCP 服务控制器。
 */
@AdminAuth
@RestController
@RequestMapping("/ai/mcp")
@RequiredArgsConstructor
public class AiMcpController {

    private final IAiMcpServerService aiMcpServerService;

    /**
     * 分页查询 MCP 服务列表。
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
     * 查询 MCP 服务详情。
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
     * 查询全部已启用的 MCP 服务。
     *
     * @return server list
     */
    @GetMapping("/all")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:list')")
    public R<List<AiMcpServerPo>> listAll() {
        return R.ok(aiMcpServerService.selectAll());
    }

    /**
     * 新增 MCP 服务。
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
     * 修改 MCP 服务。
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
     * 删除 MCP 服务。
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
     * 刷新 MCP 工具元数据。
     *
     * @param mcpId server id
     * @return refresh message
     */
    @PostMapping("/refresh/{mcpId}")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:edit')")
    public R<String> refresh(@PathVariable Long mcpId) {
        return R.ok(aiMcpServerService.refreshTools(mcpId));
    }

    /**
     * Test MCP connection (initialize + tools/list, no persistence).
     *
     * @param mcpId server id
     * @return test message with tool count
     */
    @PostMapping("/test/{mcpId}")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:query')")
    public R<String> testConnection(@PathVariable Long mcpId) {
        return R.ok(aiMcpServerService.testConnection(mcpId));
    }

    /**
     * 查询已存储的工具元数据列表（供编排设计器的工具下拉与参数表单使用）。
     *
     * @param mcpId server id
     * @return tool metadata list
     */
    @GetMapping("/tools/{mcpId}")
    @PreAuthorize("@ss.hasAuthority('ai:mcp:query')")
    public R<List<Map<String, Object>>> listTools(@PathVariable Long mcpId) {
        return R.ok(aiMcpServerService.listToolMetadata(mcpId));
    }
}
