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
 * 知识库服务。
 */
public interface IAiKnowledgeBaseService {

    /**
     * 查询知识库。
     *
     * @param query query params
     * @return page result
     */
    PageResult<AiKnowledgeBasePo> selectPage(AiKnowledgeBaseQuery query);

    /**
     * 查询知识库详情。
     *
     * @param kbId knowledge base id
     * @return detail
     */
    AiKnowledgeBasePo selectById(Long kbId);

    /**
     * 查询全部已启用的知识库。
     *
     * @return knowledge base list
     */
    List<AiKnowledgeBasePo> selectAll();

    /**
     * 新增知识库。
     *
     * @param knowledgeBase knowledge base data
     */
    void insert(AiKnowledgeBasePo knowledgeBase);

    /**
     * 修改知识库。
     *
     * @param knowledgeBase knowledge base data
     */
    void update(AiKnowledgeBasePo knowledgeBase);

    /**
     * 删除知识库。
     *
     * @param kbId knowledge base id
     */
    void deleteById(Long kbId);

    /**
     * 查询知识库内的文档。
     *
     * @param kbId knowledge base id
     * @param query query params
     * @return page result
     */
    PageResult<AiDocumentPo> selectDocumentPage(Long kbId, AiDocumentQuery query);

    /**
     * 上传知识库文档。
     *
     * @param kbId knowledge base id
     * @param file file
     */
    void uploadDocument(Long kbId, MultipartFile file);

    /**
     * 重建文档索引。
     *
     * @param docId document id
     */
    void reindexDocument(Long docId);

    /**
     * 删除文档。
     *
     * @param docId document id
     */
    void deleteDocument(Long docId);

    /**
     * 执行命中测试（优先向量检索，失败回退关键词检索）。
     *
     * @param kbId knowledge base id
     * @param query query text
     * @return hit result list
     */
    List<Map<String, Object>> hitTest(Long kbId, String query);

    /**
     * 查询段落详情（供引用来源点击跳转）。
     *
     * @param paragraphId paragraph id
     * @return paragraph detail with kb/doc info
     */
    Map<String, Object> selectParagraphDetail(Long paragraphId);
}
