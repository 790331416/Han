package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.service.IAiImageGenerationService;
import com.han.ai.service.IAiPromptTemplateService;
import com.han.api.ai.domain.AiImageCandidate;
import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI-compatible image generation implementation.
 */
@Service
@RequiredArgsConstructor
public class AiImageGenerationServiceImpl extends AiServiceSupport implements IAiImageGenerationService {

    private static final String MODEL_TYPE_IMAGE = "IMAGE";
    private static final String DEFAULT_IMAGE_SIZE = "2048x2048";

    private final AiModelMapper aiModelMapper;
    private final IAiPromptTemplateService promptTemplateService;
    private final AiModelCredentialResolver credentialResolver;
    private final AiOpenAiCompatibleClient openAiCompatibleClient;

    @Override
    public AiImageGenerateResponse generate(AiImageGenerateRequest request) {
        if (request == null) {
            throw new BusinessException("图片生成请求不能为空");
        }
        AiModelPo model = request.getModelId() != null
                ? requireModel(request.getModelId(), request.getTenantId())
                : selectDefaultImageModel(request.getTenantId());
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException("图片模型未启用");
        }

        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("图片模型 API Key 未配置，请先配置环境变量或模型凭据");
        }

        String prompt = resolvePrompt(request);
        AiOpenAiCompatibleClient.ImageGenerationResult result = openAiCompatibleClient.imageGeneration(
                model, apiKey, prompt, request.getReferenceImageUrls(), request.getCandidateCount(), resolveSize(request));

        AiImageGenerateResponse response = new AiImageGenerateResponse();
        response.setModelId(model.getModelId());
        response.setProvider(model.getProvider());
        response.setModelCode(model.getModelCode());
        response.setPrompt(prompt);
        response.setCandidates(toCandidates(result.images()));
        return response;
    }

    @Override
    public String renderPrompt(AiImageGenerateRequest request) {
        if (request == null) {
            throw new BusinessException("图片生成请求不能为空");
        }
        return resolvePrompt(request);
    }

    private String resolvePrompt(AiImageGenerateRequest request) {
        String prompt;
        if (request.getPromptTemplateId() != null) {
            prompt = promptTemplateService.render(request.getPromptTemplateId(), safeVariables(request.getVariables()));
        } else {
            prompt = request.getUserPrompt();
        }
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("图片生成提示词不能为空");
        }
        StringBuilder builder = new StringBuilder(prompt.trim());
        if (StringUtils.hasText(request.getCustomPrompt())) {
            builder.append("\n\n补充要求：\n").append(request.getCustomPrompt().trim());
        }
        return builder.toString();
    }

    private List<AiImageCandidate> toCandidates(List<AiOpenAiCompatibleClient.GeneratedImage> images) {
        List<AiImageCandidate> candidates = new ArrayList<>();
        if (images == null) {
            return candidates;
        }
        for (AiOpenAiCompatibleClient.GeneratedImage image : images) {
            AiImageCandidate candidate = new AiImageCandidate();
            candidate.setIndex(image.index());
            candidate.setUrl(image.url());
            candidate.setBase64Data(image.base64Data());
            candidate.setMimeType(image.mimeType());
            candidate.setRevisedPrompt(image.revisedPrompt());
            candidates.add(candidate);
        }
        return candidates;
    }

    private String resolveSize(AiImageGenerateRequest request) {
        if (StringUtils.hasText(request.getSize())) {
            return request.getSize().trim();
        }
        String ratio = request.getRatio() == null ? "" : request.getRatio().trim();
        return switch (ratio) {
            case "9:16" -> "1440x2560";
            case "16:9" -> "2560x1440";
            case "3:4" -> "1920x2560";
            case "4:3" -> "2560x1920";
            default -> DEFAULT_IMAGE_SIZE;
        };
    }

    private Map<String, String> safeVariables(Map<String, String> variables) {
        return variables == null ? Map.of() : variables;
    }

    private AiModelPo requireModel(Long modelId, Long tenantId) {
        AiModelPo model = aiModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("图片模型不存在");
        }
        if (!MODEL_TYPE_IMAGE.equalsIgnoreCase(model.getModelType())) {
            throw new BusinessException("当前模型不是图片模型");
        }
        if (!tenantCanUseModel(tenantId, model.getTenantId())) {
            throw new BusinessException("无权访问该图片模型");
        }
        return model;
    }

    private AiModelPo selectDefaultImageModel(Long tenantId) {
        LambdaQueryWrapper<AiModelPo> wrapper = new LambdaQueryWrapper<AiModelPo>()
                .eq(AiModelPo::getModelType, MODEL_TYPE_IMAGE)
                .eq(AiModelPo::getStatus, STATUS_ENABLED)
                .orderByAsc(AiModelPo::getModelName);
        applyTenantScope(wrapper, tenantId);
        List<AiModelPo> models = aiModelMapper.selectList(wrapper);
        return models.stream()
                .min(Comparator.comparingInt(this::providerPriority).thenComparing(AiModelPo::getModelName))
                .orElseThrow(() -> new BusinessException("图片模型未配置或未启用"));
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
        if (provider.contains("VOLC") || provider.contains("ARK") || modelCode.contains("DOUBAO") || modelCode.contains("EP_")) {
            return 0;
        }
        return 1;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
