package com.han.common.log.aspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志字段级脱敏的回归测试。
 * <p>此前唯一防护是手工标 {@code saveParams = false}，全仓只有两处标了，
 * 新增用户、改资料、租户增改这些接口的入参明文落库。
 */
class OperLogMaskerTest {

    private final OperLogMasker masker = new OperLogMasker(null);

    @Test
    @DisplayName("顶层敏感字段被替换为占位符")
    void masksTopLevelFields() {
        String json = masker.toJson(Map.of("userName", "admin", "password", "Han@2026"));

        assertFalse(json.contains("Han@2026"));
        assertTrue(json.contains("\"password\":\"***\""), "实际输出: " + json);
        assertTrue(json.contains("admin"));
    }

    @Test
    @DisplayName("嵌套对象与数组里的敏感字段同样被脱敏")
    void masksNestedFields() {
        Map<String, Object> payload = Map.of(
                "user", Map.of("name", "admin", "newPassword", "P@ssw0rd!"),
                "clients", List.of(Map.of("id", 1, "clientSecret", "s3cr3t")));

        String json = masker.toJson(payload);

        assertFalse(json.contains("P@ssw0rd!"), "实际输出: " + json);
        assertFalse(json.contains("s3cr3t"), "实际输出: " + json);
    }

    @Test
    @DisplayName("字段名匹配大小写不敏感且按包含匹配")
    void matchesCaseInsensitively() {
        assertTrue(masker.isMaskedName("PassWord"));
        assertTrue(masker.isMaskedName("oldPwd"));
        assertTrue(masker.isMaskedName("accessToken"));
        assertTrue(masker.isMaskedName("clientSecret"));
        assertTrue(masker.isMaskedName("idCardNo"));
        assertFalse(masker.isMaskedName("userName"));
        assertFalse(masker.isMaskedName("nickName"));
    }

    @Test
    @DisplayName("可以通过配置追加脱敏字段名")
    void supportsExtraFields() {
        OperLogMasker custom = new OperLogMasker(List.of("mobile", " "));

        assertTrue(custom.isMaskedName("mobileNo"));
        assertTrue(custom.getMaskFields().contains("mobile"));
        assertFalse(custom.getMaskFields().contains(" "));
    }

    @Test
    @DisplayName("null 与序列化失败都不抛异常，采集日志不影响主流程")
    void neverThrows() {
        assertEquals("null", masker.toJson(null));
        assertEquals("[序列化失败]", masker.toJson(new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("boom");
            }
        }));
    }
}
