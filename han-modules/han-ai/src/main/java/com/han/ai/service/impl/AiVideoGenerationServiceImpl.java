package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.service.IAiPromptTemplateService;
import com.han.ai.service.IAiVideoGenerationService;
import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateResponse;
import com.han.api.ai.domain.AiVideoTaskQueryRequest;
import com.han.api.ai.domain.AiVideoTaskQueryResponse;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI-compatible video generation implementation.
 */
@Service
@RequiredArgsConstructor
public class AiVideoGenerationServiceImpl extends AiServiceSupport implements IAiVideoGenerationService {

    private static final String MODEL_TYPE_VIDEO = "VIDEO";

    private final AiModelMapper aiModelMapper;
    private final IAiPromptTemplateService promptTemplateService;
    private final AiModelCredentialResolver credentialResolver;
    private final AiOpenAiCompatibleClient openAiCompatibleClient;

    @Override
    public AiVideoGenerateResponse generate(AiVideoGenerateRequest request) {
        if (request == null) {
            throw new BusinessException("视频生成请求不能为空");
        }
        AiModelPo model = request.getModelId() != null
                ? requireModel(request.getModelId(), request.getTenantId())
                : selectDefaultVideoModel(request.getTenantId());
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException("视频模型未启用");
        }

        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("视频模型 API Key 未配置，请先配置环境变量或模型凭据");
        }

        String prompt = resolvePrompt(request);
        AiOpenAiCompatibleClient.VideoGenerationResult result = openAiCompatibleClient.videoGeneration(
                model, apiKey, prompt, request.getReferenceImageUrl(), request.getDurationSec(),
                request.getRatio(), request.getResolution());

        AiVideoGenerateResponse response = new AiVideoGenerateResponse();
        response.setModelId(model.getModelId());
        response.setProvider(model.getProvider());
        response.setModelCode(model.getModelCode());
        response.setPrompt(prompt);
        response.setProviderTaskId(result.providerTaskId());
        response.setTaskStatus(result.taskStatus());
        response.setProgress(result.progress());
        response.setVideoUrl(result.videoUrl());
        response.setRawResponse(result.rawResponse());
        return response;
    }

    @Override
    public AiVideoTaskQueryResponse queryTask(AiVideoTaskQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.getProviderTaskId())) {
            throw new BusinessException("视频任务ID不能为空");
        }
        AiModelPo model = request.getModelId() != null
                ? requireModel(request.getModelId(), request.getTenantId())
                : selectDefaultVideoModel(request.getTenantId());
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException("视频模型未启用");
        }
        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("视频模型 API Key 未配置，请先配置环境变量或模型凭据");
        }

        AiOpenAiCompatibleClient.VideoGenerationResult result = openAiCompatibleClient.queryVideoGenerationTask(
                model, apiKey, request.getProviderTaskId());
        AiVideoTaskQueryResponse response = new AiVideoTaskQueryResponse();
        response.setModelId(model.getModelId());
        response.setProvider(model.getProvider());
        response.setModelCode(model.getModelCode());
        response.setProviderTaskId(result.providerTaskId());
        response.setTaskStatus(result.taskStatus());
        response.setProgress(result.progress());
        response.setVideoUrl(result.videoUrl());
        response.setRawResponse(result.rawResponse());
        return response;
    }

    @Override
    public String renderPrompt(AiVideoGenerateRequest request) {
        if (request == null) {
            throw new BusinessException("视频生成请求不能为空");
        }
        return resolvePrompt(request);
    }

    private String resolvePrompt(AiVideoGenerateRequest request) {
        String prompt;
        if (request.getPromptTemplateId() != null) {
            prompt = promptTemplateService.render(request.getPromptTemplateId(), safeVariables(request.getVariables()));
        } else {
            prompt = request.getUserPrompt();
        }
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("视频生成提示词不能为空");
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
            throw new BusinessException("视频模型不存在");
        }
        if (!MODEL_TYPE_VIDEO.equalsIgnoreCase(model.getModelType())) {
            throw new BusinessException("当前模型不是视频模型");
        }
        if (!tenantCanUseModel(tenantId, model.getTenantId())) {
            throw new BusinessException("无权访问该视频模型");
        }
        return model;
    }

    private AiModelPo selectDefaultVideoModel(Long tenantId) {
        LambdaQueryWrapper<AiModelPo> wrapper = new LambdaQueryWrapper<AiModelPo>()
                .eq(AiModelPo::getModelType, MODEL_TYPE_VIDEO)
                .eq(AiModelPo::getStatus, STATUS_ENABLED)
                .orderByAsc(AiModelPo::getModelName);
        applyTenantScope(wrapper, tenantId);
        List<AiModelPo> models = aiModelMapper.selectList(wrapper);
        return models.stream()
                .min(Comparator.comparingInt(this::providerPriority).thenComparing(AiModelPo::getModelName))
                .orElseThrow(() -> new BusinessException("视频模型未配置或未启用"));
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
        if (provider.contains("VOLC") || provider.contains("ARK") || modelCode.contains("DOUBAO")
                || modelCode.contains("SEEDANCE")) {
            return 0;
        }
        return 1;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
