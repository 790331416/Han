package com.han.ai.service.impl;

import com.han.ai.domain.po.AiModelPo;
import com.han.ai.security.AiCredentialMaskDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从环境变量或已持久化的配置中解析模型凭据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiModelCredentialResolver {

    static final String CREDENTIAL_SOURCE_ENV = "env";
    static final String CREDENTIAL_SOURCE_DATABASE = "database";
    static final String CREDENTIAL_SOURCE_NONE = "none";

    private final Environment environment;

    String resolveApiKey(AiModelPo model) {
        return resolveCredentialBinding(model).apiKey();
    }

    String resolveCredentialSource(AiModelPo model) {
        return resolveCredentialBinding(model).source();
    }

    boolean isCredentialConfigured(AiModelPo model) {
        return !CREDENTIAL_SOURCE_NONE.equals(resolveCredentialSource(model));
    }

    private CredentialBinding resolveCredentialBinding(AiModelPo model) {
        if (model == null) {
            return new CredentialBinding(null, CREDENTIAL_SOURCE_NONE);
        }

        for (String propertyName : buildCandidatePropertyNames(model)) {
            String propertyValue = trimToNull(environment.getProperty(propertyName));
            if (propertyValue != null) {
                return new CredentialBinding(propertyValue, CREDENTIAL_SOURCE_ENV);
            }
        }

        String persisted = trimToNull(model.getApiKey());
        if (persisted == null) {
            return new CredentialBinding(null, CREDENTIAL_SOURCE_NONE);
        }
        if (isMaskedValue(persisted)) {
            // 存量污染数据：历史版本把脱敏掩码当新 Key 写了库。
            // 这里必须判为「未配置」而不是照发给供应商，否则会用一串星号去请求鉴权。
            log.warn("Model apiKey in database is a masked placeholder, treated as unconfigured. modelId={}, provider={}",
                    model.getModelId(), model.getProvider());
            return new CredentialBinding(null, CREDENTIAL_SOURCE_NONE);
        }
        return new CredentialBinding(persisted, CREDENTIAL_SOURCE_DATABASE);
    }

    /**
     * 编辑保存时是否保留库内原有 API Key：留空或回传掩码串都保留原值。
     */
    boolean shouldKeepExistingApiKey(String incomingApiKey) {
        String normalized = trimToNull(incomingApiKey);
        return normalized == null || isMaskedValue(normalized);
    }

    private List<String> buildCandidatePropertyNames(AiModelPo model) {
        List<String> propertyNames = new ArrayList<>();
        String provider = normalizeToken(model.getProvider());
        String modelCode = normalizeToken(model.getModelCode());

        if (modelCode != null) {
            propertyNames.add("HAN_AI_MODEL_" + modelCode + "_API_KEY");
        }
        if (provider != null) {
            propertyNames.add("HAN_AI_PROVIDER_" + provider + "_API_KEY");
            propertyNames.add("HAN_AI_" + provider + "_API_KEY");
            propertyNames.add(provider + "_API_KEY");
        }
        if (isVolcengineArk(provider, modelCode)) {
            propertyNames.add("VOLCENGINE_ARK_API_KEY");
            propertyNames.add("ARK_API_KEY");
        }
        if ("QWEN".equals(provider)) {
            propertyNames.add("DASHSCOPE_API_KEY");
        }
        return propertyNames;
    }

    private boolean isVolcengineArk(String provider, String modelCode) {
        return "VOLCENGINE".equals(provider)
                || "ARK".equals(provider)
                || (modelCode != null && (modelCode.contains("DOUBAO") || modelCode.contains("ARK")));
    }

    private boolean isMaskedValue(String value) {
        return AiCredentialMaskDetector.isMasked(value);
    }

    private String normalizeToken(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record CredentialBinding(String apiKey, String source) {
    }
}
