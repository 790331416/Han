package com.han.common.core.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmCipherTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomIvAndRestoresPlaintext() {
        String first = AesGcmCipher.encrypt(MASTER_KEY, "ctyun-secret");
        String second = AesGcmCipher.encrypt(MASTER_KEY, "ctyun-secret");

        assertNotEquals(first, second);
        assertEquals("ctyun-secret", AesGcmCipher.decrypt(MASTER_KEY, first));
        assertEquals("ctyun-secret", AesGcmCipher.decrypt(MASTER_KEY, second));
    }

    @Test
    void rejectsWrongMasterKey() {
        String ciphertext = AesGcmCipher.encrypt(MASTER_KEY, "ctyun-secret");
        String anotherKey = Base64.getEncoder().encodeToString(new byte[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        assertThrows(IllegalStateException.class, () -> AesGcmCipher.decrypt(anotherKey, ciphertext));
    }
}
