package com.han.common.web.sensitive;

import com.han.common.core.util.HanJsonUtil;
import com.han.common.core.util.XuJsonUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S-73 回归：{@code @Sensitive} 曾经只在 Web 层 Jackson 3 生效，
 * 走 {@code HanJsonUtil} / {@code XuJsonUtil} 写缓存或打日志时是明文。
 */
class SensitiveJson2BypassTest {

    static class UserPayload {
        @Sensitive(SensitiveType.PHONE)
        private String phone;

        @Sensitive(SensitiveType.ID_CARD)
        private String idCard;

        @Sensitive(SensitiveType.EMAIL)
        private String email;

        @Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 4, suffixKeep = 4)
        private String apiKey;

        private String nickName;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getIdCard() {
            return idCard;
        }

        public void setIdCard(String idCard) {
            this.idCard = idCard;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getNickName() {
            return nickName;
        }

        public void setNickName(String nickName) {
            this.nickName = nickName;
        }
    }

    static class TimePayload {
        private LocalDateTime createTime;
        private LocalDate birthday;
        private LocalTime workStart;

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDate getBirthday() {
            return birthday;
        }

        public void setBirthday(LocalDate birthday) {
            this.birthday = birthday;
        }

        public LocalTime getWorkStart() {
            return workStart;
        }

        public void setWorkStart(LocalTime workStart) {
            this.workStart = workStart;
        }
    }

    private static UserPayload sampleUser() {
        UserPayload payload = new UserPayload();
        payload.setPhone("13812345678");
        payload.setIdCard("110101199001011234");
        payload.setEmail("zhangsan@example.com");
        payload.setApiKey("sk-abcdefghijklmnop");
        payload.setNickName("张三丰");
        return payload;
    }

    @Test
    @DisplayName("HanJsonUtil 序列化时对 @Sensitive 字段脱敏（写 Redis / 打日志路径）")
    void hanJsonUtilMasksSensitiveFields() {
        String json = HanJsonUtil.toJsonString(sampleUser());

        assertFalse(json.contains("13812345678"), "手机号不应明文出现在 JSON 中");
        assertFalse(json.contains("110101199001011234"), "身份证不应明文出现在 JSON 中");
        assertFalse(json.contains("sk-abcdefghijklmnop"), "API Key 不应明文出现在 JSON 中");

        assertTrue(json.contains("138****5678"), "实际输出: " + json);
        assertTrue(json.contains("z***@example.com"), "实际输出: " + json);
        // 与 Web 层共用同一份算法，脱敏结果必须逐字符一致
        assertTrue(json.contains(SensitiveMasker.maskIdCard("110101199001011234")), "实际输出: " + json);
        assertTrue(json.contains(SensitiveMasker.maskCustom("sk-abcdefghijklmnop", 4, 4)), "实际输出: " + json);
        assertTrue(json.contains("张三丰"), "未标注 @Sensitive 的字段保持原样");
    }

    @Test
    @DisplayName("XuJsonUtil 与 HanJsonUtil 输出完全一致")
    void xuJsonUtilBehavesIdentically() {
        assertEquals(HanJsonUtil.toJsonString(sampleUser()), XuJsonUtil.toJsonString(sampleUser()));
    }

    @Test
    @DisplayName("日期时间输出格式与 Web 层 Jackson 3 对齐")
    void dateTimeFormatMatchesWebLayer() {
        TimePayload payload = new TimePayload();
        payload.setCreateTime(LocalDateTime.of(2026, 8, 12, 14, 48, 0));
        payload.setBirthday(LocalDate.of(1990, 1, 1));
        payload.setWorkStart(LocalTime.of(9, 0, 0));

        String json = HanJsonUtil.toJsonString(payload);

        assertTrue(json.contains("\"2026-08-12 14:48:00\""), "实际输出: " + json);
        assertTrue(json.contains("\"1990-01-01\""), "实际输出: " + json);
        assertTrue(json.contains("\"09:00:00\""), "实际输出: " + json);
    }

    @Test
    @DisplayName("反序列化同时兼容新格式与升级前写入缓存的 ISO-8601")
    void dateTimeDeserializationAcceptsBothFormats() {
        LocalDateTime expected = LocalDateTime.of(2026, 8, 12, 14, 48, 0);

        TimePayload spaced = HanJsonUtil.parseObject("{\"createTime\":\"2026-08-12 14:48:00\"}", TimePayload.class);
        TimePayload iso = HanJsonUtil.parseObject("{\"createTime\":\"2026-08-12T14:48:00\"}", TimePayload.class);

        assertEquals(expected, spaced.getCreateTime());
        assertEquals(expected, iso.getCreateTime(), "历史缓存里的 ISO-8601 必须仍可读回");
    }

    @Test
    @DisplayName("Long 仍序列化为数字，parseMap 的取值类型不变")
    void longStaysNumericForUntypedReads() {
        String json = HanJsonUtil.toJsonString(java.util.Map.of("userId", 1234567890123456789L));
        assertTrue(json.contains("1234567890123456789"), "实际输出: " + json);
        assertFalse(json.contains("\"1234567890123456789\""), "实际输出: " + json);
    }
}
