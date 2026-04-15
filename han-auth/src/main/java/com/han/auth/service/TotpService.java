package com.han.auth.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * TOTP 两步验证服务
 * <p>基于 RFC 6238，生成/校验基于时间的一次性密码。
 * 兼容 Google Authenticator、Microsoft Authenticator 等主流 APP。
 */
@Slf4j
@Service
public class TotpService {

    private static final String ISSUER = "HanCloud";
    private static final int SECRET_LENGTH = 32;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    /**
     * 生成新的 TOTP 密钥
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * 生成 otpauth:// URI（用于二维码）
     *
     * @param secret   TOTP 密钥
     * @param username 用户名（显示在 Authenticator APP 中）
     * @return otpauth URI
     */
    public String generateOtpAuthUrl(String secret, String username) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    /**
     * 生成二维码 Base64 PNG 图片
     *
     * @param secret   TOTP 密钥
     * @param username 用户名
     * @return Base64 编码的 PNG 图片数据（不含 data:image/png;base64, 前缀）
     */
    public String generateQrCodeBase64(String secret, String username) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageData = generator.generate(data);
            return Base64.getEncoder().encodeToString(imageData);
        } catch (QrGenerationException e) {
            log.error("生成 TOTP 二维码失败", e);
            throw new RuntimeException("生成二维码失败", e);
        }
    }

    /**
     * 校验 TOTP 验证码
     *
     * @param secret TOTP 密钥
     * @param code   用户输入的 6 位验证码
     * @return 是否验证通过
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.length() != 6) {
            return false;
        }
        return codeVerifier.isValidCode(secret, code);
    }
}
