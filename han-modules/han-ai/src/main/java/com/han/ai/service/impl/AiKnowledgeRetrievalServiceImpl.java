package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.ai.domain.po.AiKnowledgeBasePo;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.po.AiParagraphPo;
import com.han.ai.mapper.AiKnowledgeBaseMapper;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.mapper.AiParagraphMapper;
import com.han.ai.service.IAiKnowledgeRetrievalService;
import com.han.ai.util.AiVectorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识库检索实现：向量优先 + 关键词回退。
 * <p>
 * 向量：查询文本经知识库绑定的 EMBEDDING 模型向量化，与段落向量做余弦相似度（应用层计算，
 * 段落向量存 ai_paragraph.embedding TEXT；后续可平滑切 pgvector）。
 * 关键词：沿用原 LIKE 命中逻辑，覆盖未配向量模型/未向量化的段落。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeRetrievalServiceImpl implements IAiKnowledgeRetrievalService {

    private static final String STATUS_ENABLED = "0";
    private static final String TYPE_VECTOR = "vector";
    private static final String TYPE_KEYWORD = "keyword";
    /** 单知识库单次载入参与相似度计算的段落上限（超出建议接入 pgvector） */
    private static final int VECTOR_CANDIDATE_LIMIT = 2000;
    /** 向量相似度最低门槛，低于此值视为不相关 */
    private static final double VECTOR_SCORE_THRESHOLD = 0.30D;

    private final AiKnowledgeBaseMapper aiKnowledgeBaseMapper;
    private final AiParagraphMapper aiParagraphMapper;
    private final AiModelMapper aiModelMapper;
    private final AiModelCredentialResolver credentialResolver;
    private final AiEmbeddingClient embeddingClient;

    @Override
    public List<ScoredParagraph> retrieve(List<Long> knowledgeBaseIds, String query, int topK) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || !StringUtils.hasText(query)) {
            return List.of();
        }
        int limit = topK < 1 ? 5 : topK;
        String normalizedQuery = query.trim();

        Map<Long, ScoredParagraph> merged = new LinkedHashMap<>();
        for (ScoredParagraph hit : vectorRetrieve(knowledgeBaseIds, normalizedQuery, limit)) {
            merged.put(hit.paragraph().getParagraphId(), hit);
        }
        if (merged.size() < limit) {
            for (ScoredParagraph hit : keywordRetrieve(knowledgeBaseIds, normalizedQuery, limit)) {
                merged.putIfAbsent(hit.paragraph().getParagraphId(), hit);
            }
        }

        List<ScoredParagraph> results = merged.values().stream()
                .sorted(Comparator.comparingDouble(ScoredParagraph::score).reversed())
                .limit(limit)
                .toList();
        recordHits(results);
        return results;
    }

    // ==================== 向量检索 ====================

    private List<ScoredParagraph> vectorRetrieve(List<Long> knowledgeBaseIds, String query, int limit) {
        Map<Long, List<Long>> kbIdsByModel = groupKnowledgeBasesByEmbeddingModel(knowledgeBaseIds);
        if (kbIdsByModel.isEmpty()) {
            return List.of();
        }
        List<ScoredParagraph> hits = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : kbIdsByModel.entrySet()) {
            hits.addAll(vectorRetrieveForModel(entry.getKey(), entry.getValue(), query, limit));
        }
        return hits;
    }

    private List<ScoredParagraph> vectorRetrieveForModel(Long modelId, List<Long> kbIds, String query, int limit) {
        try {
            AiModelPo model = aiModelMapper.selectById(modelId);
            if (model == null || !STATUS_ENABLED.equals(model.getStatus())) {
                return List.of();
            }
            String apiKey = credentialResolver.resolveApiKey(model);
            if (!StringUtils.hasText(apiKey)) {
                log.debug("Embedding model {} has no usable api key, skip vector retrieval", modelId);
                return List.of();
            }
            float[] queryVector = embeddingClient.embed(model, apiKey, query);

            List<AiParagraphPo> candidates = aiParagraphMapper.selectList(new LambdaQueryWrapper<AiParagraphPo>()
                    .in(AiParagraphPo::getKbId, kbIds)
                    .eq(AiParagraphPo::getStatus, STATUS_ENABLED)
                    .eq(AiParagraphPo::getDelFlag, 0)
                    .isNotNull(AiParagraphPo::getEmbedding)
                    .ne(AiParagraphPo::getEmbedding, "")
                    .last("LIMIT " + VECTOR_CANDIDATE_LIMIT));

            List<ScoredParagraph> scored = new ArrayList<>();
            for (AiParagraphPo paragraph : candidates) {
                float[] paragraphVector = AiVectorUtil.fromJson(paragraph.getEmbedding());
                double similarity = AiVectorUtil.cosineSimilarity(queryVector, paragraphVector);
                if (similarity >= VECTOR_SCORE_THRESHOLD) {
                    scored.add(new ScoredParagraph(paragraph, Math.min(similarity, 0.9999D), TYPE_VECTOR));
                }
            }
            scored.sort(Comparator.comparingDouble(ScoredParagraph::score).reversed());
            return scored.size() > limit ? scored.subList(0, limit) : scored;
        } catch (Exception e) {
            // 向量链路失败降级关键词检索，不阻断对话/命中测试
            log.warn("Vector retrieval failed for embedding model {}, fallback to keyword", modelId, e);
            return List.of();
        }
    }

    private Map<Long, List<Long>> groupKnowledgeBasesByEmbeddingModel(List<Long> knowledgeBaseIds) {
        Map<Long, List<Long>> kbIdsByModel = new HashMap<>();
        for (AiKnowledgeBasePo kb : aiKnowledgeBaseMapper.selectBatchIds(knowledgeBaseIds)) {
            if (kb == null || kb.getEmbeddingModelId() == null) {
                continue;
            }
            kbIdsByModel.computeIfAbsent(kb.getEmbeddingModelId(), key -> new ArrayList<>()).add(kb.getKbId());
        }
        return kbIdsByModel;
    }

    // ==================== 关键词回退 ====================

    private List<ScoredParagraph> keywordRetrieve(List<Long> knowledgeBaseIds, String query, int limit) {
        Set<String> searchTerms = buildSearchTerms(query);
        if (searchTerms.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<AiParagraphPo> wrapper = new LambdaQueryWrapper<AiParagraphPo>()
                .in(AiParagraphPo::getKbId, knowledgeBaseIds)
                .eq(AiParagraphPo::getStatus, STATUS_ENABLED)
                .eq(AiParagraphPo::getDelFlag, 0)
                .and(q -> {
                    boolean first = true;
                    for (String term : searchTerms) {
                        if (first) {
                            q.like(AiParagraphPo::getContent, term);
                            first = false;
                        } else {
                            q.or().like(AiParagraphPo::getContent, term);
                        }
                    }
                })
                .orderByDesc(AiParagraphPo::getHitCount)
                .orderByDesc(AiParagraphPo::getCreateTime)
                .last("LIMIT " + Math.max(limit, 5));
        List<ScoredParagraph> hits = new ArrayList<>();
        for (AiParagraphPo paragraph : aiParagraphMapper.selectList(wrapper)) {
            hits.add(new ScoredParagraph(paragraph, keywordScore(paragraph.getContent(), query), TYPE_KEYWORD));
        }
        return hits;
    }

    private Set<String> buildSearchTerms(String query) {
        Set<String> searchTerms = new LinkedHashSet<>();
        String normalizedForSearch = query.replaceAll("[\"“”'‘’《》「」【】（）()]", " ").trim();
        addSearchTerm(searchTerms, query);
        addSearchTerm(searchTerms, normalizedForSearch);
        for (String term : normalizedForSearch.split("[\\s,，。；;、？！!？：:]+")) {
            addSearchTerm(searchTerms, term);
        }
        if (normalizedForSearch.length() > 12) {
            addSearchTerm(searchTerms, normalizedForSearch.substring(0, 12));
        }
        return searchTerms;
    }

    private void addSearchTerm(Set<String> searchTerms, String rawTerm) {
        if (!StringUtils.hasText(rawTerm)) {
            return;
        }
        String cleaned = rawTerm.trim()
                .replaceAll("^[\"“”'‘’《》「」【】（）()，。；;、？！!？：:]+", "")
                .replaceAll("[\"“”'‘’《》「」【】（）()，。；;、？！!？：:]+$", "")
                .trim();
        if (cleaned.length() >= 2) {
            searchTerms.add(cleaned);
        }
    }

    private double keywordScore(String content, String query) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(query)) {
            return 0D;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int firstIndex = lowerContent.indexOf(lowerQuery);
        if (firstIndex < 0) {
            return 0.1D;
        }
        double lengthFactor = Math.min(0.5D, (double) lowerQuery.length() / Math.max(lowerContent.length(), 1) * 5D);
        double positionFactor = Math.max(0D, 0.5D - (double) firstIndex / Math.max(lowerContent.length(), 1));
        return Math.min(0.99D, 0.2D + lengthFactor + positionFactor);
    }

    // ==================== 命中计数 ====================

    private void recordHits(List<ScoredParagraph> results) {
        for (ScoredParagraph hit : results) {
            try {
                aiParagraphMapper.update(null, new LambdaUpdateWrapper<AiParagraphPo>()
                        .eq(AiParagraphPo::getParagraphId, hit.paragraph().getParagraphId())
                        .setSql("hit_count = COALESCE(hit_count, 0) + 1"));
            } catch (Exception ignored) {
                // 命中计数失败不影响检索结果
            }
        }
    }
}
