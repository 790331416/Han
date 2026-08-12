package com.han.system.sdfz.compat;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomAesCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按凭证选择 AES 密钥的兼容层加解密入口。
 *
 * <p>带凭证的请求用凭证派生密钥，登录前的请求用配置里的匿名密钥；
 * 响应必须用与请求同一把密钥回写，否则旧前端在换发凭证的瞬间会解不开。
 */
@Component
@RequiredArgsConstructor
public class LegacyCipher {

    private final LegacyCompatProperties properties;

    public String encrypt(String plaintext, String token) {
        if (ClassroomAesCodec.canDeriveKey(token)) {
            return ClassroomAesCodec.encryptWithToken(plaintext, token);
        }
        return ClassroomAesCodec.encrypt(plaintext, anonymousKey(), anonymousIv());
    }

    public String decrypt(String ciphertextHex, String token) {
        if (ClassroomAesCodec.canDeriveKey(token)) {
            return ClassroomAesCodec.decryptWithToken(ciphertextHex, token);
        }
        return ClassroomAesCodec.decrypt(ciphertextHex, anonymousKey(), anonymousIv());
    }

    private String anonymousKey() {
        return require(properties.getAnonymousKey(), "sdfz.compat.anonymous-key");
    }

    private String anonymousIv() {
        return require(properties.getAnonymousIv(), "sdfz.compat.anonymous-iv");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("兼容层缺少配置项 " + name);
        }
        return value;
    }
}
