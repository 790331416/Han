package com.han.auth.config;

import com.han.common.core.util.HanSecureUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.KeyPair;

/**
 * 安全配置属性
 *
 * <p>控制密码加密传输：
 * <ul>
 *   <li>开发/测试环境：enabled=false，明文传输方便调试</li>
 *   <li>生产环境：enabled=true，RSA 加密传输</li>
 * </ul>
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "han.security.password-encrypt")
public class SecurityProperties {

    /** 是否启用密码加密传输（默认 false） */
    private boolean enabled = false;

    /** RSA 密钥对（应用启动时自动生成） */
    private KeyPair rsaKeyPair;

    /** Base64 编码的公钥 */
    private String publicKey;

    /** Base64 编码的私钥 */
    private String privateKey;

    /**
     * 初始化 RSA 密钥对
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // ponytail: 密钥先保存在 auth 进程内；扩为多副本前改为共享 key-id/密钥服务。
        // 注册专用密钥始终生成；enabled 只控制旧登录链路是否要求密文，保持兼容。
        rsaKeyPair = HanSecureUtil.generateRsaKeyPair();
        publicKey = HanSecureUtil.getPublicKeyBase64(rsaKeyPair);
        privateKey = HanSecureUtil.getPrivateKeyBase64(rsaKeyPair);
        if (enabled) {
            log.info("密码加密传输已启用，RSA 密钥对已生成");
        } else {
            log.info("密码加密传输未启用（开发/测试模式），已生成厂商注册专用密钥");
        }
    }

    /** 注册入口始终需要可用的 RSA 密钥。 */
    public boolean isRegistrationKeyReady() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }
}
