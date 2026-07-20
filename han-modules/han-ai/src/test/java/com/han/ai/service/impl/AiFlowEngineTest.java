package com.han.ai.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 编排引擎纯逻辑单测（不触发真实模型/检索/MCP 调用）：
 * G1-2 llm 节点记忆轮数注入。
 */
class AiFlowEngineTest {

    @Test
    void memoryRoundsDefaultsToFourAndClampsToTwenty() throws Exception {
        AiFlowEngine engine = newEngine();
        assertEquals(4, resolveMemoryRounds(engine, dataOf(null)));
        assertEquals(2, resolveMemoryRounds(engine, dataOf(2)));
        assertEquals(0, resolveMemoryRounds(engine, dataOf(-1)));
        assertEquals(20, resolveMemoryRounds(engine, dataOf(99)));
    }

    @Test
    void appendMemoryTurnsTakesLatestRoundsOnly() throws Exception {
        AiFlowEngine engine = newEngine();
        List<AiFlowEngine.FlowChatTurn> history = List.of(
                new AiFlowEngine.FlowChatTurn("user", "第一问"),
                new AiFlowEngine.FlowChatTurn("assistant", "第一答"),
                new AiFlowEngine.FlowChatTurn("user", "第二问"),
                new AiFlowEngine.FlowChatTurn("assistant", "第二答"),
                new AiFlowEngine.FlowChatTurn("system", "应被忽略"),
                new AiFlowEngine.FlowChatTurn("user", "第三问"),
                new AiFlowEngine.FlowChatTurn("assistant", "第三答")
        );
        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();

        appendMemoryTurns(engine, messages, history, 2);

        // 最近 2 轮 = 末尾 4 条（system 角色被忽略）
        assertEquals(3, messages.size());
        assertEquals("第二答", messages.get(0).content());
        assertEquals("第三问", messages.get(1).content());
        assertEquals("第三答", messages.get(2).content());
    }

    @Test
    void appendMemoryTurnsSkippedWhenRoundsIsZero() throws Exception {
        AiFlowEngine engine = newEngine();
        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();

        appendMemoryTurns(engine, messages,
                List.of(new AiFlowEngine.FlowChatTurn("user", "问"),
                        new AiFlowEngine.FlowChatTurn("assistant", "答")), 0);

        assertEquals(0, messages.size());
    }

    // ==================== G1-3 变量系统 v2 ====================

    @Test
    void renderTemplateResolvesFlatVarsAndNodeOutputRefs() throws Exception {
        AiFlowEngine engine = newEngine();
        Map<String, String> vars = new HashMap<>();
        vars.put("message", "用户问题");
        vars.put("node_2", "检索结果文本");
        vars.put("city", "杭州");

        String rendered = renderTemplate(engine, "问：{{message}}；城市：{{ city }}；上游：{{node_2.output}}；未知：{{nope}}{{node_2.foo}}", vars);

        assertEquals("问：用户问题；城市：杭州；上游：检索结果文本；未知：", rendered);
    }

    @Test
    void startInputParamsInjectProvidedValueOverDefaultAndProtectReservedNames() throws Exception {
        AiFlowEngine engine = newEngine();
        Map<String, Object> data = new HashMap<>();
        data.put("inputParams", List.of(
                Map.of("name", "city", "defaultValue", "北京"),
                Map.of("name", "topic", "defaultValue", "短剧"),
                Map.of("name", "message", "defaultValue", "禁止覆盖保留变量"),
                Map.of("name", " ", "defaultValue", "空名忽略")
        ));
        AiFlowGraph.FlowNode startNode = new AiFlowGraph.FlowNode("node_1", "start", data);
        Map<String, String> vars = new HashMap<>();
        vars.put("message", "原始消息");

        Method method = AiFlowEngine.class.getDeclaredMethod("applyStartInputParams",
                AiFlowGraph.FlowNode.class, Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(engine, startNode, Map.of("city", "杭州"), vars);

        assertEquals("杭州", vars.get("city"));
        assertEquals("短剧", vars.get("topic"));
        assertEquals("原始消息", vars.get("message"));
    }

    private String renderTemplate(AiFlowEngine engine, String template, Map<String, String> vars) throws Exception {
        Method method = AiFlowEngine.class.getDeclaredMethod("renderTemplate", String.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(engine, template, vars);
    }

    private AiFlowEngine newEngine() throws Exception {
        Constructor<AiFlowEngine> constructor = AiFlowEngine.class.getDeclaredConstructor(
                com.han.ai.mapper.AiModelMapper.class,
                com.han.ai.mapper.AiMcpServerMapper.class,
                AiModelCredentialResolver.class,
                AiOpenAiCompatibleClient.class,
                com.han.ai.service.IAiKnowledgeRetrievalService.class,
                AiMcpClientService.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, null, null, null, null);
    }

    private AiFlowGraph.FlowNode nodeOf(Map<String, Object> data) {
        return new AiFlowGraph.FlowNode("node_1", "llm", data);
    }

    private Map<String, Object> dataOf(Integer memoryRounds) {
        Map<String, Object> data = new HashMap<>();
        if (memoryRounds != null) {
            data.put("memoryRounds", memoryRounds);
        }
        return data;
    }

    private int resolveMemoryRounds(AiFlowEngine engine, Map<String, Object> data) throws Exception {
        Method method = AiFlowEngine.class.getDeclaredMethod("resolveMemoryRounds", AiFlowGraph.FlowNode.class);
        method.setAccessible(true);
        return (int) method.invoke(engine, nodeOf(data));
    }

    @SuppressWarnings("unchecked")
    private void appendMemoryTurns(AiFlowEngine engine, List<AiOpenAiCompatibleClient.ProviderMessage> messages,
                                   List<AiFlowEngine.FlowChatTurn> history, int rounds) throws Exception {
        Method method = AiFlowEngine.class.getDeclaredMethod("appendMemoryTurns", List.class, List.class, int.class);
        method.setAccessible(true);
        try {
            method.invoke(engine, messages, history, rounds);
        } catch (InvocationTargetException ex) {
            throw (Exception) ex.getCause();
        }
    }
}
