package com.han.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.security.AiUrlSecurityValidator;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 客户端（JSON-RPC 2.0 over HTTP）：面向 streamable_http（含兼容同端点 POST 的 sse 服务）
 * 提供 tools/list 与 tools/call 真实调用。
 * <p>
 * 边界：stdio 传输需容器内进程管理，本期不支持（明确报错引导切换传输方式）；
 * 旧版 HTTP+SSE 双端点（GET 订阅 + POST endpoint）服务器如不兼容同端点 POST，会得到明确错误提示。
 * server.envVars（JSON 对象）在 HTTP 传输下作为附加请求头发送（如 Authorization）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiMcpClientService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final AiUrlSecurityValidator urlSecurityValidator;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicLong requestIdSeq = new AtomicLong(1);

    /**
     * MCP 工具定义（tools/list 结果）。
     */
    record McpTool(String name, String description, Map<String, Object> inputSchema) {
    }

    /**
     * 拉取工具列表（initialize 握手 + tools/list）。
     */
    List<McpTool> listTools(AiMcpServerPo server) {
        McpSession session = openSession(server);
        Map<String, Object> result = sendRequest(server, session, "tools/list", Map.of());
        Object rawTools = result.get("tools");
        List<McpTool> tools = new ArrayList<>();
        if (rawTools instanceof List<?> toolList) {
            for (Object item : toolList) {
                if (!(item instanceof Map<?, ?> toolMap)) {
                    continue;
                }
                Object name = toolMap.get("name");
                if (name == null || !StringUtils.hasText(String.valueOf(name))) {
                    continue;
                }
                Map<String, Object> schema = new HashMap<>();
                if (toolMap.get("inputSchema") instanceof Map<?, ?> schemaMap) {
                    for (Map.Entry<?, ?> entry : schemaMap.entrySet()) {
                        schema.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                Object description = toolMap.get("description");
                tools.add(new McpTool(String.valueOf(name),
                        description != null ? String.valueOf(description) : "", schema));
            }
        }
        return tools;
    }

    /**
     * 真实工具调用（tools/call），返回 content 文本聚合；isError=true 时抛业务异常。
     */
    String callTool(AiMcpServerPo server, String toolName, Map<String, Object> arguments) {
        McpSession session = openSession(server);
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : Map.of());
        Map<String, Object> result = sendRequest(server, session, "tools/call", params);

        boolean isError = Boolean.TRUE.equals(result.get("isError"));
        StringBuilder text = new StringBuilder();
        if (result.get("content") instanceof List<?> contentList) {
            for (Object item : contentList) {
                if (item instanceof Map<?, ?> contentMap && "text".equals(contentMap.get("type"))) {
                    Object value = contentMap.get("text");
                    if (value != null) {
                        if (!text.isEmpty()) {
                            text.append('\n');
                        }
                        text.append(value);
                    }
                }
            }
        }
        String output = text.toString().trim();
        if (isError) {
            throw new BusinessException(StringUtils.hasText(output) ? output : "MCP 工具执行返回错误");
        }
        return StringUtils.hasText(output) ? output : "(工具执行成功，无文本输出)";
    }

    private record McpSession(String sessionId) {
    }

    /**
     * initialize 握手并捕获 Mcp-Session-Id（无状态服务器可能不返回，允许为空）。
     * 握手失败时按可诊断信息抛业务异常。
     */
    private McpSession openSession(AiMcpServerPo server) {
        requireHttpTransport(server);
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "han-ai", "version", "1.0"));
        RpcResponse response = postJsonRpc(server, null, buildRpcBody("initialize", params, requestIdSeq.getAndIncrement()));
        if (response.error() != null) {
            throw new BusinessException("MCP initialize 失败：" + response.error());
        }
        String sessionId = response.sessionId();
        // initialized 通知（部分服务器要求；失败不阻断，尽力而为）
        try {
            postJsonRpc(server, sessionId, XuJsonUtil.toJsonString(Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/initialized")));
        } catch (BusinessException ignored) {
            // 无状态服务器可能不接受通知，忽略
        }
        return new McpSession(sessionId);
    }

    private Map<String, Object> sendRequest(AiMcpServerPo server, McpSession session, String method,
                                            Map<String, Object> params) {
        RpcResponse response = postJsonRpc(server, session.sessionId(),
                buildRpcBody(method, params, requestIdSeq.getAndIncrement()));
        if (response.error() != null) {
            throw new BusinessException("MCP " + method + " 失败：" + response.error());
        }
        return response.result() != null ? response.result() : Map.of();
    }

    private void requireHttpTransport(AiMcpServerPo server) {
        String transport = server.getTransportType();
        if ("stdio".equals(transport)) {
            throw new BusinessException("stdio 传输暂不支持容器内真实调用，请将该 MCP 服务切换为 streamable_http");
        }
        if (!StringUtils.hasText(server.getUrl())) {
            throw new BusinessException("MCP 服务未配置 URL");
        }
        // SSRF 防护（G1-12）：连接路径兜底校验，防止保存后 DNS 指向变化或存量脏数据绕过
        urlSecurityValidator.validate(server.getUrl(), "MCP服务");
    }

    private String buildRpcBody(String method, Map<String, Object> params, long id) {
        Map<String, Object> body = new HashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.put("params", params);
        return XuJsonUtil.toJsonString(body);
    }

    private record RpcResponse(Map<String, Object> result, String error, String sessionId) {
    }

    private RpcResponse postJsonRpc(AiMcpServerPo server, String sessionId, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl().trim()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (StringUtils.hasText(sessionId)) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        applyExtraHeaders(builder, server.getEnvVars());
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 202) {
                // 通知类请求的成功应答，无响应体
                return new RpcResponse(Map.of(), null, extractSessionId(response, sessionId));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("MCP 服务响应 " + response.statusCode() + "：" + excerpt(response.body()));
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String payload = contentType.contains("text/event-stream")
                    ? extractSsePayload(response.body())
                    : response.body();
            return parseRpcPayload(payload, extractSessionId(response, sessionId));
        } catch (IOException ex) {
            log.warn("MCP request IO error, url={}", server.getUrl(), ex);
            throw new BusinessException("MCP 服务连接失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("MCP 请求被中断");
        }
    }

    private String extractSessionId(HttpResponse<String> response, String fallback) {
        return response.headers().firstValue("Mcp-Session-Id").orElse(fallback);
    }

    /**
     * SSE 响应体中提取最后一个 data: 行的 JSON-RPC 消息（tools/list、tools/call 的响应帧）。
     */
    private String extractSsePayload(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String lastData = null;
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String candidate = trimmed.substring(5).trim();
                if (candidate.startsWith("{") && (candidate.contains("\"result\"") || candidate.contains("\"error\""))) {
                    lastData = candidate;
                }
            }
        }
        if (lastData == null) {
            throw new BusinessException("MCP SSE 响应中未找到 JSON-RPC 结果帧");
        }
        return lastData;
    }

    private RpcResponse parseRpcPayload(String payload, String sessionId) {
        if (!StringUtils.hasText(payload)) {
            return new RpcResponse(Map.of(), null, sessionId);
        }
        Map<String, Object> parsed;
        try {
            parsed = XuJsonUtil.parseObject(payload, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException ex) {
            throw new BusinessException("MCP 响应不是合法 JSON-RPC：" + excerpt(payload));
        }
        if (parsed == null) {
            return new RpcResponse(Map.of(), null, sessionId);
        }
        if (parsed.get("error") instanceof Map<?, ?> errorMap) {
            Object message = errorMap.get("message");
            return new RpcResponse(null, message != null ? String.valueOf(message) : "未知错误", sessionId);
        }
        Map<String, Object> result = new HashMap<>();
        if (parsed.get("result") instanceof Map<?, ?> resultMap) {
            for (Map.Entry<?, ?> entry : resultMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return new RpcResponse(result, null, sessionId);
    }

    /**
     * envVars JSON 对象在 HTTP 传输下作为附加请求头（如 {"Authorization":"Bearer xxx"}）。
     */
    private void applyExtraHeaders(HttpRequest.Builder builder, String envVars) {
        if (!StringUtils.hasText(envVars)) {
            return;
        }
        try {
            Map<String, Object> headers = XuJsonUtil.parseObject(envVars, new TypeReference<Map<String, Object>>() {});
            if (headers == null) {
                return;
            }
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && entry.getValue() != null) {
                    builder.header(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        } catch (RuntimeException ignored) {
            // envVars 非 JSON 对象时忽略（可能是 stdio 环境变量格式）
        }
    }

    private String excerpt(String body) {
        if (!StringUtils.hasText(body)) {
            return "(空响应)";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }
}
