package com.han.common.security.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.han.common.core.util.HanJsonUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XSS 请求包装器测试。
 *
 * <p>对应「XSS 过滤器对 JSON 请求体做整体 Jsoup 清洗会静默篡改业务数据」与
 * 「包装器只覆盖 getInputStream，getReader / getParameterMap 未覆盖」两条发现。
 */
class XssHttpServletRequestWrapperTest {

    @Test
    @DisplayName("JSON 结构与非字符串值保持原样")
    void shouldPreserveJsonStructure() throws Exception {
        String body = "{\"name\":\"张三\",\"age\":30,\"vip\":true,\"tags\":[\"a\",\"b\"],\"extra\":null}";

        JsonNode result = HanJsonUtil.getObjectMapper().readTree(readBody(wrap(body)));

        assertThat(result.get("name").textValue()).isEqualTo("张三");
        assertThat(result.get("age").intValue()).isEqualTo(30);
        assertThat(result.get("vip").booleanValue()).isTrue();
        assertThat(result.get("tags")).hasSize(2);
        assertThat(result.get("extra").isNull()).isTrue();
    }

    @Test
    @DisplayName("嵌套对象中的脚本标签被清除，兄弟字段不受影响")
    void shouldStripScriptInsideNestedValue() throws Exception {
        String body = "{\"profile\":{\"bio\":\"<script>alert(1)</script>hello\",\"city\":\"北京\"}}";

        JsonNode result = HanJsonUtil.getObjectMapper().readTree(readBody(wrap(body)));

        assertThat(result.get("profile").get("bio").textValue()).doesNotContain("<script");
        assertThat(result.get("profile").get("bio").textValue()).contains("hello");
        assertThat(result.get("profile").get("city").textValue()).isEqualTo("北京");
    }

    @Test
    @DisplayName("清洗后仍是合法 JSON，且不会被插入换行")
    void shouldStayValidJsonWithoutInjectedNewlines() throws Exception {
        String body = "{\"remark\":\"" + "很长的说明文本".repeat(40) + "\"}";

        String cleaned = readBody(wrap(body));

        assertThat(cleaned).doesNotContain("\n");
        assertThat(HanJsonUtil.getObjectMapper().readTree(cleaned).get("remark").textValue())
                .isEqualTo("很长的说明文本".repeat(40));
    }

    @Test
    @DisplayName("getReader 与 getInputStream 读到同样的清洗结果")
    void shouldCleanReaderToo() throws Exception {
        String body = "{\"bio\":\"<script>alert(1)</script>ok\"}";
        XssHttpServletRequestWrapper wrapper = wrap(body);

        try (BufferedReader reader = wrapper.getReader()) {
            String viaReader = reader.lines().reduce("", String::concat);
            assertThat(viaReader).doesNotContain("<script").contains("ok");
        }
    }

    @Test
    @DisplayName("非合法 JSON 退回整体清洗，防护不放弃")
    void shouldFallBackForMalformedJson() throws Exception {
        String cleaned = readBody(wrap("<script>alert(1)</script>not json"));

        assertThat(cleaned).doesNotContain("<script").contains("not json");
    }

    @Test
    @DisplayName("getParameterMap 同样被清洗")
    void shouldCleanParameterMap() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/user/edit");
        request.addParameter("nickname", "<script>alert(1)</script>tom");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        assertThat(wrapper.getParameterMap().get("nickname")[0]).doesNotContain("<script").contains("tom");
    }

    private XssHttpServletRequestWrapper wrap(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/user/edit");
        request.setContentType("application/json;charset=UTF-8");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return new XssHttpServletRequestWrapper(request);
    }

    private String readBody(XssHttpServletRequestWrapper wrapper) throws Exception {
        return new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
