package com.han.common.log.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.han.common.core.util.HanJsonUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 操作日志的字段级脱敏。
 * <p>
 * 此前唯一的防护手段是在敏感接口上手工标 {@code saveParams = false}，靠人记住，
 * 漏一个就把明文密码、手机号、API Key 写进 {@code oper_param} 落库。
 * 这里按字段名黑名单做兜底，作用于<b>任意嵌套层级</b>。
 * <p>
 * 序列化用的是 {@code HanJsonUtil} 的 mapper 副本，因此打了 {@code @Sensitive} 的字段
 * 已经先被脱敏一次；本类是覆盖「没打注解的 DTO」的第二道防线。
 */
public class OperLogMasker {

    /** 脱敏后的占位符 */
    public static final String MASKED = "***";

    /** 默认脱敏字段名（大小写不敏感，按「包含」匹配） */
    private static final List<String> DEFAULT_MASK_FIELDS = List.of(
            "password", "pwd", "passwd", "secret", "token", "credential",
            "privatekey", "private_key", "apikey", "api_key", "accesskey", "access_key",
            "idcard", "id_card", "idnumber", "id_number", "bankcard", "bank_card"
    );

    private final ObjectMapper mapper = HanJsonUtil.getObjectMapper();
    private final List<String> maskFields;

    public OperLogMasker(Collection<String> extraFields) {
        List<String> fields = new ArrayList<>(DEFAULT_MASK_FIELDS);
        if (extraFields != null) {
            extraFields.stream()
                    .filter(f -> f != null && !f.isBlank())
                    .map(f -> f.trim().toLowerCase(Locale.ROOT))
                    .forEach(fields::add);
        }
        this.maskFields = List.copyOf(fields);
    }

    /**
     * 序列化并脱敏。序列化失败不抛异常 —— 采集日志不能影响主流程。
     */
    public String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            JsonNode node = mapper.valueToTree(value);
            maskNode(node);
            return node.toString();
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }

    /**
     * 字段名是否命中脱敏黑名单。
     */
    public boolean isMaskedName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String field : maskFields) {
            if (lower.contains(field)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前生效的脱敏字段名（只读）。
     */
    public List<String> getMaskFields() {
        return maskFields;
    }

    private void maskNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> names = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (isMaskedName(name)) {
                    objectNode.put(name, MASKED);
                } else {
                    maskNode(objectNode.get(name));
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskNode);
        }
    }
}
