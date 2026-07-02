package com.han.ai.service.impl;

import com.han.ai.domain.po.AiModelPo;
import com.han.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 客户端（Spring AI OpenAI 兼容协议）
 * <p>
 * 面向火山方舟 doubao-embedding 等 OpenAI 兼容 /embeddings 端点；
 * 模型配置（baseUrl/modelCode/apiKey）来自 AI 模型管理（model_type=EMBEDDING）。
 */
@Slf4j
@Component
class AiEmbeddingClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_BATCH_SIZE = 16;

    /** 按「模型ID+配置指纹」缓存已构建的 EmbeddingModel，配置变更后指纹变化自动重建 */
    private final Map<String, OpenAiEmbeddingModel> modelCache = new ConcurrentHashMap<>();

    float[] embed(AiModelPo model, String apiKey, String text) {
        List<float[]> vectors = embedBatch(model, apiKey, List.of(text));
        if (vectors.isEmpty()) {
            throw new BusinessException("向量模型未返回有效向量");
        }
        return vectors.get(0);
    }

    List<float[]> embedBatch(AiModelPo model, String apiKey, List<String> texts) {
        validate(model, apiKey);
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        OpenAiEmbeddingModel embeddingModel = resolveEmbeddingModel(model, apiKey);
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + MAX_BATCH_SIZE, texts.size()));
            try {
                EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));
                if (response == null || response.getResults() == null || response.getResults().size() != batch.size()) {
                    throw new BusinessException("向量模型返回结果数量与输入不一致");
                }
                response.getResults().forEach(result -> vectors.add(result.getOutput()));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Embedding request failed, provider={}, modelCode={}", model.getProvider(), model.getModelCode(), e);
                throw new BusinessException("向量模型调用失败: " + e.getMessage());
            }
        }
        return vectors;
    }

    private OpenAiEmbeddingModel resolveEmbeddingModel(AiModelPo model, String apiKey) {
        String cacheKey = model.getModelId() + ":" + Objects.hash(model.getBaseUrl(), model.getModelCode(), apiKey);
        return modelCache.computeIfAbsent(cacheKey, key -> new OpenAiEmbeddingModel(
                MetadataMode.EMBED,
                (OpenAiEmbeddingOptions) OpenAiEmbeddingOptions.builder()
                        .model(model.getModelCode().trim())
                        .apiKey(apiKey)
                        .baseUrl(normalizeBaseUrl(model.getBaseUrl()))
                        .timeout(REQUEST_TIMEOUT)
                        .build()));
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private void validate(AiModelPo model, String apiKey) {
        if (model == null) {
            throw new BusinessException("向量模型配置不能为空");
        }
        if (!StringUtils.hasText(model.getBaseUrl())) {
            throw new BusinessException("向量模型 Base URL 未配置");
        }
        if (!StringUtils.hasText(model.getModelCode())) {
            throw new BusinessException("向量模型标识未配置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未找到可用的向量模型 API Key");
        }
    }
}
