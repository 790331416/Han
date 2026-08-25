package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiDocumentPo;
import com.han.ai.domain.po.AiKnowledgeBasePo;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.po.AiParagraphPo;
import com.han.ai.domain.query.AiDocumentQuery;
import com.han.ai.domain.query.AiKnowledgeBaseQuery;
import com.han.ai.mapper.AiDocumentMapper;
import com.han.ai.mapper.AiKnowledgeBaseMapper;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.mapper.AiParagraphMapper;
import com.han.ai.service.IAiKnowledgeBaseService;
import com.han.ai.service.IAiKnowledgeRetrievalService;
import com.han.ai.util.AiVectorUtil;
import com.han.api.file.FileServiceClient;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Knowledge base service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeBaseServiceImpl extends AiServiceSupport implements IAiKnowledgeBaseService {

    private static final String DOC_STATUS_PENDING = "pending";
    private static final String DOC_STATUS_INDEXING = "indexing";
    private static final String DOC_STATUS_COMPLETED = "completed";
    private static final String DOC_STATUS_FAILED = "failed";
    private static final int MAX_PARAGRAPH_LENGTH = 500;

    private final AiKnowledgeBaseMapper aiKnowledgeBaseMapper;
    private final AiDocumentMapper aiDocumentMapper;
    private final AiParagraphMapper aiParagraphMapper;
    private final AiModelMapper aiModelMapper;
    private final AiModelCredentialResolver credentialResolver;
    private final AiEmbeddingClient embeddingClient;
    private final IAiKnowledgeRetrievalService knowledgeRetrievalService;
    private final AiDocumentTextExtractor textExtractor;
    private final FileServiceClient fileServiceClient;

    @Value("${han.ai.document-storage-path:./data/ai-documents}")
    private String documentStoragePath;

    @Override
    public PageResult<AiKnowledgeBasePo> selectPage(AiKnowledgeBaseQuery query) {
        AiKnowledgeBaseQuery safeQuery = query != null ? query : new AiKnowledgeBaseQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiKnowledgeBasePo> page = aiKnowledgeBaseMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiKnowledgeBasePo selectById(Long kbId) {
        return requireKnowledgeBase(kbId);
    }

    @Override
    public List<AiKnowledgeBasePo> selectAll() {
        LambdaQueryWrapper<AiKnowledgeBasePo> wrapper = new LambdaQueryWrapper<AiKnowledgeBasePo>()
                .eq(AiKnowledgeBasePo::getStatus, STATUS_ENABLED)
                .orderByAsc(AiKnowledgeBasePo::getKbName);
        applyKnowledgeBaseTenantScope(wrapper);
        return aiKnowledgeBaseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiKnowledgeBasePo knowledgeBase) {
        validateKnowledgeBase(knowledgeBase, false);
        ensureKnowledgeBaseNameUnique(knowledgeBase.getKbName(), null);
        normalizeKnowledgeBase(knowledgeBase);
        fillKnowledgeBaseCreateAudit(knowledgeBase);
        knowledgeBase.setDocumentCount(0);
        knowledgeBase.setParagraphCount(0);
        knowledgeBase.setCharCount(0L);
        aiKnowledgeBaseMapper.insert(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiKnowledgeBasePo knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.getKbId() == null) {
            throw new BusinessException("知识库ID不能为空");
        }
        AiKnowledgeBasePo existing = requireKnowledgeBase(knowledgeBase.getKbId());
        existing.setKbName(knowledgeBase.getKbName());
        existing.setDescription(knowledgeBase.getDescription());
        existing.setKbType(knowledgeBase.getKbType());
        existing.setEmbeddingModelId(knowledgeBase.getEmbeddingModelId());
        existing.setStatus(knowledgeBase.getStatus());
        validateKnowledgeBase(existing, true);
        ensureKnowledgeBaseNameUnique(existing.getKbName(), existing.getKbId());
        normalizeKnowledgeBase(existing);
        fillKnowledgeBaseUpdateAudit(existing);
        aiKnowledgeBaseMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long kbId) {
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(kbId);
        List<AiDocumentPo> documents = aiDocumentMapper.selectList(new LambdaQueryWrapper<AiDocumentPo>()
                .eq(AiDocumentPo::getKbId, kbId));
        for (AiDocumentPo document : documents) {
            deleteDocumentFile(document);
        }
        aiParagraphMapper.delete(new LambdaQueryWrapper<AiParagraphPo>().eq(AiParagraphPo::getKbId, kbId));
        aiDocumentMapper.delete(new LambdaQueryWrapper<AiDocumentPo>().eq(AiDocumentPo::getKbId, kbId));
        aiKnowledgeBaseMapper.deleteById(kbId);
        deleteKnowledgeBaseDirectory(knowledgeBase.getKbId());
    }

    @Override
    public PageResult<AiDocumentPo> selectDocumentPage(Long kbId, AiDocumentQuery query) {
        requireKnowledgeBase(kbId);
        AiDocumentQuery safeQuery = query != null ? query : new AiDocumentQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        LambdaQueryWrapper<AiDocumentPo> wrapper = new LambdaQueryWrapper<AiDocumentPo>()
                .eq(AiDocumentPo::getKbId, kbId)
                .like(StringUtils.hasText(safeQuery.getDocName()), AiDocumentPo::getDocName, safeQuery.getDocName())
                .eq(StringUtils.hasText(safeQuery.getIndexStatus()), AiDocumentPo::getIndexStatus, safeQuery.getIndexStatus())
                .orderByDesc(AiDocumentPo::getUpdateTime)
                .orderByDesc(AiDocumentPo::getCreateTime);
        applyDocumentTenantScope(wrapper);
        Page<AiDocumentPo> page = aiDocumentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadDocument(Long kbId, MultipartFile file) {
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(kbId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String docName = StringUtils.hasText(originalFilename) ? originalFilename.trim() : "unnamed.txt";
        String docType = textExtractor.resolveDocumentType(docName);
        StoredDocument storedDocument = storeFile(docName, file);

        AiDocumentPo document = new AiDocumentPo();
        document.setKbId(kbId);
        document.setDocName(docName);
        document.setDocType(docType);
        document.setFilePath(storedDocument.filePath());
        document.setFileId(storedDocument.fileId());
        document.setFileSize(file.getSize());
        document.setCharCount(0L);
        document.setParagraphCount(0);
        document.setIndexStatus(DOC_STATUS_PENDING);
        document.setIndexError("");
        document.setStatus(STATUS_ENABLED);
        fillDocumentCreateAudit(document);
        aiDocumentMapper.insert(document);

        indexDocument(knowledgeBase, document);
        refreshKnowledgeBaseStats(kbId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindexDocument(Long docId) {
        AiDocumentPo document = requireDocument(docId);
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(document.getKbId());
        aiParagraphMapper.delete(new LambdaQueryWrapper<AiParagraphPo>().eq(AiParagraphPo::getDocId, docId));
        indexDocument(knowledgeBase, document);
        refreshKnowledgeBaseStats(document.getKbId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long docId) {
        AiDocumentPo document = requireDocument(docId);
        aiParagraphMapper.delete(new LambdaQueryWrapper<AiParagraphPo>().eq(AiParagraphPo::getDocId, docId));
        aiDocumentMapper.deleteById(docId);
        deleteDocumentFile(document);
        refreshKnowledgeBaseStats(document.getKbId());
    }

    @Override
    public List<Map<String, Object>> hitTest(Long kbId, String query) {
        requireKnowledgeBase(kbId);
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<IAiKnowledgeRetrievalService.ScoredParagraph> hits =
                knowledgeRetrievalService.retrieve(List.of(kbId), query.trim(), 10);
        if (hits.isEmpty()) {
            return List.of();
        }

        Map<Long, String> documentNames = aiDocumentMapper.selectBatchIds(hits.stream()
                        .map(hit -> hit.paragraph().getDocId())
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(AiDocumentPo::getDocId, AiDocumentPo::getDocName, (left, _right) -> left, LinkedHashMap::new));

        return hits.stream()
                .map(hit -> {
                    AiParagraphPo paragraph = hit.paragraph();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("paragraphId", paragraph.getParagraphId());
                    item.put("title", StringUtils.hasText(paragraph.getTitle())
                            ? paragraph.getTitle()
                            : documentNames.getOrDefault(paragraph.getDocId(), "未命名文档"));
                    item.put("docName", documentNames.getOrDefault(paragraph.getDocId(), ""));
                    item.put("content", paragraph.getContent());
                    item.put("score", hit.score());
                    item.put("retrievalType", hit.retrievalType());
                    return item;
                })
                .toList();
    }

    @Override
    public Map<String, Object> selectParagraphDetail(Long paragraphId) {
        if (paragraphId == null) {
            throw new BusinessException("段落ID不能为空");
        }
        AiParagraphPo paragraph = aiParagraphMapper.selectById(paragraphId);
        if (paragraph == null || (paragraph.getDelFlag() != null && paragraph.getDelFlag() != 0)) {
            throw new BusinessException("段落不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(paragraph.getTenantId())) {
            throw new BusinessException("无权访问该段落");
        }
        AiKnowledgeBasePo knowledgeBase = aiKnowledgeBaseMapper.selectById(paragraph.getKbId());
        AiDocumentPo document = aiDocumentMapper.selectById(paragraph.getDocId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("paragraphId", paragraph.getParagraphId());
        detail.put("title", paragraph.getTitle());
        detail.put("content", paragraph.getContent());
        detail.put("charCount", paragraph.getCharCount());
        detail.put("hitCount", paragraph.getHitCount());
        detail.put("kbId", paragraph.getKbId());
        detail.put("kbName", knowledgeBase != null ? knowledgeBase.getKbName() : null);
        detail.put("docId", paragraph.getDocId());
        detail.put("docName", document != null ? document.getDocName() : null);
        detail.put("vectorized", StringUtils.hasText(paragraph.getEmbedding()));
        return detail;
    }

    private LambdaQueryWrapper<AiKnowledgeBasePo> buildQueryWrapper(AiKnowledgeBaseQuery query) {
        LambdaQueryWrapper<AiKnowledgeBasePo> wrapper = new LambdaQueryWrapper<AiKnowledgeBasePo>()
                .like(StringUtils.hasText(query.getKbName()), AiKnowledgeBasePo::getKbName, query.getKbName())
                .eq(StringUtils.hasText(query.getKbType()), AiKnowledgeBasePo::getKbType, query.getKbType())
                .eq(StringUtils.hasText(query.getStatus()), AiKnowledgeBasePo::getStatus, query.getStatus())
                .orderByDesc(AiKnowledgeBasePo::getUpdateTime)
                .orderByDesc(AiKnowledgeBasePo::getCreateTime);
        applyKnowledgeBaseTenantScope(wrapper);
        return wrapper;
    }

    private void applyKnowledgeBaseTenantScope(LambdaQueryWrapper<AiKnowledgeBasePo> wrapper) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiKnowledgeBasePo::getTenantId, tenantId);
        }
    }

    private void applyDocumentTenantScope(LambdaQueryWrapper<AiDocumentPo> wrapper) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiDocumentPo::getTenantId, tenantId);
        }
    }

    private AiKnowledgeBasePo requireKnowledgeBase(Long kbId) {
        if (kbId == null) {
            throw new BusinessException("知识库ID不能为空");
        }
        AiKnowledgeBasePo knowledgeBase = aiKnowledgeBaseMapper.selectById(kbId);
        if (knowledgeBase == null) {
            throw new BusinessException("知识库不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(knowledgeBase.getTenantId())) {
            throw new BusinessException("无权访问该知识库");
        }
        return knowledgeBase;
    }

    private AiDocumentPo requireDocument(Long docId) {
        if (docId == null) {
            throw new BusinessException("文档ID不能为空");
        }
        AiDocumentPo document = aiDocumentMapper.selectById(docId);
        if (document == null) {
            throw new BusinessException("文档不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(document.getTenantId())) {
            throw new BusinessException("无权访问该文档");
        }
        return document;
    }

    private void validateKnowledgeBase(AiKnowledgeBasePo knowledgeBase, boolean update) {
        if (knowledgeBase == null) {
            throw new BusinessException("知识库信息不能为空");
        }
        if (update && knowledgeBase.getKbId() == null) {
            throw new BusinessException("知识库ID不能为空");
        }
        if (!StringUtils.hasText(knowledgeBase.getKbName())) {
            throw new BusinessException("知识库名称不能为空");
        }
        if (!StringUtils.hasText(knowledgeBase.getKbType())) {
            throw new BusinessException("知识库类型不能为空");
        }
        if (!STATUS_ENABLED.equals(knowledgeBase.getStatus()) && !STATUS_DISABLED.equals(knowledgeBase.getStatus())) {
            throw new BusinessException("知识库状态不合法");
        }
    }

    private void ensureKnowledgeBaseNameUnique(String kbName, Long excludeId) {
        LambdaQueryWrapper<AiKnowledgeBasePo> wrapper = new LambdaQueryWrapper<AiKnowledgeBasePo>()
                .eq(AiKnowledgeBasePo::getKbName, kbName);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiKnowledgeBasePo::getTenantId, tenantId);
        }
        if (excludeId != null) {
            wrapper.ne(AiKnowledgeBasePo::getKbId, excludeId);
        }
        if (aiKnowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("知识库名称已存在");
        }
    }

    private void normalizeKnowledgeBase(AiKnowledgeBasePo knowledgeBase) {
        knowledgeBase.setKbName(trimToNull(knowledgeBase.getKbName()));
        knowledgeBase.setDescription(trimToNull(knowledgeBase.getDescription()));
        knowledgeBase.setKbType(trimToNull(knowledgeBase.getKbType()));
        knowledgeBase.setStatus(StringUtils.hasText(knowledgeBase.getStatus())
                ? knowledgeBase.getStatus().trim()
                : STATUS_ENABLED);
    }

    private void fillKnowledgeBaseCreateAudit(AiKnowledgeBasePo knowledgeBase) {
        knowledgeBase.setTenantId(resolveTenantIdForWrite());
        knowledgeBase.setCreateBy(resolveOperator());
        knowledgeBase.setCreateTime(now());
        knowledgeBase.setUpdateBy(resolveOperator());
        knowledgeBase.setUpdateTime(now());
    }

    private void fillKnowledgeBaseUpdateAudit(AiKnowledgeBasePo knowledgeBase) {
        knowledgeBase.setUpdateBy(resolveOperator());
        knowledgeBase.setUpdateTime(now());
    }

    private void fillDocumentCreateAudit(AiDocumentPo document) {
        document.setTenantId(resolveTenantIdForWrite());
        document.setCreateBy(resolveOperator());
        document.setCreateTime(now());
        document.setUpdateBy(resolveOperator());
        document.setUpdateTime(now());
    }

    private void fillDocumentUpdateAudit(AiDocumentPo document) {
        document.setUpdateBy(resolveOperator());
        document.setUpdateTime(now());
    }

    private void indexDocument(AiKnowledgeBasePo knowledgeBase, AiDocumentPo document) {
        document.setIndexStatus(DOC_STATUS_INDEXING);
        document.setIndexError("");
        fillDocumentUpdateAudit(document);
        aiDocumentMapper.updateById(document);

        try {
            IndexOutcome outcome = buildIndexOutcome(knowledgeBase, document);
            String embeddingWarning = outcome.failed() ? "" : embedParagraphs(knowledgeBase, outcome.paragraphs());
            aiParagraphMapper.delete(new LambdaQueryWrapper<AiParagraphPo>().eq(AiParagraphPo::getDocId, document.getDocId()));
            for (AiParagraphPo paragraph : outcome.paragraphs()) {
                aiParagraphMapper.insert(paragraph);
            }
            document.setCharCount((long) outcome.charCount());
            document.setParagraphCount(outcome.paragraphs().size());
            document.setIndexStatus(outcome.failed() ? DOC_STATUS_FAILED : DOC_STATUS_COMPLETED);
            document.setIndexError(StringUtils.hasText(outcome.errorMessage()) ? outcome.errorMessage() : embeddingWarning);
            fillDocumentUpdateAudit(document);
            aiDocumentMapper.updateById(document);
        } catch (IOException ex) {
            document.setIndexStatus(DOC_STATUS_FAILED);
            document.setIndexError("文档读取失败: " + ex.getMessage());
            document.setParagraphCount(0);
            document.setCharCount(0L);
            fillDocumentUpdateAudit(document);
            aiDocumentMapper.updateById(document);
        }
    }

    /**
     * 段落向量化：知识库配置 EMBEDDING 模型时写入向量；失败仅降级（保留段落做关键词检索），返回告警信息。
     */
    private String embedParagraphs(AiKnowledgeBasePo knowledgeBase, List<AiParagraphPo> paragraphs) {
        if (knowledgeBase.getEmbeddingModelId() == null || paragraphs.isEmpty()) {
            return "";
        }
        try {
            AiModelPo model = aiModelMapper.selectById(knowledgeBase.getEmbeddingModelId());
            if (model == null || !STATUS_ENABLED.equals(model.getStatus())) {
                return "向量化跳过: 绑定的向量模型不存在或已停用";
            }
            String apiKey = credentialResolver.resolveApiKey(model);
            if (!StringUtils.hasText(apiKey)) {
                return "向量化跳过: 向量模型未配置可用 API Key";
            }
            List<String> contents = paragraphs.stream().map(AiParagraphPo::getContent).toList();
            List<float[]> vectors = embeddingClient.embedBatch(model, apiKey, contents);
            for (int i = 0; i < paragraphs.size() && i < vectors.size(); i++) {
                paragraphs.get(i).setEmbedding(AiVectorUtil.toJson(vectors.get(i)));
            }
            return "";
        } catch (Exception e) {
            log.warn("Paragraph embedding failed, kbId={}, docParagraphs={}", knowledgeBase.getKbId(), paragraphs.size(), e);
            return "向量化失败(已降级关键词检索): " + e.getMessage();
        }
    }

    private IndexOutcome buildIndexOutcome(AiKnowledgeBasePo knowledgeBase, AiDocumentPo document) throws IOException {
        MaterializedDocument materialized = materializeDocument(document);
        if (materialized == null || !Files.exists(materialized.path())) {
            return new IndexOutcome(List.of(), 0, true, "文档文件不存在，无法重建索引");
        }
        String docType = textExtractor.resolveDocumentType(document.getDocName());
        String content;
        try {
            content = textExtractor.extract(materialized.path(), docType);
        } finally {
            if (materialized.temporary()) {
                Files.deleteIfExists(materialized.path());
            }
        }
        if (!StringUtils.hasText(content)) {
            String message = switch (docType) {
                case "pdf", "docx", "xlsx" -> "未能从文档中提取到文本内容（可能为扫描件、图片型或空文档）";
                default -> "文档内容为空，未生成索引";
            };
            return new IndexOutcome(List.of(), 0, true, message);
        }

        String normalizedContent = normalizeContent(content);
        List<String> paragraphContents = splitParagraphs(normalizedContent);
        List<AiParagraphPo> paragraphs = new ArrayList<>();
        LocalDateTime currentTime = now();
        String operator = resolveOperator();
        int order = 1;
        for (String paragraphContent : paragraphContents) {
            AiParagraphPo paragraph = new AiParagraphPo();
            paragraph.setDocId(document.getDocId());
            paragraph.setKbId(knowledgeBase.getKbId());
            paragraph.setTitle(document.getDocName() + "#" + order++);
            paragraph.setContent(paragraphContent);
            paragraph.setCharCount(paragraphContent.length());
            paragraph.setHitCount(0);
            paragraph.setEmbedding(null);
            paragraph.setStatus(STATUS_ENABLED);
            paragraph.setTenantId(document.getTenantId());
            paragraph.setCreateBy(operator);
            paragraph.setCreateTime(currentTime);
            paragraph.setUpdateBy(operator);
            paragraph.setUpdateTime(currentTime);
            paragraph.setDelFlag(0);
            paragraphs.add(paragraph);
        }
        return new IndexOutcome(paragraphs, normalizedContent.length(), false, "");
    }

    private String normalizeContent(String content) {
        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 空行分段 + 超长硬切（包内可见便于单测直接断言分段结果）。
     */
    static List<String> splitParagraphs(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String block : content.split("\\n\\n")) {
            String trimmedBlock = block.trim();
            if (!StringUtils.hasText(trimmedBlock)) {
                continue;
            }
            if (trimmedBlock.length() <= MAX_PARAGRAPH_LENGTH) {
                result.add(trimmedBlock);
                continue;
            }
            int start = 0;
            while (start < trimmedBlock.length()) {
                int end = Math.min(start + MAX_PARAGRAPH_LENGTH, trimmedBlock.length());
                result.add(trimmedBlock.substring(start, end).trim());
                start = end;
            }
        }
        return result;
    }

    private StoredDocument storeFile(String docName, MultipartFile file) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("han-kb-upload-", suffix(docName));
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            var result = fileServiceClient.uploadInternal(new FileSystemResource(temporary),
                    "ai_knowledge_document", "PRIVATE", null);
            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().getId() == null) {
                throw new BusinessException(result == null || !StringUtils.hasText(result.getMsg())
                        ? "知识库文档归档失败" : result.getMsg());
            }
            Long fileId = result.getData().getId();
            return new StoredDocument(fileId, "han:" + fileId);
        } catch (IOException ex) {
            throw new BusinessException("保存文档失败: " + ex.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 临时文件由系统兜底清理，不影响已经归档的文件。
                }
            }
        }
    }

    private MaterializedDocument materializeDocument(AiDocumentPo document) throws IOException {
        if (document.getFileId() != null) {
            var result = fileServiceClient.temporaryUrl(document.getFileId());
            if (result == null || !result.isSuccess() || !StringUtils.hasText(result.getData())) {
                throw new IOException(result == null || !StringUtils.hasText(result.getMsg())
                        ? "知识库文档下载地址获取失败" : result.getMsg());
            }
            Path temporary = Files.createTempFile("han-kb-index-", suffix(document.getDocName()));
            try (InputStream input = new URL(result.getData()).openStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            return new MaterializedDocument(temporary, true);
        }
        if (!StringUtils.hasText(document.getFilePath())) {
            return null;
        }
        return new MaterializedDocument(Paths.get(document.getFilePath()), false);
    }

    private String suffix(String docName) {
        int lastDot = docName == null ? -1 : docName.lastIndexOf('.');
        return lastDot >= 0 ? docName.substring(lastDot) : ".tmp";
    }

    private void refreshKnowledgeBaseStats(Long kbId) {
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(kbId);
        List<AiDocumentPo> documents = aiDocumentMapper.selectList(new LambdaQueryWrapper<AiDocumentPo>()
                .eq(AiDocumentPo::getKbId, kbId));
        List<AiParagraphPo> paragraphs = aiParagraphMapper.selectList(new LambdaQueryWrapper<AiParagraphPo>()
                .eq(AiParagraphPo::getKbId, kbId)
                .eq(AiParagraphPo::getDelFlag, 0));
        knowledgeBase.setDocumentCount(documents.size());
        knowledgeBase.setParagraphCount(paragraphs.size());
        knowledgeBase.setCharCount(paragraphs.stream()
                .map(AiParagraphPo::getCharCount)
                .filter(value -> value != null && value > 0)
                .mapToLong(Integer::longValue)
                .sum());
        fillKnowledgeBaseUpdateAudit(knowledgeBase);
        aiKnowledgeBaseMapper.updateById(knowledgeBase);
    }

    private void deleteDocumentFile(AiDocumentPo document) {
        if (document.getFileId() != null) {
            try {
                var result = fileServiceClient.removeInternal(document.getFileId());
                if (result == null || !result.isSuccess()) {
                    log.warn("知识库对象存储文件删除未完成，fileId={}", document.getFileId());
                }
            } catch (Exception ex) {
                log.warn("知识库对象存储文件删除调用失败，fileId={}", document.getFileId(), ex);
            }
            return;
        }
        String filePath = document.getFilePath();
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException ignored) {
            // Ignore file cleanup failure and keep database state consistent.
        }
    }

    private void deleteKnowledgeBaseDirectory(Long kbId) {
        Path directory = Paths.get(documentStoragePath, String.valueOf(kbId));
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Ignore cleanup failure.
                }
            });
        } catch (IOException ignored) {
            // Ignore cleanup failure.
        }
    }

    private record IndexOutcome(List<AiParagraphPo> paragraphs, int charCount, boolean failed, String errorMessage) {
    }

    private record StoredDocument(Long fileId, String filePath) {
    }

    private record MaterializedDocument(Path path, boolean temporary) {
    }
}
