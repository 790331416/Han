package com.han.open.util;

import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkceUtilTest {

    /** RFC 7636 附录 B 的官方测试向量 */
    private static final String RFC_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void deriveS256ChallengeMatchesRfcTestVector() {
        assertThat(PkceUtil.deriveS256Challenge(RFC_VERIFIER)).isEqualTo(RFC_CHALLENGE);
    }

    @Test
    void matchesAcceptsCorrectS256Verifier() {
        assertThat(PkceUtil.matches(RFC_VERIFIER, RFC_CHALLENGE, PkceUtil.METHOD_S256)).isTrue();
    }

    @Test
    void matchesRejectsWrongS256Verifier() {
        String other = "aBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertThat(PkceUtil.matches(other, RFC_CHALLENGE, PkceUtil.METHOD_S256)).isFalse();
    }

    @Test
    void matchesRejectsArbitraryNonEmptyVerifier() {
        // 修复前的实现只判断 code_verifier 非空，传任意值都能换到令牌
        assertThat(PkceUtil.matches("x", RFC_CHALLENGE, PkceUtil.METHOD_S256)).isFalse();
    }

    @Test
    void matchesAcceptsPlainVerifier() {
        assertThat(PkceUtil.matches(RFC_VERIFIER, RFC_VERIFIER, PkceUtil.METHOD_PLAIN)).isTrue();
    }

    @Test
    void matchesRejectsPlainVerifierWhenChallengeDiffers() {
        assertThat(PkceUtil.matches(RFC_VERIFIER, RFC_CHALLENGE, PkceUtil.METHOD_PLAIN)).isFalse();
    }

    @Test
    void matchesRejectsNullVerifier() {
        assertThat(PkceUtil.matches(null, RFC_CHALLENGE, PkceUtil.METHOD_S256)).isFalse();
    }

    @Test
    void isValidVerifierFormatEnforcesRfcLengthAndCharset() {
        assertThat(PkceUtil.isValidVerifierFormat(RFC_VERIFIER)).isTrue();
        assertThat(PkceUtil.isValidVerifierFormat("short")).isFalse();
        assertThat(PkceUtil.isValidVerifierFormat("a".repeat(129))).isFalse();
        assertThat(PkceUtil.isValidVerifierFormat("a".repeat(42) + "!")).isFalse();
    }

    @Test
    void normalizeMethodDefaultsToPlainAndAcceptsS256IgnoringCase() {
        assertThat(PkceUtil.normalizeMethod(null)).isEqualTo(PkceUtil.METHOD_PLAIN);
        assertThat(PkceUtil.normalizeMethod("  ")).isEqualTo(PkceUtil.METHOD_PLAIN);
        assertThat(PkceUtil.normalizeMethod("s256")).isEqualTo(PkceUtil.METHOD_S256);
        assertThat(PkceUtil.normalizeMethod("PLAIN")).isEqualTo(PkceUtil.METHOD_PLAIN);
    }

    @Test
    void normalizeMethodRejectsUnknownMethod() {
        assertThatThrownBy(() -> PkceUtil.normalizeMethod("S512"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的 code_challenge_method");
    }
}
