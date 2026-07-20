package com.han.ai.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库文档抽取组件测试（G1-7）：样例 pdf/docx/xlsx 在测试运行时用 PDFBox/POI
 * 生成（避免向仓库提交二进制样例），验证「解析文本非空 + 分段非空」，
 * 即 pdf/docx 上传后不再「收了不认」。
 */
class AiDocumentTextExtractorTest {

    private static final String PDF_SAMPLE_TEXT = "Han knowledge base PDF sample paragraph.";
    private static final String DOCX_SAMPLE_TEXT_1 = "Han 知识库 docx 样例第一段。";
    private static final String DOCX_SAMPLE_TEXT_2 = "Second paragraph for docx sample.";

    private final AiDocumentTextExtractor extractor = new AiDocumentTextExtractor();

    // ---------- 类型识别 ----------

    @Test
    void resolvesDocumentTypesBySuffix() {
        assertThat(extractor.resolveDocumentType("报告.PDF")).isEqualTo("pdf");
        assertThat(extractor.resolveDocumentType("说明.docx")).isEqualTo("docx");
        assertThat(extractor.resolveDocumentType("台账.xlsx")).isEqualTo("xlsx");
        assertThat(extractor.resolveDocumentType("清单.csv")).isEqualTo("csv");
        assertThat(extractor.resolveDocumentType("readme.md")).isEqualTo("md");
        assertThat(extractor.resolveDocumentType("page.HTM")).isEqualTo("html");
        assertThat(extractor.resolveDocumentType("notes.txt")).isEqualTo("txt");
        assertThat(extractor.resolveDocumentType("unknown.bin")).isEqualTo("txt");
    }

    // ---------- pdf ----------

    @Test
    void extractsPdfTextAndSplitsNonEmptyParagraphs(@TempDir Path tempDir) throws IOException {
        Path pdfPath = tempDir.resolve("sample.pdf");
        createSamplePdf(pdfPath);

        String text = extractor.extract(pdfPath, "pdf");

        assertThat(text).as("pdf 解析文本非空").isNotBlank();
        assertThat(text).contains("PDF sample paragraph");
        List<String> paragraphs = AiKnowledgeBaseServiceImpl.splitParagraphs(text.trim());
        assertThat(paragraphs).as("pdf 分段非空").isNotEmpty();
        assertThat(paragraphs.get(0)).contains("Han knowledge base");
    }

    // ---------- docx ----------

    @Test
    void extractsDocxTextAndSplitsNonEmptyParagraphs(@TempDir Path tempDir) throws IOException {
        Path docxPath = tempDir.resolve("sample.docx");
        createSampleDocx(docxPath);

        String text = extractor.extract(docxPath, "docx");

        assertThat(text).as("docx 解析文本非空").isNotBlank();
        assertThat(text).contains("知识库 docx 样例").contains("Second paragraph");
        List<String> paragraphs = AiKnowledgeBaseServiceImpl.splitParagraphs(text.trim());
        assertThat(paragraphs).as("docx 分段非空").isNotEmpty();
    }

    // ---------- xlsx ----------

    @Test
    void extractsXlsxCellsWithSheetHeaderAndSplitsNonEmpty(@TempDir Path tempDir) throws IOException {
        Path xlsxPath = tempDir.resolve("sample.xlsx");
        createSampleXlsx(xlsxPath);

        String text = extractor.extract(xlsxPath, "xlsx");

        assertThat(text).as("xlsx 解析文本非空").isNotBlank();
        assertThat(text).contains("[台账]").contains("角色").contains("镜头时长");
        // 全空行被跳过
        assertThat(text.lines().filter(String::isBlank).count()).isLessThanOrEqualTo(1);
        assertThat(AiKnowledgeBaseServiceImpl.splitParagraphs(text.trim())).isNotEmpty();
    }

    // ---------- 文本类直读 ----------

    @Test
    void readsPlainTextFamiliesDirectly(@TempDir Path tempDir) throws IOException {
        Path csvPath = tempDir.resolve("sample.csv");
        Files.writeString(csvPath, "场景,时长\n开场,5s\n", StandardCharsets.UTF_8);
        assertThat(extractor.extract(csvPath, "csv")).contains("开场,5s");

        Path htmlPath = tempDir.resolve("sample.html");
        Files.writeString(htmlPath, "<html><body><p>网页正文</p></body></html>", StandardCharsets.UTF_8);
        String html = extractor.extract(htmlPath, "html");
        assertThat(html).contains("网页正文").doesNotContain("<p>");
    }

    // ---------- 损坏文档走可诊断失败 ----------

    @Test
    void wrapsCorruptedDocumentErrorsAsIOException(@TempDir Path tempDir) throws IOException {
        Path fakeDocx = tempDir.resolve("broken.docx");
        Files.writeString(fakeDocx, "这不是一个合法的 docx 文件", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> extractor.extract(fakeDocx, "docx"))
                .isInstanceOf(IOException.class);

        Path fakePdf = tempDir.resolve("broken.pdf");
        Files.writeString(fakePdf, "not a pdf", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> extractor.extract(fakePdf, "pdf"))
                .isInstanceOf(IOException.class);
    }

    // ---------- 样例文件生成（运行时构造，不入库） ----------

    private void createSamplePdf(Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(PDF_SAMPLE_TEXT);
                content.endText();
            }
            document.save(path.toFile());
        }
    }

    private void createSampleDocx(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(DOCX_SAMPLE_TEXT_1);
            document.createParagraph().createRun().setText(DOCX_SAMPLE_TEXT_2);
            try (OutputStream out = Files.newOutputStream(path)) {
                document.write(out);
            }
        }
    }

    private void createSampleXlsx(Path path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("台账");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("角色");
            header.createCell(1).setCellValue("镜头时长");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("林小满");
            row.createCell(1).setCellValue("5s");
            // 第 2 行留空，验证全空行跳过
            sheet.createRow(2);
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }
}
