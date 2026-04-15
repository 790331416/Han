package com.han.common.security.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * XSS 请求包装器。
 * <p>
 * 对查询参数、请求头和 JSON 请求体中的字符串字段执行 XSS 清洗。
 * 这里不能直接清洗整段 JSON 字符串，否则会破坏 JSON 结构，导致业务请求无法反序列化。
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 允许基础富文本标签和常用属性，兼容公告、富文本编辑器等场景。
     */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "style", "class")
            .addProtocols("img", "src", "data");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private byte[] cachedBody;

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return value != null ? clean(value) : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }

        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = clean(values[i]);
        }
        return cleaned;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return value != null ? clean(value) : null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (!isJsonRequest()) {
            return super.getInputStream();
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(getCachedBody());
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // no-op
            }

            @Override
            public int read() {
                return inputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private boolean isJsonRequest() {
        String contentType = super.getContentType();
        return contentType != null && contentType.contains("application/json");
    }

    private byte[] getCachedBody() throws IOException {
        if (cachedBody != null) {
            return cachedBody;
        }

        String body = readBody(super.getInputStream());
        String sanitizedBody = sanitizeJsonBody(body);
        cachedBody = sanitizedBody.getBytes(StandardCharsets.UTF_8);
        return cachedBody;
    }

    /**
     * 仅清洗 JSON 中的字符串值，保留 JSON 结构。
     */
    private static String sanitizeJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode sanitizedRoot = sanitizeNode(root);
            return OBJECT_MAPPER.writeValueAsString(sanitizedRoot);
        } catch (Exception ignored) {
            return body;
        }
    }

    private static JsonNode sanitizeNode(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isTextual()) {
            return JsonNodeFactory.instance.textNode(clean(node.textValue()));
        }

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node.deepCopy();
            for (int i = 0; i < arrayNode.size(); i++) {
                arrayNode.set(i, sanitizeNode(arrayNode.get(i)));
            }
            return arrayNode;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node.deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                objectNode.set(entry.getKey(), sanitizeNode(entry.getValue()));
            }
            return objectNode;
        }

        return node;
    }

    /**
     * 使用 Jsoup 白名单清洗单个文本值。
     */
    private static String clean(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Jsoup.clean(value, SAFELIST);
    }

    private static String readBody(ServletInputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
