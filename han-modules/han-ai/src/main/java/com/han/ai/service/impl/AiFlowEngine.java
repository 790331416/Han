package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.vo.AiFlowNodeTraceVo;
import com.han.ai.mapper.AiMcpServerMapper;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.service.IAiKnowledgeRetrievalService;
import com.han.ai.service.IAiKnowledgeRetrievalService.ScoredParagraph;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * advanced 编排执行引擎：按画布拓扑逐节点执行（start/llm/knowledge/condition/tool/output/end），
 * 产出最终回复与全链节点轨迹。单节点失败 fail-fast，未到达分支节点标记 skipped。
 * <p>
 * 边界：节点数上限见 {@link AiFlowGraph#MAX_NODES}，全流执行超时 {@link #MAX_FLOW_MILLIS}。
 * tool 节点通过 MCP 客户端真实调用，调用结果写入 result 供后续节点消费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiFlowEngine {

    private static final long MAX_FLOW_MILLIS = 5 * 60_000L;
    private static final int TRACE_TEXT_LIMIT = 800;
    /** llm 节点默认记忆轮数（1 轮 = 一问一答），节点属性 memoryRounds 可覆盖 */
    private static final int DEFAULT_LLM_MEMORY_ROUNDS = 4;
    private static final int MAX_LLM_MEMORY_ROUNDS = 20;
    /** 引擎保留变量名：start 自定义入参不得覆盖 */
    private static final Set<String> RESERVED_VAR_NAMES = Set.of("message", "result", "knowledge");
    /** 变量引用语法：{{name}} 或 {{nodeId.output}}（结构化引用，flowConfig v2） */
    private static final java.util.regex.Pattern VAR_REF_PATTERN =
            java.util.regex.Pattern.compile("\\{\\{\\s*([\\w-]+)(?:\\.([\\w-]+))?\\s*}}");

    private final AiModelMapper aiModelMapper;
    private final AiMcpServerMapper aiMcpServerMapper;
    private final AiModelCredentialResolver credentialResolver;
    private final AiOpenAiCompatibleClient openAiCompatibleClient;
    private final IAiKnowledgeRetrievalService knowledgeRetrievalService;
    private final AiMcpClientService aiMcpClientService;

    /**
     * 执行结果：失败时 finalText 为 null，errorMessage 给出原因；traces 始终保留已产生的轨迹。
     */
    record FlowResult(boolean success, String finalText, List<AiFlowNodeTraceVo> traces, String errorMessage) {
    }

    /**
     * 编排执行事件监听（SSE node_start / node_delta / node_end 协议来源）：
     * onNodeDelta 仅 llm 节点逐 token 触发；回调异常不得中断编排（由调用方自行兜底）。
     */
    interface FlowEventListener {

        default void onNodeStart(AiFlowGraph.FlowNode node) {
        }

        default void onNodeDelta(String nodeId, String delta) {
        }

        default void onNodeEnd(AiFlowNodeTraceVo trace) {
        }
    }

    /**
     * 编排会话历史轮次（role=user/assistant），供 llm 节点按记忆轮数注入。
     */
    record FlowChatTurn(String role, String content) {
    }

    /**
     * 执行编排；本方法不抛业务异常，解析/执行失败均折叠进 FlowResult。
     */
    FlowResult execute(String flowConfig, String userMessage) {
        return execute(flowConfig, userMessage, null);
    }

    /**
     * 执行编排（带事件监听）：节点开始/llm 逐 token/节点结束实时回调 listener。
     */
    FlowResult execute(String flowConfig, String userMessage, FlowEventListener listener) {
        return execute(flowConfig, userMessage, List.of(), listener);
    }

    /**
     * 执行编排（带会话历史）：history 为当前消息之前的对话轮次，
     * llm 节点按节点级「记忆轮数」（默认 {@link #DEFAULT_LLM_MEMORY_ROUNDS}）截取注入，多轮追问不失忆。
     */
    FlowResult execute(String flowConfig, String userMessage, List<FlowChatTurn> history, FlowEventListener listener) {
        return execute(flowConfig, userMessage, history, null, listener);
    }

    /**
     * 执行编排（全参）：inputParams 为 start 节点自定义入参的调用方取值
     * （flowConfig v2，缺省回落各入参 defaultValue；v1 画布无入参定义，行为不变）。
     */
    FlowResult execute(String flowConfig, String userMessage, List<FlowChatTurn> history,
                       Map<String, String> inputParams, FlowEventListener listener) {
        AiFlowGraph graph;
        try {
            graph = AiFlowGraph.parse(flowConfig);
        } catch (BusinessException ex) {
            return new FlowResult(false, null, List.of(), ex.getMessage());
        }
        List<FlowChatTurn> safeHistory = history == null ? List.of() : history;

        long startedAt = System.currentTimeMillis();
        Map<String, String> vars = new HashMap<>();
        vars.put("message", userMessage == null ? "" : userMessage);
        vars.put("result", "");
        vars.put("knowledge", "");
        applyStartInputParams(graph.startNode(), inputParams, vars);

        List<AiFlowNodeTraceVo> traces = new ArrayList<>();
        Set<String> reachable = new HashSet<>();
        reachable.add(graph.startNode().id());
        String lastOutputText = null;
        String lastLlmText = null;
        String failure = null;

        for (AiFlowGraph.FlowNode node : graph.topologicalOrder()) {
            if (!reachable.contains(node.id())) {
                recordTrace(traces, listener, buildTrace(node, "skipped", null, "未命中执行分支", 0L, null));
                continue;
            }
            if (failure != null) {
                recordTrace(traces, listener, buildTrace(node, "skipped", null, "前序节点失败，已中断", 0L, null));
                continue;
            }
            if (System.currentTimeMillis() - startedAt > MAX_FLOW_MILLIS) {
                failure = "编排执行超时（超过 5 分钟）";
                recordTrace(traces, listener, buildTrace(node, "failed", null, null, 0L, failure));
                continue;
            }
            notifyNodeStart(listener, node);
            long nodeStart = System.currentTimeMillis();
            try {
                NodeOutcome outcome = executeNode(node, vars, safeHistory, listener);
                long cost = System.currentTimeMillis() - nodeStart;
                recordTrace(traces, listener, buildTrace(node, outcome.status(), outcome.input(), outcome.output(), cost, null));
                if (StringUtils.hasText(outcome.varValue())) {
                    vars.put(node.id(), outcome.varValue());
                }
                switch (node.type()) {
                    case "llm" -> {
                        vars.put("result", outcome.varValue());
                        lastLlmText = outcome.varValue();
                    }
                    case "tool" -> vars.put("result", outcome.varValue());
                    case "knowledge" -> vars.put("knowledge", outcome.varValue());
                    case "output" -> lastOutputText = outcome.varValue();
                    default -> { }
                }
                expandReachable(graph, node, outcome, reachable);
            } catch (BusinessException ex) {
                long cost = System.currentTimeMillis() - nodeStart;
                failure = "节点「" + node.label() + "」执行失败：" + ex.getMessage();
                recordTrace(traces, listener, buildTrace(node, "failed", null, null, cost, ex.getMessage()));
            } catch (RuntimeException ex) {
                long cost = System.currentTimeMillis() - nodeStart;
                log.warn("Flow node execution error, nodeId={}, type={}", node.id(), node.type(), ex);
                failure = "节点「" + node.label() + "」执行异常";
                recordTrace(traces, listener, buildTrace(node, "failed", null, null, cost, "执行异常：" + ex.getClass().getSimpleName()));
            }
        }

        if (failure != null) {
            return new FlowResult(false, null, traces, failure);
        }
        String finalText = StringUtils.hasText(lastOutputText) ? lastOutputText
                : StringUtils.hasText(lastLlmText) ? lastLlmText
                : "编排执行完成，但未产生文本输出（请为流程添加 LLM 或输出节点）";
        return new FlowResult(true, finalText, traces, null);
    }

    /**
     * 节点产出：status 恒为 succeeded/skipped（失败走异常）；varValue 是写入变量表的完整文本。
     */
    private record NodeOutcome(String status, String input, String output, String varValue, Boolean conditionResult) {

        static NodeOutcome succeeded(String input, String output, String varValue) {
            return new NodeOutcome("succeeded", input, output, varValue, null);
        }

        static NodeOutcome condition(String input, boolean result) {
            return new NodeOutcome("succeeded", input, result ? "true（走「是」分支）" : "false（走「否」分支）",
                    String.valueOf(result), result);
        }

        static NodeOutcome skipped(String reason) {
            return new NodeOutcome("skipped", null, reason, "", null);
        }
    }

    private NodeOutcome executeNode(AiFlowGraph.FlowNode node, Map<String, String> vars,
                                    List<FlowChatTurn> history, FlowEventListener listener) {
        return switch (node.type()) {
            case "start" -> NodeOutcome.succeeded(null, vars.get("message"), vars.get("message"));
            case "knowledge" -> executeKnowledge(node, vars);
            case "llm" -> executeLlm(node, vars, history, listener);
            case "condition" -> executeCondition(node, vars);
            case "tool" -> executeTool(node, vars);
            case "output" -> executeOutput(node, vars);
            case "end" -> NodeOutcome.succeeded(null, "流程结束", "");
            default -> throw new BusinessException("不支持的节点类型：" + node.type());
        };
    }

    /**
     * start 节点自定义入参注入（flowConfig v2）：inputParams 定义 [{name, defaultValue}]，
     * 取值优先级：调用方传值 > defaultValue > 空串；保留变量名不可覆盖。
     */
    private void applyStartInputParams(AiFlowGraph.FlowNode startNode, Map<String, String> inputParams,
                                       Map<String, String> vars) {
        Object raw = startNode.data() != null ? startNode.data().get("inputParams") : null;
        if (!(raw instanceof List<?> paramList)) {
            return;
        }
        for (Object item : paramList) {
            if (!(item instanceof Map<?, ?> param)) {
                continue;
            }
            Object rawName = param.get("name");
            String name = rawName != null ? String.valueOf(rawName).trim() : "";
            if (!StringUtils.hasText(name) || RESERVED_VAR_NAMES.contains(name)) {
                continue;
            }
            String provided = inputParams != null ? inputParams.get(name) : null;
            Object rawDefault = param.get("defaultValue");
            String defaultValue = rawDefault != null ? String.valueOf(rawDefault) : "";
            vars.put(name, StringUtils.hasText(provided) ? provided : defaultValue);
        }
    }

    private void notifyNodeStart(FlowEventListener listener, AiFlowGraph.FlowNode node) {
        if (listener == null) {
            return;
        }
        try {
            listener.onNodeStart(node);
        } catch (RuntimeException ex) {
            log.debug("Flow node start listener error, nodeId={}", node.id(), ex);
        }
    }

    private void recordTrace(List<AiFlowNodeTraceVo> traces, FlowEventListener listener, AiFlowNodeTraceVo trace) {
        traces.add(trace);
        if (listener == null) {
            return;
        }
        try {
            listener.onNodeEnd(trace);
        } catch (RuntimeException ex) {
            log.debug("Flow node end listener error, nodeId={}", trace.getNodeId(), ex);
        }
    }

    private NodeOutcome executeKnowledge(AiFlowGraph.FlowNode node, Map<String, String> vars) {
        Long kbId = node.dataLong("kbId");
        if (kbId == null) {
            throw new BusinessException("知识库节点未选择知识库");
        }
        Integer topK = node.dataInt("topK");
        String query = StringUtils.hasText(vars.get("result")) ? vars.get("result") : vars.get("message");
        List<ScoredParagraph> hits = knowledgeRetrievalService.retrieve(List.of(kbId), query,
                topK != null && topK > 0 ? Math.min(topK, 20) : 5);
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (ScoredParagraph hit : hits) {
            builder.append(index++).append(". ").append(hit.paragraph().getContent()).append('\n');
        }
        String knowledgeText = builder.toString().trim();
        return NodeOutcome.succeeded("查询：" + truncate(query),
                hits.isEmpty() ? "未命中知识段落" : "命中 " + hits.size() + " 条：" + truncate(knowledgeText),
                knowledgeText);
    }

    private NodeOutcome executeLlm(AiFlowGraph.FlowNode node, Map<String, String> vars,
                                   List<FlowChatTurn> history, FlowEventListener listener) {
        AiModelPo model = resolveLlmModel(node.dataLong("modelId"));
        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("模型「" + model.getModelName() + "」API Key 未配置");
        }
        AiModelPo effectiveModel = applyNodeTemperature(model, node.dataText("temperature"));

        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();
        StringBuilder systemPrompt = new StringBuilder();
        String nodePrompt = node.dataText("systemPrompt");
        if (StringUtils.hasText(nodePrompt)) {
            // v2：系统提示词支持 {{变量}} / {{节点ID.output}} 模板渲染
            systemPrompt.append(renderTemplate(nodePrompt.trim(), vars));
        }
        String knowledge = vars.get("knowledge");
        if (StringUtils.hasText(knowledge)) {
            if (!systemPrompt.isEmpty()) {
                systemPrompt.append("\n\n");
            }
            systemPrompt.append("以下是命中的知识库上下文，请优先引用并结合它们回答：\n").append(knowledge);
        }
        if (!systemPrompt.isEmpty()) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.system(systemPrompt.toString()));
        }
        appendMemoryTurns(messages, history, resolveMemoryRounds(node));
        String userInput = resolveLlmUserInput(node, vars);
        messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(userInput));

        String reply = requestLlmReply(node, effectiveModel, apiKey, messages, listener);
        return NodeOutcome.succeeded("模型：" + model.getModelName() + "；输入：" + truncate(userInput),
                truncate(reply), reply);
    }

    /**
     * llm 节点用户输入：v2 支持 userTemplate 模板（{{变量}}/{{节点ID.output}}），
     * 未配置时保持 v1 行为（最近结果，否则用户消息）。
     */
    private String resolveLlmUserInput(AiFlowGraph.FlowNode node, Map<String, String> vars) {
        String userTemplate = node.dataText("userTemplate");
        if (StringUtils.hasText(userTemplate)) {
            String rendered = renderTemplate(userTemplate.trim(), vars);
            if (StringUtils.hasText(rendered)) {
                return rendered;
            }
        }
        return StringUtils.hasText(vars.get("result")) ? vars.get("result") : vars.get("message");
    }

    /**
     * 节点记忆轮数：memoryRounds 属性可配（0 关闭记忆），缺省 {@value #DEFAULT_LLM_MEMORY_ROUNDS} 轮，
     * 上限 {@value #MAX_LLM_MEMORY_ROUNDS} 轮。
     */
    private int resolveMemoryRounds(AiFlowGraph.FlowNode node) {
        Integer configured = node.dataInt("memoryRounds");
        if (configured == null) {
            return DEFAULT_LLM_MEMORY_ROUNDS;
        }
        return Math.max(0, Math.min(configured, MAX_LLM_MEMORY_ROUNDS));
    }

    /**
     * 按记忆轮数注入会话历史（1 轮 = user+assistant 两条），取最近 rounds*2 条。
     */
    private void appendMemoryTurns(List<AiOpenAiCompatibleClient.ProviderMessage> messages,
                                   List<FlowChatTurn> history, int memoryRounds) {
        if (memoryRounds <= 0 || history == null || history.isEmpty()) {
            return;
        }
        int limit = memoryRounds * 2;
        int startIndex = Math.max(0, history.size() - limit);
        for (int index = startIndex; index < history.size(); index++) {
            FlowChatTurn turn = history.get(index);
            if (turn == null || !StringUtils.hasText(turn.content())) {
                continue;
            }
            if ("user".equals(turn.role())) {
                messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(turn.content()));
            } else if ("assistant".equals(turn.role())) {
                messages.add(AiOpenAiCompatibleClient.ProviderMessage.assistant(turn.content()));
            }
        }
    }

    /**
     * llm 节点模型调用：有监听时走真流式（逐 token 回调 onNodeDelta），
     * 未产出任何增量即失败时降级为一次性补全，保留原有非流式路径。
     */
    private String requestLlmReply(AiFlowGraph.FlowNode node, AiModelPo model, String apiKey,
                                   List<AiOpenAiCompatibleClient.ProviderMessage> messages,
                                   FlowEventListener listener) {
        if (listener == null) {
            return openAiCompatibleClient.chatCompletion(model, apiKey, messages, model.getMaxTokens());
        }
        StringBuilder streamed = new StringBuilder();
        try {
            return openAiCompatibleClient.chatCompletionStream(model, apiKey, messages, model.getMaxTokens(),
                    delta -> {
                        streamed.append(delta);
                        try {
                            listener.onNodeDelta(node.id(), delta);
                        } catch (RuntimeException ex) {
                            log.debug("Flow node delta listener error, nodeId={}", node.id(), ex);
                        }
                    });
        } catch (BusinessException ex) {
            if (!streamed.isEmpty()) {
                throw ex;
            }
            log.warn("Flow llm node stream failed before first token, fallback to non-stream, nodeId={}, reason={}",
                    node.id(), ex.getMessage());
            return openAiCompatibleClient.chatCompletion(model, apiKey, messages, model.getMaxTokens());
        }
    }
    private NodeOutcome executeTool(AiFlowGraph.FlowNode node, Map<String, String> vars) {
        Long mcpId = node.dataLong("mcpId");
        String toolName = node.dataText("toolName");
        if (mcpId == null) {
            throw new BusinessException("工具节点未选择 MCP 服务");
        }
        if (!StringUtils.hasText(toolName)) {
            throw new BusinessException("工具节点未填写工具名称");
        }
        AiMcpServerPo server = resolveMcpServer(mcpId);
        Map<String, Object> arguments = parseToolArguments(node.dataText("arguments"), vars);
        String result = aiMcpClientService.callTool(server, toolName.trim(), arguments);
        String input = "MCP：" + server.getServerName() + "；工具：" + toolName.trim()
                + (arguments.isEmpty() ? "；入参：{}" : "；入参：" + truncate(XuJsonUtil.toJsonString(arguments)));
        return NodeOutcome.succeeded(input, truncate(result), result);
    }

    private AiMcpServerPo resolveMcpServer(Long mcpId) {
        LambdaQueryWrapper<AiMcpServerPo> wrapper = new LambdaQueryWrapper<AiMcpServerPo>()
                .eq(AiMcpServerPo::getMcpId, mcpId)
                .eq(AiMcpServerPo::getStatus, "0");
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId != null) {
            wrapper.eq(AiMcpServerPo::getTenantId, tenantId);
        }
        AiMcpServerPo server = aiMcpServerMapper.selectOne(wrapper.last("LIMIT 1"));
        if (server == null) {
            throw new BusinessException("工具节点配置的 MCP 服务不存在或未启用");
        }
        return server;
    }

    private Map<String, Object> parseToolArguments(String rawArguments, Map<String, String> vars) {
        if (!StringUtils.hasText(rawArguments)) {
            return Map.of();
        }
        String rendered = renderTemplate(rawArguments, vars);
        try {
            Map<String, Object> parsed = XuJsonUtil.parseObject(rendered, new TypeReference<Map<String, Object>>() {});
            return parsed != null ? parsed : Map.of();
        } catch (RuntimeException ex) {
            throw new BusinessException("工具节点入参不是合法 JSON");
        }
    }

    /**
     * 节点温度以副本覆盖模型默认值，不回写共享的模型对象。
     */
    private AiModelPo applyNodeTemperature(AiModelPo model, String rawTemperature) {
        if (!StringUtils.hasText(rawTemperature)) {
            return model;
        }
        BigDecimal temperature;
        try {
            temperature = new BigDecimal(rawTemperature.trim());
        } catch (NumberFormatException ignored) {
            return model;
        }
        if (temperature.compareTo(BigDecimal.ZERO) < 0 || temperature.compareTo(BigDecimal.valueOf(2)) > 0) {
            return model;
        }
        AiModelPo copy = new AiModelPo();
        copy.setModelId(model.getModelId());
        copy.setModelName(model.getModelName());
        copy.setModelType(model.getModelType());
        copy.setProvider(model.getProvider());
        copy.setModelCode(model.getModelCode());
        copy.setBaseUrl(model.getBaseUrl());
        copy.setApiKey(model.getApiKey());
        copy.setMaxTokens(model.getMaxTokens());
        copy.setTemperature(temperature);
        copy.setSupportsVision(model.getSupportsVision());
        copy.setStatus(model.getStatus());
        copy.setTenantId(model.getTenantId());
        return copy;
    }

    private AiModelPo resolveLlmModel(Long modelId) {
        AiModelPo model;
        if (modelId != null) {
            model = aiModelMapper.selectById(modelId);
            if (model == null) {
                throw new BusinessException("LLM 节点配置的模型不存在");
            }
        } else {
            model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelPo>()
                    .eq(AiModelPo::getStatus, "0")
                    .eq(AiModelPo::getModelType, "LLM")
                    .orderByAsc(AiModelPo::getModelId)
                    .last("LIMIT 1"));
            if (model == null) {
                throw new BusinessException("LLM 节点未选择模型且系统无可用 LLM 模型");
            }
        }
        if (!"LLM".equalsIgnoreCase(model.getModelType())) {
            throw new BusinessException("LLM 节点配置的模型不是 LLM 类型");
        }
        if (!"0".equals(model.getStatus())) {
            throw new BusinessException("LLM 节点配置的模型未启用");
        }
        return model;
    }

    /**
     * 条件表达式为受限语法（防注入，不引入 SpEL）：
     * {@code {{var}} contains 'x'} / {@code {{var}} == 'x'} / {@code {{var}} != 'x'}
     * / {@code {{var}} not_empty} / {@code {{var}} is_empty}。
     * var 支持 message / result / knowledge / 节点ID。
     */
    private NodeOutcome executeCondition(AiFlowGraph.FlowNode node, Map<String, String> vars) {
        String expression = node.dataText("expression");
        if (!StringUtils.hasText(expression)) {
            throw new BusinessException("条件节点未配置表达式");
        }
        boolean result = evaluateCondition(expression.trim(), vars);
        return NodeOutcome.condition("表达式：" + expression.trim(), result);
    }

    private boolean evaluateCondition(String expression, Map<String, String> vars) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\{\\{\\s*([\\w-]+)\\s*}}\\s*(contains|==|!=|not_empty|is_empty)\\s*(?:'([^']*)')?$")
                .matcher(expression);
        if (!matcher.matches()) {
            throw new BusinessException("条件表达式不合法，支持：{{变量}} contains '值' / == '值' / != '值' / not_empty / is_empty");
        }
        String varName = matcher.group(1);
        String operator = matcher.group(2);
        String operand = matcher.group(3);
        String value = vars.getOrDefault(varName, "");
        return switch (operator) {
            case "contains" -> {
                requireOperand(operator, operand);
                yield value.contains(operand);
            }
            case "==" -> {
                requireOperand(operator, operand);
                yield value.equals(operand);
            }
            case "!=" -> {
                requireOperand(operator, operand);
                yield !value.equals(operand);
            }
            case "not_empty" -> StringUtils.hasText(value);
            case "is_empty" -> !StringUtils.hasText(value);
            default -> throw new BusinessException("不支持的条件运算符：" + operator);
        };
    }

    private void requireOperand(String operator, String operand) {
        if (operand == null) {
            throw new BusinessException("条件运算符 " + operator + " 需要一个 '值' 操作数");
        }
    }

    /**
     * 输出节点：模板变量替换，支持 {{message}}/{{result}}/{{knowledge}}/{{节点ID}}。
     */
    private NodeOutcome executeOutput(AiFlowGraph.FlowNode node, Map<String, String> vars) {
        String template = node.dataText("template");
        String rendered;
        if (StringUtils.hasText(template)) {
            rendered = renderTemplate(template, vars);
        } else {
            rendered = StringUtils.hasText(vars.get("result")) ? vars.get("result") : vars.get("message");
        }
        return NodeOutcome.succeeded(StringUtils.hasText(template) ? "模板：" + truncate(template) : "透传最近结果",
                truncate(rendered), rendered);
    }

    /**
     * 模板渲染：支持 {@code {{name}}}（message/result/knowledge/入参/节点ID）
     * 与 {@code {{nodeId.output}}} 结构化引用（节点主输出）；未知引用替换为空串。
     */
    private String renderTemplate(String template, Map<String, String> vars) {
        return VAR_REF_PATTERN.matcher(template)
                .replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                        resolveVarRef(vars, match.group(1), match.group(2))));
    }

    /**
     * 变量引用解析：无字段名走扁平变量表；字段名为 output 时取该节点主输出，
     * 其余字段暂未定义（返回空串，为后续结构化字段预留）。
     */
    private String resolveVarRef(Map<String, String> vars, String name, String field) {
        if (field == null) {
            return vars.getOrDefault(name, "");
        }
        if ("output".equals(field)) {
            return vars.getOrDefault(name, "");
        }
        return "";
    }

    /**
     * 扩展可达集合：condition 节点仅沿命中分支（sourceHandle 匹配或未指定）扩展，其余节点全量扩展。
     */
    private void expandReachable(AiFlowGraph graph, AiFlowGraph.FlowNode node, NodeOutcome outcome,
                                 Set<String> reachable) {
        String chosenHandle = null;
        if ("condition".equals(node.type()) && outcome.conditionResult() != null) {
            chosenHandle = outcome.conditionResult() ? "yes" : "no";
        }
        for (AiFlowGraph.FlowEdge edge : graph.outgoing(node.id())) {
            if (chosenHandle == null || edge.sourceHandle() == null || chosenHandle.equals(edge.sourceHandle())) {
                reachable.add(edge.target());
            }
        }
    }

    private AiFlowNodeTraceVo buildTrace(AiFlowGraph.FlowNode node, String status, String input, String output,
                                         Long costMs, String error) {
        AiFlowNodeTraceVo trace = new AiFlowNodeTraceVo();
        trace.setNodeId(node.id());
        trace.setNodeType(node.type());
        trace.setNodeName(node.label());
        trace.setStatus(status);
        trace.setInput(truncate(input));
        trace.setOutput(truncate(output));
        trace.setCostMs(costMs);
        trace.setError(error);
        return trace;
    }

    private String truncate(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String normalized = text.trim();
        return normalized.length() > TRACE_TEXT_LIMIT ? normalized.substring(0, TRACE_TEXT_LIMIT) + "..." : normalized;
    }
}
