package com.han.ai.security;

import com.han.common.web.sensitive.SensitiveMasker;
import com.han.common.web.sensitive.SensitiveType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 掩码识别单测。
 * <p>
 * 核心断言：识别器必须认得脱敏序列化器真实产出的掩码。这里直接调用
 * {@link SensitiveMasker#maskCustom}（{@code SensitiveSerializer} 与 Jackson 2 侧共用的同一份实现），
 * 而不是照抄一份期望字符串，这样一旦上游掩码格式变化，本测试会立刻失败而不是悄悄失效。
 */
class AiCredentialMaskDetectorTest {

    /** 与 AiModelPo.apiKey / AiMcpServerPo.envVars 上的 @Sensitive 配置一致 */
    private static final int PREFIX_KEEP = 4;
    private static final int SUFFIX_KEEP = 4;

    private String maskLikeSerializer(String value) {
        return SensitiveMasker.maskCustom(value, PREFIX_KEEP, SUFFIX_KEEP);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sk-1234",
            "sk-12345",
            "sk-123456789",
            "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789",
            "{\"Authorization\":\"Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature\"}"
    })
    void detectsEveryMaskTheSerializerCanProduce(String rawValue) {
        String masked = maskLikeSerializer(rawValue);

        assertThat(AiCredentialMaskDetector.isMasked(masked))
                .as("序列化器产出的掩码必须被识别: %s -> %s", rawValue, masked)
                .isTrue();
        assertThat(AiCredentialMaskDetector.isMasked(rawValue))
                .as("真实凭据不能被误判为掩码: %s", rawValue)
                .isFalse();
    }

    @Test
    void detectsLongKeyMaskThatOldAllAsteriskRuleMissed() {
        // 历史实现只认 ^[*]+$，真实长度的 key 掩码成 abcd****wxyz 过不了判定，
        // 于是掩码被当成新凭据写库，写库后又认不出来，被当作真实凭据发给模型服务商。
        String masked = "sk-p" + "*".repeat(32) + "6789";

        assertThat(masked).doesNotMatch("^\\*+$");
        assertThat(AiCredentialMaskDetector.isMasked(masked)).isTrue();
    }

    @Test
    void treatsAllAsteriskAndShortMasksAsMasked() {
        assertThat(AiCredentialMaskDetector.isMasked("********")).isTrue();
        assertThat(AiCredentialMaskDetector.isMasked("*")).isTrue();
        // 长度 9 时星号段只有 1 位，靠精确形状匹配兜住
        assertThat(AiCredentialMaskDetector.isMasked("abcd*wxyz")).isTrue();
        // 长度 10 时星号段 2 位
        assertThat(AiCredentialMaskDetector.isMasked("abcd**wxyz")).isTrue();
    }

    @Test
    void treatsBlankAndRealCredentialsAsNotMasked() {
        assertThat(AiCredentialMaskDetector.isMasked(null)).isFalse();
        assertThat(AiCredentialMaskDetector.isMasked("")).isFalse();
        assertThat(AiCredentialMaskDetector.isMasked("   ")).isFalse();
        assertThat(AiCredentialMaskDetector.isMasked("sk-proj-9fA2bQ7xLmN0pR4t")).isFalse();
        assertThat(AiCredentialMaskDetector.isMasked("{\"API_KEY\":\"volc-abc123\"}")).isFalse();
        // 单个星号出现在真实值里不应触发误判
        assertThat(AiCredentialMaskDetector.isMasked("pass*word-token-1234")).isFalse();
    }

    @Test
    void sensitiveTypeCustomIsStillTheTypeWeMirror() {
        assertThat(SensitiveType.valueOf("CUSTOM")).isNotNull();
    }
}
