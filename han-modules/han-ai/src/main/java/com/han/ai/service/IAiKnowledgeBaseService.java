package com.han.ai.service;

import com.han.ai.domain.po.AiDocumentPo;
import com.han.ai.domain.po.AiKnowledgeBasePo;
import com.han.ai.domain.query.AiDocumentQuery;
import com.han.ai.domain.query.AiKnowledgeBaseQuery;
import com.han.common.core.domain.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Knowledge base service.
 */
public interface IAiKnowledgeBaseService {

    /**
     * Query knowledge bases.
     *
     * @param query query params
     * @return page result
     */
    PageResult<AiKnowledgeBasePo> selectPage(AiKnowledgeBaseQuery query);

    /**
     * Query knowledge base detail.
     *
     * @param kbId knowledge base id
     * @return detail
     */
    AiKnowledgeBasePo selectById(Long kbId);

    /**
     * Query all enabled knowledge bases.
     *
     * @return knowledge base list
     */
    List<AiKnowledgeBasePo> selectAll();

    /**
     * Insert knowledge base.
     *
     * @param knowledgeBase knowledge base data
     */
    void insert(AiKnowledgeBasePo knowledgeBase);

    /**
     * Update knowledge base.
     *
     * @param knowledgeBase knowledge base data
     */
    void update(AiKnowledgeBasePo knowledgeBase);

    /**
     * Delete knowledge base.
     *
     * @param kbId knowledge base id
     */
    void deleteById(Long kbId);

    /**
     * Query documents in a knowledge base.
     *
     * @param kbId knowledge base id
     * @param query query params
     * @return page result
     */
    PageResult<AiDocumentPo> selectDocumentPage(Long kbId, AiDocumentQuery query);

    /**
     * Upload knowledge document.
     *
     * @param kbId knowledge base id
     * @param file file
     */
    void uploadDocument(Long kbId, MultipartFile file);

    /**
     * Reindex document.
     *
     * @param docId document id
     */
    void reindexDocument(Long docId);

    /**
     * Delete document.
     *
     * @param docId document id
     */
    void deleteDocument(Long docId);

    /**
     * Run hit test (vector first, keyword fallback).
     *
     * @param kbId knowledge base id
     * @param query query text
     * @return hit result list
     */
    List<Map<String, Object>> hitTest(Long kbId, String query);

    /**
     * Query paragraph detail (for citation click-through).
     *
     * @param paragraphId paragraph id
     * @return paragraph detail with kb/doc info
     */
    Map<String, Object> selectParagraphDetail(Long paragraphId);
}
