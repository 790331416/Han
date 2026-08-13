package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.service.IAiPromptTemplateService;
import com.han.ai.service.IAiTextGenerationService;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议的文本生成实现。
 */
@Service
@RequiredArgsConstructor
public class AiTextGenerationServiceImpl extends AiServiceSupport implements IAiTextGenerationService {

    private static final String MODEL_TYPE_LLM = "LLM";

    private final AiModelMapper aiModelMapper;
    private final IAiPromptTemplateService promptTemplateService;
    private final AiModelCredentialResolver credentialResolver;
    private final AiOpenAiCompatibleClient openAiCompatibleClient;

    @Override
    public AiTextGenerateResponse generate(AiTextGenerateRequest request) {
        if (request == null) {
            throw new BusinessException("文本生成请求不能为空");
        }
        AiModelPo model = request.getModelId() != null
                ? requireModel(request.getModelId(), request.getTenantId())
                : selectDefaultTextModel(request.getTenantId());
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException("文本模型未启用");
        }

        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("文本模型 API Key 未配置，请先配置环境变量或模型凭据");
        }

        String prompt = resolvePrompt(request);
        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.system(request.getSystemPrompt().trim()));
        }
        messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(prompt));

        String content = openAiCompatibleClient.chatCompletion(model, apiKey, messages, request.getMaxTokens());
        AiTextGenerateResponse response = new AiTextGenerateResponse();
        response.setContent(content);
        response.setModelId(model.getModelId());
        response.setProvider(model.getProvider());
        response.setModelCode(model.getModelCode());
        return response;
    }

    @Override
    public AiTextGenerateResponse stream(AiTextGenerateRequest request, Consumer<String> deltaConsumer) {
        if (request == null) {
            throw new BusinessException("文本生成请求不能为空");
        }
        AiModelPo model = request.getModelId() != null
                ? requireModel(request.getModelId(), request.getTenantId())
                : selectDefaultTextModel(request.getTenantId());
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException("文本模型未启用");
        }

        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("文本模型 API Key 未配置，请先配置环境变量或模型凭据");
        }

        String prompt = resolvePrompt(request);
        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.system(request.getSystemPrompt().trim()));
        }
        messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(prompt));

        String content = openAiCompatibleClient.chatCompletionStream(model, apiKey, messages, request.getMaxTokens(), deltaConsumer);
        AiTextGenerateResponse response = new AiTextGenerateResponse();
        response.setContent(content);
        response.setModelId(model.getModelId());
        response.setProvider(model.getProvider());
        response.setModelCode(model.getModelCode());
        return response;
    }

    @Override
    public String renderPrompt(AiTextGenerateRequest request) {
        if (request == null) {
            throw new BusinessException("文本生成请求不能为空");
        }
        return resolvePrompt(request);
    }

    private String resolvePrompt(AiTextGenerateRequest request) {
        String prompt;
        if (request.getPromptTemplateId() != null) {
            prompt = promptTemplateService.render(request.getPromptTemplateId(), safeVariables(request.getVariables()));
        } else {
            prompt = request.getUserPrompt();
        }
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("文本生成提示词不能为空");
        }
        StringBuilder builder = new StringBuilder(prompt.trim());
        if (StringUtils.hasText(request.getCustomPrompt())) {
            builder.append("\n\n补充要求：\n").append(request.getCustomPrompt().trim());
        }
        return builder.toString();
    }

    private Map<String, String> safeVariables(Map<String, String> variables) {
        return variables == null ? Map.of() : variables;
    }

    private AiModelPo requireModel(Long modelId, Long tenantId) {
        AiModelPo model = aiModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("文本模型不存在");
        }
        if (!tenantCanUseModel(tenantId, model.getTenantId())) {
            throw new BusinessException("无权访问该文本模型");
        }
        return model;
    }

    private AiModelPo selectDefaultTextModel(Long tenantId) {
        LambdaQueryWrapper<AiModelPo> wrapper = new LambdaQueryWrapper<AiModelPo>()
                .eq(AiModelPo::getModelType, MODEL_TYPE_LLM)
                .eq(AiModelPo::getStatus, STATUS_ENABLED)
                .orderByAsc(AiModelPo::getModelName);
        applyTenantScope(wrapper, tenantId);
        List<AiModelPo> models = aiModelMapper.selectList(wrapper);
        return models.stream()
                .min(Comparator.comparingInt(this::providerPriority).thenComparing(AiModelPo::getModelName))
                .orElseThrow(() -> new BusinessException("文本模型未配置或未启用"));
    }

    private void applyTenantScope(LambdaQueryWrapper<AiModelPo> wrapper, Long tenantId) {
        if (tenantId != null && tenantId > 0) {
            wrapper.and(q -> q.eq(AiModelPo::getTenantId, tenantId)
                    .or().eq(AiModelPo::getTenantId, 0L)
                    .or().isNull(AiModelPo::getTenantId));
        }
    }

    private boolean tenantCanUseModel(Long requestTenantId, Long modelTenantId) {
        return requestTenantId == null || requestTenantId <= 0
                || modelTenantId == null || modelTenantId <= 0
                || requestTenantId.equals(modelTenantId);
    }

    private int providerPriority(AiModelPo model) {
        String provider = normalize(model.getProvider());
        String modelCode = normalize(model.getModelCode());
        if (provider.contains("VOLC") || provider.contains("ARK") || modelCode.contains("DOUBAO")) {
            return 0;
        }
        return 1;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
