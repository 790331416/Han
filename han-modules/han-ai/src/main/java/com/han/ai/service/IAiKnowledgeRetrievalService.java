package com.han.ai.service;

import com.han.ai.domain.po.AiParagraphPo;

import java.util.List;

/**
 * 知识库检索服务（RAG 公共能力）
 * <p>
 * 向量检索优先（知识库配置了 EMBEDDING 模型且段落已向量化），
 * 未向量化部分回退关键词匹配；供命中测试与 AI 对话共用。
 */
public interface IAiKnowledgeRetrievalService {

    /**
     * 跨知识库检索。
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param query 查询文本
     * @param topK 返回条数上限
     * @return 命中段落（按相关度降序）
     */
    List<ScoredParagraph> retrieve(List<Long> knowledgeBaseIds, String query, int topK);

    /**
     * 命中段落 + 相关度得分 + 检索方式。
     *
     * @param paragraph 段落
     * @param score 相关度（向量=余弦相似度 0~1，关键词=启发式 0~1）
     * @param retrievalType vector / keyword
     */
    record ScoredParagraph(AiParagraphPo paragraph, double score, String retrievalType) {
    }
}
