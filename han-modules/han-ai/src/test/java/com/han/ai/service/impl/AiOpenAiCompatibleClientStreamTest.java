package com.han.ai.service.impl;

import com.han.ai.domain.po.AiModelPo;
import com.han.ai.security.AiUrlSecurityValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真流式通道行为自查：本地 HttpServer 模拟 OpenAI 兼容 SSE 供应商，
 * 覆盖逐 token 增量透传与流式 tool_calls 分片聚合-执行-回填循环。
 */
class AiOpenAiCompatibleClientStreamTest {

    private HttpServer server;
    private final List<String> capturedRequestBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private List<List<String>> sseRoundPayloads;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            capturedRequestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int round = Math.min(requestCount.getAndIncrement(), sseRoundPayloads.size() - 1);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                for (String line : sseRoundPayloads.get(round)) {
                    body.write((line + "\n\n").getBytes(StandardCharsets.UTF_8));
                    body.flush();
                }
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatCompletionStreamForwardsDeltasIncrementally() {
        sseRoundPayloads = List.of(List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"！\"}}]}",
                "data: [DONE]"
        ));
        List<String> deltas = new ArrayList<>();

        AiOpenAiCompatibleClient client = new AiOpenAiCompatibleClient(new AiUrlSecurityValidator(true, true, ""));
        String content = client.chatCompletionStream(model(), "test-key",
                List.of(AiOpenAiCompatibleClient.ProviderMessage.user("hi")), null, deltas::add);

        assertEquals(List.of("你", "好", "！"), deltas);
        assertEquals("你好！", content);
    }

    @Test
    void chatCompletionStreamWithToolsAggregatesFragmentsExecutesAndStreamsFinalReply() {
        sseRoundPayloads = List.of(
                // 第一轮：tool_calls 分片（arguments 跨 chunk 拆分）
                List.of(
                        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"ci\"}}]}}]}",
                        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"ty\\\":\\\"HZ\\\"}\"}}]}}]}",
                        "data: [DONE]"
                ),
                // 第二轮：工具结果回填后的文本流
                List.of(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"晴\"}}]}",
                        "data: {\"choices\":[{\"delta\":{\"content\":\"天\"}}]}",
                        "data: [DONE]"
                )
        );
        List<String> deltas = new ArrayList<>();
        List<String> executedToolNames = new ArrayList<>();
        List<String> executedToolArgs = new ArrayList<>();

        AiOpenAiCompatibleClient client = new AiOpenAiCompatibleClient(new AiUrlSecurityValidator(true, true, ""));
        AiOpenAiCompatibleClient.ToolLoopResult result = client.chatCompletionStreamWithTools(
                model(), "test-key",
                List.of(AiOpenAiCompatibleClient.ProviderMessage.user("杭州天气")),
                null,
                List.of(new AiOpenAiCompatibleClient.ToolSpec("lookup", "查询天气", null)),
                (toolName, argumentsJson) -> {
                    executedToolNames.add(toolName);
                    executedToolArgs.add(argumentsJson);
                    return new AiOpenAiCompatibleClient.ToolExecution("晴", true);
                },
                5,
                deltas::add);

        assertEquals(List.of("lookup"), executedToolNames);
        assertEquals(List.of("{\"city\":\"HZ\"}"), executedToolArgs);
        assertEquals(List.of("晴", "天"), deltas);
        assertEquals("晴天", result.content());
        assertEquals(1, result.executedCalls().size());
        assertTrue(result.executedCalls().get(0).success());
        assertEquals(2, capturedRequestBodies.size());
        // 第二轮请求须包含 assistant tool_calls 回填与 role=tool 的工具结果
        String secondRequest = capturedRequestBodies.get(1);
        assertTrue(secondRequest.contains("\"tool_calls\""));
        assertTrue(secondRequest.contains("\"role\":\"tool\""));
        assertTrue(secondRequest.contains("\"tool_call_id\":\"call_1\""));
    }

    private AiModelPo model() {
        AiModelPo model = new AiModelPo();
        model.setModelCode("test-llm");
        model.setProvider("test");
        model.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return model;
    }
}
