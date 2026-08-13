package com.han.common.security.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.han.common.core.util.HanJsonUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS 请求包装器
 *
 * <p>对请求参数、请求头和 JSON Body 进行 XSS 清洗。
 *
 * <p><b>JSON Body 按值清洗，不整体清洗。</b>此前的实现把整个 JSON 文本丢给
 * {@code Jsoup.clean}，等于让 HTML 解析器去解析 JSON：文本里出现的标签样式片段会被当成
 * 标记处理，Jsoup 默认开启的 prettyPrint 还会往里插换行，结果是结构被静默改写。
 * 现在先解析成 JSON 树，只对字符串值做清洗，再序列化回去；解析失败（不是合法 JSON）
 * 才退回整体清洗，保证防护不降级。
 */
@Slf4j
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /** 宽松白名单：允许基础 HTML 标签（富文本场景） */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "style", "class")
            .addProtocols("img", "src", "data");

    /** 关闭 prettyPrint，避免清洗过程往内容里插入换行与缩进 */
    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    private static final String JSON_CONTENT_TYPE = "application/json";

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
        return cleanAll(values);
    }

    /**
     * 此前未覆盖，导致按 Map 方式读参数可以绕过清洗。
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> source = super.getParameterMap();
        if (source == null || source.isEmpty()) {
            return source;
        }
        Map<String, String[]> cleaned = new LinkedHashMap<>(source.size());
        source.forEach((key, values) -> cleaned.put(key, values != null ? cleanAll(values) : null));
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
        ByteArrayInputStream source = new ByteArrayInputStream(cachedBody());
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // no-op
            }

            @Override
            public int read() {
                return source.read();
            }
        };
    }

    /**
     * 此前未覆盖，按字符流读取请求体可以绕过清洗。
     */
    @Override
    public BufferedReader getReader() throws IOException {
        if (!isJsonRequest()) {
            return super.getReader();
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private boolean isJsonRequest() {
        String contentType = super.getContentType();
        return contentType != null && contentType.contains(JSON_CONTENT_TYPE);
    }

    /**
     * 请求体只能读一次，清洗结果缓存下来供 getInputStream 与 getReader 共用。
     */
    private byte[] cachedBody() throws IOException {
        if (cachedBody == null) {
            String body = readBody();
            cachedBody = cleanJsonBody(body).getBytes(StandardCharsets.UTF_8);
        }
        return cachedBody;
    }

    private String readBody() throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(super.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    /**
     * 解析 JSON 后只清洗字符串值，保持结构与数值/布尔/null 原样。
     */
    private String cleanJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return body == null ? "" : body;
        }
        ObjectMapper mapper = HanJsonUtil.getObjectMapper();
        try {
            JsonNode cleaned = cleanNode(mapper.readTree(body));
            return mapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            // 声明了 JSON 却不是合法 JSON：退回整体清洗，不因解析失败而放弃防护
            log.debug("请求体非合法 JSON，退回整体 XSS 清洗", e);
            return clean(body);
        }
    }

    private JsonNode cleanNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(clean(node.textValue()));
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, cleanNode(array.get(i)));
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<String> names = object.fieldNames();
            Map<String, JsonNode> replacements = new LinkedHashMap<>();
            while (names.hasNext()) {
                String name = names.next();
                replacements.put(name, cleanNode(object.get(name)));
            }
            replacements.forEach(object::set);
            return object;
        }
        return node;
    }

    private String[] cleanAll(String[] values) {
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = values[i] != null ? clean(values[i]) : null;
        }
        return cleaned;
    }

    /**
     * XSS 清洗：使用 Jsoup 白名单过滤
     */
    private static String clean(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Jsoup.clean(value, "", SAFELIST, OUTPUT_SETTINGS);
    }
}
