package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.han.ai.domain.po.AiDocumentPo;
import com.han.ai.domain.po.AiKnowledgeBasePo;
import com.han.ai.domain.po.AiParagraphPo;
import com.han.ai.mapper.AiDocumentMapper;
import com.han.ai.mapper.AiKnowledgeBaseMapper;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.mapper.AiParagraphMapper;
import com.han.ai.service.IAiKnowledgeRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识库建索引单测：重点覆盖「重建索引失败不得清空既有段落」这条数据安全约束。
 */
class AiKnowledgeBaseServiceImplTest {

    private AiKnowledgeBaseMapper knowledgeBaseMapper;
    private AiDocumentMapper documentMapper;
    private AiParagraphMapper paragraphMapper;
    private AiDocumentTextExtractor textExtractor;
    private AiKnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() {
        knowledgeBaseMapper = mock(AiKnowledgeBaseMapper.class);
        documentMapper = mock(AiDocumentMapper.class);
        paragraphMapper = mock(AiParagraphMapper.class);
        textExtractor = mock(AiDocumentTextExtractor.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new AiKnowledgeBaseServiceImpl(
                knowledgeBaseMapper,
                documentMapper,
                paragraphMapper,
                mock(AiModelMapper.class),
                mock(AiModelCredentialResolver.class),
                mock(AiEmbeddingClient.class),
                mock(IAiKnowledgeRetrievalService.class),
                textExtractor,
                new TransactionTemplate(transactionManager));
    }

    private void givenDocument(Long docId, String filePath) {
        AiKnowledgeBasePo knowledgeBase = new AiKnowledgeBasePo();
        knowledgeBase.setKbId(11L);
        knowledgeBase.setKbName("回归知识库");
        knowledgeBase.setStatus("0");
        when(knowledgeBaseMapper.selectById(11L)).thenReturn(knowledgeBase);

        AiDocumentPo document = new AiDocumentPo();
        document.setDocId(docId);
        document.setKbId(11L);
        document.setDocName("handbook.md");
        document.setFilePath(filePath);
        document.setParagraphCount(42);
        document.setCharCount(9000L);
        when(documentMapper.selectById(docId)).thenReturn(document);
    }

    @Test
    void reindexKeepsExistingParagraphsWhenSourceFileMissing() {
        givenDocument(21L, "D:/not-exists/handbook.md");

        service.reindexDocument(21L);

        Mockito.verify(paragraphMapper, Mockito.never()).delete(anyParagraphWrapper());
        Mockito.verify(paragraphMapper, Mockito.never()).insert(any(AiParagraphPo.class));

        ArgumentCaptor<AiDocumentPo> captor = ArgumentCaptor.forClass(AiDocumentPo.class);
        Mockito.verify(documentMapper, Mockito.atLeastOnce()).updateById(captor.capture());
        AiDocumentPo saved = captor.getValue();
        assertThat(saved.getIndexStatus()).isEqualTo("failed");
        assertThat(saved.getIndexError()).contains("文档文件不存在");
        // 失败分支不得把段落数/字符数清零，否则统计与实际留存内容脱节
        assertThat(saved.getParagraphCount()).isEqualTo(42);
        assertThat(saved.getCharCount()).isEqualTo(9000L);
    }

    @Test
    void reindexKeepsExistingParagraphsWhenExtractionYieldsNoText() throws Exception {
        Path emptyFile = Files.createTempFile("han-ai-reindex", ".md");
        try {
            givenDocument(22L, emptyFile.toString());
            when(textExtractor.resolveDocumentType("handbook.md")).thenReturn("md");
            when(textExtractor.extract(any(Path.class), Mockito.eq("md"))).thenReturn("   ");

            service.reindexDocument(22L);

            Mockito.verify(paragraphMapper, Mockito.never()).delete(anyParagraphWrapper());
            Mockito.verify(paragraphMapper, Mockito.never()).insert(any(AiParagraphPo.class));
        } finally {
            Files.deleteIfExists(emptyFile);
        }
    }

    @Test
    void reindexReplacesParagraphsWhenExtractionSucceeds() throws Exception {
        Path file = Files.createTempFile("han-ai-reindex", ".md");
        try {
            givenDocument(23L, file.toString());
            when(textExtractor.resolveDocumentType("handbook.md")).thenReturn("md");
            when(textExtractor.extract(any(Path.class), Mockito.eq("md")))
                    .thenReturn("第一段内容\n\n第二段内容");

            service.reindexDocument(23L);

            Mockito.verify(paragraphMapper).delete(anyParagraphWrapper());
            Mockito.verify(paragraphMapper, Mockito.times(2)).insert(any(AiParagraphPo.class));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<AiParagraphPo> anyParagraphWrapper() {
        return any(Wrapper.class);
    }
}
