package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final TransactionTemplate transactionTemplate;

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
    public void deleteById(Long kbId) {
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(kbId);
        transactionTemplate.executeWithoutResult(status -> {
            aiParagraphMapper.delete(new LambdaQueryWrapper<AiParagraphPo>().eq(AiParagraphPo::getKbId, kbId));
            aiDocumentMapper.delete(new LambdaQueryWrapper<AiDocumentPo>().eq(AiDocumentPo::getKbId, kbId));
            aiKnowledgeBaseMapper.deleteById(kbId);
        });
        // 物理文件在数据库提交之后再清理，事务回滚时源文件仍然完整
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

    /**
     * 上传并建索引：文档落库走短事务，文本解析与远程 Embedding 一律在事务外执行，
     * 避免远程调用期间独占数据库连接。落盘文件在建档失败时同步清理，不留孤儿文件。
     */
    @Override
    public void uploadDocument(Long kbId, MultipartFile file) {
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(kbId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String docName = StringUtils.hasText(originalFilename) ? originalFilename.trim() : "unnamed.txt";
        String docType = textExtractor.resolveDocumentType(docName);
        Path storedPath = storeFile(kbId, docName, file);

        AiDocumentPo document = new AiDocumentPo();
        document.setKbId(kbId);
        document.setDocName(docName);
        document.setDocType(docType);
        document.setFilePath(storedPath.toString());
        document.setFileSize(file.getSize());
        document.setCharCount(0L);
        document.setParagraphCount(0);
        document.setIndexStatus(DOC_STATUS_PENDING);
        document.setIndexError("");
        document.setStatus(STATUS_ENABLED);
        fillDocumentCreateAudit(document);
        try {
            transactionTemplate.executeWithoutResult(status -> aiDocumentMapper.insert(document));
        } catch (RuntimeException ex) {
            deleteDocumentFile(storedPath.toString());
            throw ex;
        }

        indexDocument(knowledgeBase, document);
    }

    /**
     * 重建索引：旧段落只在新段落成功构建之后才替换（同一事务内先删后插）。
     * 源文件缺失、解析失败或内容为空时保留原有段落与向量，只把失败原因写回文档状态。
     */
    @Override
    public void reindexDocument(Long docId) {
        AiDocumentPo document = requireDocument(docId);
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(document.getKbId());
        indexDocument(knowledgeBase, document);
    }

    @Override
    public void deleteDocument(Long docId) {
        AiDocumentPo document = requireDocument(docId);
        transactionTemplate.executeWithoutResult(status -> {
            aiParagraphMapper.delete(new LambdaQueryWrapper<AiParagraphPo>().eq(AiParagraphPo::getDocId, docId));
            aiDocumentMapper.deleteById(docId);
            refreshKnowledgeBaseStats(document.getKbId());
        });
        // 物理文件在数据库提交之后再删，事务回滚时文件仍可用于重建索引
        deleteDocumentFile(document.getFilePath());
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

    /**
     * 三段式建索引：短事务置 indexing → 事务外解析与向量化 → 短事务落库。
     * <p>
     * 关键约束：只有成功产出新段落时才替换旧段落；任何失败分支都保留既有段落与向量，
     * 只回写文档的失败状态与原因，避免「重建索引失败反而清空知识库」的不可逆数据丢失。
     */
    private void indexDocument(AiKnowledgeBasePo knowledgeBase, AiDocumentPo document) {
        markIndexing(document);

        IndexOutcome outcome;
        String embeddingWarning;
        try {
            // 事务外：文件读取、文本抽取、分片
            outcome = buildIndexOutcome(knowledgeBase, document);
            // 事务外：远程 Embedding 调用（可能耗时数十秒）
            embeddingWarning = outcome.failed() ? "" : embedParagraphs(knowledgeBase, outcome.paragraphs());
        } catch (IOException ex) {
            applyIndexFailure(document, "文档读取失败: " + ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            log.warn("Document index failed, docId={}", document.getDocId(), ex);
            applyIndexFailure(document, "文档解析失败: " + ex.getMessage());
            return;
        }

        if (outcome.failed()) {
            applyIndexFailure(document, outcome.errorMessage());
            return;
        }

        String indexError = embeddingWarning;
        transactionTemplate.executeWithoutResult(status -> {
            aiParagraphMapper.delete(new LambdaQueryWrapper<AiParagraphPo>().eq(AiParagraphPo::getDocId, document.getDocId()));
            for (AiParagraphPo paragraph : outcome.paragraphs()) {
                aiParagraphMapper.insert(paragraph);
            }
            document.setCharCount((long) outcome.charCount());
            document.setParagraphCount(outcome.paragraphs().size());
            document.setIndexStatus(DOC_STATUS_COMPLETED);
            document.setIndexError(indexError);
            fillDocumentUpdateAudit(document);
            aiDocumentMapper.updateById(document);
            refreshKnowledgeBaseStats(document.getKbId());
        });
    }

    private void markIndexing(AiDocumentPo document) {
        document.setIndexStatus(DOC_STATUS_INDEXING);
        document.setIndexError("");
        fillDocumentUpdateAudit(document);
        transactionTemplate.executeWithoutResult(status -> aiDocumentMapper.updateById(document));
    }

    /**
     * 建索引失败：保留既有段落，仅回写文档状态与失败原因。
     * 段落数与字符数沿用上一次成功建索引的结果，不清零，避免统计与实际内容脱节。
     */
    private void applyIndexFailure(AiDocumentPo document, String errorMessage) {
        document.setIndexStatus(DOC_STATUS_FAILED);
        document.setIndexError(StringUtils.hasText(errorMessage) ? errorMessage : "建索引失败");
        fillDocumentUpdateAudit(document);
        transactionTemplate.executeWithoutResult(status -> {
            aiDocumentMapper.updateById(document);
            refreshKnowledgeBaseStats(document.getKbId());
        });
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
        Path path = Paths.get(document.getFilePath());
        if (!Files.exists(path)) {
            return new IndexOutcome(List.of(), 0, true, "文档文件不存在，无法重建索引");
        }
        String docType = textExtractor.resolveDocumentType(document.getDocName());
        String content = textExtractor.extract(path, docType);
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

    private Path storeFile(Long kbId, String docName, MultipartFile file) {
        Path directory = Paths.get(documentStoragePath, String.valueOf(kbId));
        try {
            Files.createDirectories(directory);
            String suffix = "";
            int lastDot = docName.lastIndexOf('.');
            if (lastDot >= 0) {
                suffix = docName.substring(lastDot);
            }
            Path target = directory.resolve(UUID.randomUUID() + suffix);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException ex) {
            throw new BusinessException("保存文档失败: " + ex.getMessage());
        }
    }

    /**
     * 统计刷新：三个计数全部下推数据库聚合。
     * 原实现把整库段落（含 content 与 embedding 大字段）拉进内存做 count/sum，大知识库必 OOM。
     */
    private void refreshKnowledgeBaseStats(Long kbId) {
        AiKnowledgeBasePo knowledgeBase = requireKnowledgeBase(kbId);
        Long documentCount = aiDocumentMapper.selectCount(new LambdaQueryWrapper<AiDocumentPo>()
                .eq(AiDocumentPo::getKbId, kbId));
        Long paragraphCount = aiParagraphMapper.selectCount(new LambdaQueryWrapper<AiParagraphPo>()
                .eq(AiParagraphPo::getKbId, kbId)
                .eq(AiParagraphPo::getDelFlag, 0));
        knowledgeBase.setDocumentCount(documentCount != null ? documentCount.intValue() : 0);
        knowledgeBase.setParagraphCount(paragraphCount != null ? paragraphCount.intValue() : 0);
        knowledgeBase.setCharCount(sumParagraphCharCount(kbId));
        fillKnowledgeBaseUpdateAudit(knowledgeBase);
        aiKnowledgeBaseMapper.updateById(knowledgeBase);
    }

    private long sumParagraphCharCount(Long kbId) {
        QueryWrapper<AiParagraphPo> wrapper = new QueryWrapper<AiParagraphPo>()
                .select("COALESCE(SUM(CASE WHEN char_count > 0 THEN char_count ELSE 0 END), 0)")
                .eq("kb_id", kbId)
                .eq("del_flag", 0);
        List<Object> rows = aiParagraphMapper.selectObjs(wrapper);
        if (rows.isEmpty() || !(rows.get(0) instanceof Number total)) {
            return 0L;
        }
        return total.longValue();
    }

    private void deleteDocumentFile(String filePath) {
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
}
