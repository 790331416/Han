package com.han.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * advanced 编排画布图模型：解析 flowConfig（{nodes,edges}），校验 DAG 合法性并给出拓扑序。
 * <p>
 * 校验规则（与设计器前端校验一致，后端为准）：
 * 有且仅有一个 start 节点；无环；无孤岛节点（所有节点与 start 连通）；节点数不超过 {@link #MAX_NODES}。
 */
final class AiFlowGraph {

    static final int MAX_NODES = 30;

    /**
     * 画布节点：id/type 来自 VueFlow 节点，data 承载属性面板配置（label/modelId/kbId/expression/template 等）。
     */
    record FlowNode(String id, String type, Map<String, Object> data) {

        String label() {
            Object label = data != null ? data.get("label") : null;
            return label != null && StringUtils.hasText(String.valueOf(label)) ? String.valueOf(label) : type;
        }

        String dataText(String key) {
            Object value = data != null ? data.get(key) : null;
            return value != null ? String.valueOf(value) : null;
        }

        Long dataLong(String key) {
            Object value = data != null ? data.get(key) : null;
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.valueOf(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        Integer dataInt(String key) {
            Long value = dataLong(key);
            return value != null ? value.intValue() : null;
        }
    }

    /**
     * 画布连线；sourceHandle 用于 condition 节点分支（yes/no）。
     */
    record FlowEdge(String source, String target, String sourceHandle) {
    }

    private final Map<String, FlowNode> nodes;
    private final List<FlowEdge> edges;
    private final List<FlowNode> topologicalOrder;
    private final FlowNode startNode;

    private AiFlowGraph(Map<String, FlowNode> nodes, List<FlowEdge> edges,
                        List<FlowNode> topologicalOrder, FlowNode startNode) {
        this.nodes = nodes;
        this.edges = edges;
        this.topologicalOrder = topologicalOrder;
        this.startNode = startNode;
    }

    /**
     * 解析并校验画布配置；非法时抛出带明确原因的 {@link BusinessException}。
     */
    static AiFlowGraph parse(String flowConfig) {
        if (!StringUtils.hasText(flowConfig) || "{}".equals(flowConfig.trim())) {
            throw new BusinessException("编排画布为空，请先在设计器中编排节点");
        }
        Map<String, Object> root;
        try {
            root = XuJsonUtil.parseObject(flowConfig, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException ex) {
            throw new BusinessException("编排画布配置不是合法 JSON");
        }
        if (root == null) {
            throw new BusinessException("编排画布配置不是合法 JSON");
        }
        Map<String, FlowNode> nodes = parseNodes(root.get("nodes"));
        List<FlowEdge> edges = parseEdges(root.get("edges"), nodes);

        if (nodes.isEmpty()) {
            throw new BusinessException("编排画布没有任何节点");
        }
        if (nodes.size() > MAX_NODES) {
            throw new BusinessException("编排节点数超过上限 " + MAX_NODES);
        }
        List<FlowNode> startNodes = nodes.values().stream().filter(node -> "start".equals(node.type())).toList();
        if (startNodes.size() != 1) {
            throw new BusinessException(startNodes.isEmpty() ? "编排缺少开始节点" : "编排只能有一个开始节点");
        }
        FlowNode startNode = startNodes.get(0);

        List<FlowNode> order = topologicalSort(nodes, edges);
        ensureConnected(nodes, edges, startNode);
        return new AiFlowGraph(nodes, edges, order, startNode);
    }

    private static Map<String, FlowNode> parseNodes(Object rawNodes) {
        Map<String, FlowNode> nodes = new LinkedHashMap<>();
        if (!(rawNodes instanceof List<?> nodeList)) {
            return nodes;
        }
        for (Object item : nodeList) {
            if (!(item instanceof Map<?, ?> nodeMap)) {
                continue;
            }
            Object id = nodeMap.get("id");
            Object type = nodeMap.get("type");
            if (id == null || type == null) {
                continue;
            }
            Map<String, Object> data = new HashMap<>();
            if (nodeMap.get("data") instanceof Map<?, ?> dataMap) {
                for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                    data.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            String nodeId = String.valueOf(id);
            nodes.put(nodeId, new FlowNode(nodeId, String.valueOf(type), data));
        }
        return nodes;
    }

    private static List<FlowEdge> parseEdges(Object rawEdges, Map<String, FlowNode> nodes) {
        List<FlowEdge> edges = new ArrayList<>();
        if (!(rawEdges instanceof List<?> edgeList)) {
            return edges;
        }
        for (Object item : edgeList) {
            if (!(item instanceof Map<?, ?> edgeMap)) {
                continue;
            }
            Object source = edgeMap.get("source");
            Object target = edgeMap.get("target");
            if (source == null || target == null) {
                continue;
            }
            String sourceId = String.valueOf(source);
            String targetId = String.valueOf(target);
            if (!nodes.containsKey(sourceId) || !nodes.containsKey(targetId)) {
                throw new BusinessException("编排存在指向不存在节点的连线");
            }
            Object handle = edgeMap.get("sourceHandle");
            edges.add(new FlowEdge(sourceId, targetId,
                    handle != null && StringUtils.hasText(String.valueOf(handle)) ? String.valueOf(handle) : null));
        }
        return edges;
    }

    /**
     * Kahn 拓扑排序；存在环时抛异常。
     */
    private static List<FlowNode> topologicalSort(Map<String, FlowNode> nodes, List<FlowEdge> edges) {
        Map<String, Integer> inDegree = new HashMap<>();
        nodes.keySet().forEach(id -> inDegree.put(id, 0));
        for (FlowEdge edge : edges) {
            inDegree.merge(edge.target(), 1, Integer::sum);
        }
        Deque<String> queue = new ArrayDeque<>();
        // 按画布节点定义顺序入队，保证同层节点执行顺序稳定
        nodes.keySet().stream().filter(id -> inDegree.get(id) == 0).forEach(queue::addLast);
        List<FlowNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            order.add(nodes.get(current));
            for (FlowEdge edge : edges) {
                if (edge.source().equals(current) && inDegree.merge(edge.target(), -1, Integer::sum) == 0) {
                    queue.addLast(edge.target());
                }
            }
        }
        if (order.size() != nodes.size()) {
            throw new BusinessException("编排存在环路，请检查连线方向");
        }
        return order;
    }

    /**
     * 无向连通性检查：所有节点必须与 start 连通，防止孤岛节点。
     */
    private static void ensureConnected(Map<String, FlowNode> nodes, List<FlowEdge> edges, FlowNode startNode) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.addLast(startNode.id());
        visited.add(startNode.id());
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (FlowEdge edge : edges) {
                String next = null;
                if (edge.source().equals(current)) {
                    next = edge.target();
                } else if (edge.target().equals(current)) {
                    next = edge.source();
                }
                if (next != null && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        if (visited.size() != nodes.size()) {
            throw new BusinessException("编排存在未与开始节点连通的孤岛节点");
        }
    }

    List<FlowNode> topologicalOrder() {
        return topologicalOrder;
    }

    FlowNode startNode() {
        return startNode;
    }

    List<FlowEdge> outgoing(String nodeId) {
        List<FlowEdge> result = new ArrayList<>();
        for (FlowEdge edge : edges) {
            if (edge.source().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    int nodeCount() {
        return nodes.size();
    }
}
