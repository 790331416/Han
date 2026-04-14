package com.han.ai.service.impl;

import com.han.ai.domain.po.AiModelPo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Resolve model credentials from environment variables or persisted config.
 */
@Component
@RequiredArgsConstructor
class AiModelCredentialResolver {

    static final String CREDENTIAL_SOURCE_ENV = "env";
    static final String CREDENTIAL_SOURCE_DATABASE = "database";
    static final String CREDENTIAL_SOURCE_NONE = "none";

    private static final Pattern MASKED_VALUE_PATTERN = Pattern.compile("^[*]+$");

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
        if (persisted == null || isMaskedValue(persisted)) {
            return new CredentialBinding(null, CREDENTIAL_SOURCE_NONE);
        }
        return new CredentialBinding(persisted, CREDENTIAL_SOURCE_DATABASE);
    }

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
        if ("QWEN".equals(provider)) {
            propertyNames.add("DASHSCOPE_API_KEY");
        }
        return propertyNames;
    }

    private boolean isMaskedValue(String value) {
        return MASKED_VALUE_PATTERN.matcher(value).matches();
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
