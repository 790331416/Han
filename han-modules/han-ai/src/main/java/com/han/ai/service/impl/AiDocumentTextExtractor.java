package com.han.ai.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 知识库文档文本抽取组件（G1-7）：按文档类型抽取纯文本，供索引链路分段/向量化。
 * <p>
 * 支持类型：txt / md / csv 直读；html 剥标签；pdf 走 PDFBox；docx 走 POI XWPF；
 * xlsx 走 POI XSSF（按 sheet-行-单元格线性化，单元格以制表符分隔）。
 * 未识别后缀按纯文本 UTF-8 兜底读取（与历史行为一致）。
 * <p>
 * 解析器抛出的运行时异常统一包装为 {@link IOException}，交由索引链路按
 * 「文档读取失败」置 failed 状态并记录原因，不中断上传事务之外的其他文档。
 */
@Slf4j
@Component
class AiDocumentTextExtractor {

    /**
     * 按文件名后缀识别文档类型；未识别后缀按 txt 兜底（与历史行为一致）。
     */
    String resolveDocumentType(String docName) {
        String lowerName = docName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            return "pdf";
        }
        if (lowerName.endsWith(".docx")) {
            return "docx";
        }
        if (lowerName.endsWith(".xlsx")) {
            return "xlsx";
        }
        if (lowerName.endsWith(".csv")) {
            return "csv";
        }
        if (lowerName.endsWith(".md")) {
            return "md";
        }
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return "html";
        }
        return "txt";
    }

    /**
     * 抽取文档纯文本；无可抽取内容时返回空串（由调用方决定失败文案）。
     *
     * @throws IOException 文件读取失败或文档格式损坏/加密导致解析失败
     */
    String extract(Path path, String docType) throws IOException {
        return switch (docType) {
            case "txt", "md", "csv" -> Files.readString(path, StandardCharsets.UTF_8);
            case "html" -> Files.readString(path, StandardCharsets.UTF_8).replaceAll("<[^>]+>", " ");
            case "pdf" -> extractPdf(path);
            case "docx" -> extractDocx(path);
            case "xlsx" -> extractXlsx(path);
            default -> Files.readString(path, StandardCharsets.UTF_8);
        };
    }

    private String extractPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return text != null ? text : "";
        } catch (RuntimeException ex) {
            log.warn("PDF text extraction failed, path={}", path, ex);
            throw new IOException("pdf 解析失败: " + ex.getMessage(), ex);
        }
    }

    private String extractDocx(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            return text != null ? text : "";
        } catch (RuntimeException ex) {
            log.warn("DOCX text extraction failed, path={}", path, ex);
            throw new IOException("docx 解析失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * xlsx 线性化：sheet 名独立成行，数据行内单元格以制表符连接、跳过全空行。
     */
    private String extractXlsx(Path path) throws IOException {
        DataFormatter formatter = new DataFormatter();
        StringBuilder text = new StringBuilder();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            for (Sheet sheet : workbook) {
                StringBuilder sheetText = new StringBuilder();
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (StringUtils.hasText(value)) {
                            if (!line.isEmpty()) {
                                line.append('\t');
                            }
                            line.append(value.trim());
                        }
                    }
                    if (!line.isEmpty()) {
                        sheetText.append(line).append('\n');
                    }
                }
                if (!sheetText.isEmpty()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append('[').append(sheet.getSheetName()).append("]\n").append(sheetText);
                }
            }
            return text.toString();
        } catch (RuntimeException ex) {
            log.warn("XLSX text extraction failed, path={}", path, ex);
            throw new IOException("xlsx 解析失败: " + ex.getMessage(), ex);
        }
    }
}
