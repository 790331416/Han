package com.han.ai.service.impl;

import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.mapper.AiMcpServerMapper;
import com.han.ai.security.AiUrlSecurityValidator;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 服务管理单测（G1-5）：stdio 保存拦截（决策 D4）、测试连接（真连不落库）、
 * 工具元数据清单（designer 工具下拉数据源）。
 */
class AiMcpServerServiceImplTest {

    private AiMcpServerMapper mapper;
    private AiMcpClientService clientService;
    private AiMcpServerServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiMcpServerMapper.class);
        clientService = mock(AiMcpClientService.class);
        AiUrlSecurityValidator validator = mock(AiUrlSecurityValidator.class);
        service = new AiMcpServerServiceImpl(mapper, clientService, validator);
    }

    private AiMcpServerPo remoteServer(Long mcpId) {
        AiMcpServerPo server = new AiMcpServerPo();
        server.setMcpId(mcpId);
        server.setServerName("回归工具服务");
        server.setTransportType("streamable_http");
        server.setUrl("https://mcp.example.com/mcp");
        server.setStatus("0");
        return server;
    }

    // ---------- stdio 保存拦截（决策 D4：保留字段、保存层拒绝） ----------

    @Test
    void insertRejectsStdioTransport() {
        AiMcpServerPo server = new AiMcpServerPo();
        server.setServerName("本地工具服务");
        server.setTransportType("stdio");
        server.setCommand("npx");
        server.setStatus("0");

        assertThatThrownBy(() -> service.insert(server))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("stdio 传输暂不可用");
        Mockito.verify(mapper, Mockito.never()).insert(any(AiMcpServerPo.class));
    }

    @Test
    void updateRejectsSwitchingToStdio() {
        AiMcpServerPo existing = remoteServer(7L);
        when(mapper.selectById(7L)).thenReturn(existing);

        AiMcpServerPo incoming = remoteServer(7L);
        incoming.setTransportType("stdio");
        incoming.setCommand("uvx");

        assertThatThrownBy(() -> service.update(incoming))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("stdio 传输暂不可用");
        Mockito.verify(mapper, Mockito.never()).updateById(any(AiMcpServerPo.class));
    }

    // ---------- 测试连接（initialize + tools/list，不落库） ----------

    @Test
    void testConnectionReturnsToolCountWithoutPersisting() {
        AiMcpServerPo server = remoteServer(3L);
        when(mapper.selectById(3L)).thenReturn(server);
        when(clientService.listTools(server)).thenReturn(List.of(
                new AiMcpClientService.McpTool("search", "搜索", Map.of()),
                new AiMcpClientService.McpTool("fetch", "抓取", Map.of())));

        String message = service.testConnection(3L);

        assertThat(message).contains("连接成功").contains("2");
        Mockito.verify(mapper, Mockito.never()).updateById(any(AiMcpServerPo.class));
    }

    @Test
    void testConnectionPropagatesDiagnosableFailure() {
        AiMcpServerPo server = remoteServer(4L);
        when(mapper.selectById(4L)).thenReturn(server);
        when(clientService.listTools(server)).thenThrow(new BusinessException(
                "MCP服务URL指向内网地址（192.168.1.10），已被安全策略拒绝；如确需放行请配置 han.ai.ssrf.allowed-hosts 白名单（部署环境变量 AI_SSRF_ALLOWED_HOSTS）"));

        assertThatThrownBy(() -> service.testConnection(4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被安全策略拒绝")
                .hasMessageContaining("AI_SSRF_ALLOWED_HOSTS");
    }

    @Test
    void testConnectionRejectsMissingServer() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.testConnection(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP服务不存在");
    }

    // ---------- 工具元数据清单（designer 工具下拉数据源） ----------

    @Test
    void listToolMetadataParsesStoredTools() {
        AiMcpServerPo server = remoteServer(5L);
        server.setTools("[{\"name\":\"search\",\"description\":\"搜索\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}}]");
        when(mapper.selectById(5L)).thenReturn(server);

        List<Map<String, Object>> tools = service.listToolMetadata(5L);

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name")).isEqualTo("search");
        assertThat(tools.get(0).get("inputSchema")).isInstanceOf(Map.class);
    }

    @Test
    void listToolMetadataFallsBackToEmptyOnBlankOrInvalidJson() {
        AiMcpServerPo blank = remoteServer(6L);
        blank.setTools("");
        when(mapper.selectById(6L)).thenReturn(blank);
        assertThat(service.listToolMetadata(6L)).isEmpty();

        AiMcpServerPo invalid = remoteServer(8L);
        invalid.setTools("not-json");
        when(mapper.selectById(8L)).thenReturn(invalid);
        assertThat(service.listToolMetadata(8L)).isEmpty();
    }
}
