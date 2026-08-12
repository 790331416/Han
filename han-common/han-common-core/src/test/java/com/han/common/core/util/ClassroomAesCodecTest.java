package com.han.common.core.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassroomAesCodecTest {

    private static final String KEY = "0123456789ABCDEF";
    private static final String IV = "FEDCBA9876543210";
    private static final String TOKEN_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void roundTripsUtf8PayloadThroughLowercaseHex() {
        String plaintext = "orgId=100&orgName=兰州市第一中学";

        String ciphertext = ClassroomAesCodec.encrypt(plaintext, KEY, IV);

        assertThat(ciphertext).matches("[0-9a-f]+");
        assertThat(ClassroomAesCodec.decrypt(ciphertext, KEY, IV)).isEqualTo(plaintext);
    }

    @Test
    void acceptsUppercaseHexFromLegacyClients() {
        String ciphertext = ClassroomAesCodec.encrypt("pkId=1", KEY, IV);

        assertThat(ClassroomAesCodec.decrypt(ciphertext.toUpperCase(java.util.Locale.ROOT), KEY, IV))
                .isEqualTo("pkId=1");
    }

    @Test
    void derivesKeyAndIvFromTokenTailExactlyLikeLegacyClients() {
        String token = "x".repeat(20) + "KEY0123456789ABC" + "MIDDLE0123456789" + "IV01234567890123";

        assertThat(ClassroomAesCodec.deriveKey(token)).isEqualTo("KEY0123456789ABC");
        assertThat(ClassroomAesCodec.deriveIv(token)).isEqualTo("IV01234567890123");
        assertThat(ClassroomAesCodec.decryptWithToken(
                ClassroomAesCodec.encryptWithToken("a=1", token), token)).isEqualTo("a=1");
    }

    @Test
    void issuedClassroomTokenSatisfiesLegacyKeyDerivation() {
        String token = ClassroomTokenCodec.issue(
                Map.of("userId", "100", "username", "Teacher"), TOKEN_SECRET,
                Instant.now().getEpochSecond(), 3600, "jti-derive");

        assertThat(ClassroomAesCodec.canDeriveKey(token)).isTrue();
        assertThat(ClassroomAesCodec.deriveKey(token).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSize(16);
        assertThat(ClassroomAesCodec.deriveIv(token).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSize(16);
    }

    @Test
    void rejectsTokensTooShortForKeyDerivation() {
        String token = "x".repeat(47);

        assertThat(ClassroomAesCodec.canDeriveKey(token)).isFalse();
        assertThatThrownBy(() -> ClassroomAesCodec.deriveKey(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTokensWhoseTailIsNotSingleByteAscii() {
        String token = "x".repeat(20) + "密钥密钥密钥密钥" + "MIDDLE0123456789" + "IV01234567890123";

        assertThat(ClassroomAesCodec.canDeriveKey(token)).isFalse();
    }

    @Test
    void rejectsKeyMaterialThatIsNotSixteenBytes() {
        assertThatThrownBy(() -> ClassroomAesCodec.encrypt("a=1", "tooshort", IV))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassroomAesCodec.encrypt("a=1", KEY, "tooshort"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCiphertextThatIsNotHexOrWasSignedWithAnotherKey() {
        assertThatThrownBy(() -> ClassroomAesCodec.decrypt("not-hex", KEY, IV))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassroomAesCodec.decrypt(
                ClassroomAesCodec.encrypt("a=1", KEY, IV), "FEDCBA9876543210", IV))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassroomAesCodec.decrypt("", KEY, IV))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
