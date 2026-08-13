package com.han.file.security;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.FileUploadUtils;
import com.han.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 上传校验：扩展名白名单 + 体积上限 + 文件真实内容校验。
 *
 * <p>三条约束缺一不可：扩展名可以随便改，Content-Type 由客户端自己填，只有文件头字节骗不了人。
 * 对外返回的 MIME 一律由白名单扩展名推导，绝不回显客户端声明的 Content-Type。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadValidator {

    /**
     * 扩展名 -> 允许的内容家族。值为空集表示该类型没有稳定魔数（纯文本类），只要不是危险内容即可。
     */
    private static final Map<String, Set<String>> EXPECTED_FAMILIES = Map.ofEntries(
            Map.entry("jpg", Set.of("jpeg")),
            Map.entry("jpeg", Set.of("jpeg")),
            Map.entry("png", Set.of("png")),
            Map.entry("gif", Set.of("gif")),
            Map.entry("webp", Set.of("webp")),
            Map.entry("bmp", Set.of("bmp")),
            Map.entry("pdf", Set.of("pdf")),
            Map.entry("doc", Set.of("ole2", "zip")),
            Map.entry("xls", Set.of("ole2", "zip")),
            Map.entry("ppt", Set.of("ole2", "zip")),
            Map.entry("docx", Set.of("zip")),
            Map.entry("xlsx", Set.of("zip")),
            Map.entry("pptx", Set.of("zip")),
            Map.entry("zip", Set.of("zip")),
            Map.entry("mp4", Set.of("isobmff")),
            Map.entry("m4v", Set.of("isobmff")),
            Map.entry("mov", Set.of("isobmff")),
            Map.entry("webm", Set.of("matroska")),
            Map.entry("mp3", Set.of("mp3")),
            Map.entry("wav", Set.of("wav")),
            Map.entry("txt", Set.of()),
            Map.entry("csv", Set.of()),
            Map.entry("md", Set.of()),
            Map.entry("json", Set.of())
    );

    /**
     * {@link FileUploadUtils#getContentType} 未覆盖但平台在用的类型补充表。
     */
    private static final Map<String, String> EXTRA_CONTENT_TYPES = Map.of(
            "webp", "image/webp",
            "bmp", "image/bmp",
            "txt", "text/plain",
            "csv", "text/csv",
            "md", "text/markdown",
            "json", "application/json",
            "zip", "application/zip",
            "ppt", "application/vnd.ms-powerpoint",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    /**
     * sys_file.file_name 列宽，落库前按此截断。
     */
    private static final int MAX_NAME_LENGTH = 200;

    private final FileProperties fileProperties;

    /**
     * 校验上传元信息（文件名、扩展名白名单、体积上限）。
     *
     * @param originalFilename 客户端提交的原始文件名
     * @param size             文件字节数
     * @return 校验结果
     */
    public ValidatedUpload validateMetadata(String originalFilename, long size) {
        if (size <= 0) {
            throw new BusinessException("上传文件不能为空");
        }
        String safeName = sanitizeFilename(originalFilename);
        if (safeName.isEmpty()) {
            throw new BusinessException("上传文件名不能为空");
        }
        String extension = extractExtension(safeName);
        if (extension.isEmpty()) {
            throw new BusinessException("上传文件缺少扩展名，无法识别类型");
        }
        if (!isAllowedExtension(extension)) {
            throw new BusinessException("不支持的文件类型: " + extension);
        }
        long maxBytes = resolveMaxBytes(extension);
        if (size > maxBytes) {
            throw new BusinessException("文件大小超过限制: " + formatMegabytes(maxBytes));
        }
        return new ValidatedUpload(safeName, extension, resolveContentType(safeName, extension), size);
    }

    /**
     * 按文件头字节校验真实内容与扩展名是否一致。
     *
     * @param validated 元信息校验结果
     * @param header    文件头字节（至少 {@link FileSignatureDetector#PROBE_BYTES} 字节，不足则按实际长度）
     */
    public void verifyContent(ValidatedUpload validated, byte[] header) {
        if (!fileProperties.getUpload().isVerifyContent()) {
            return;
        }
        String family = FileSignatureDetector.detect(header);
        if (FileSignatureDetector.isDangerous(family)) {
            log.warn("Rejected upload with executable/markup content, name={}, family={}", validated.originalName(), family);
            throw new BusinessException("文件内容与声明类型不符，检测到可执行或可脚本化内容");
        }
        Set<String> expected = EXPECTED_FAMILIES.get(validated.extension());
        if (expected == null || expected.isEmpty()) {
            return;
        }
        if (family == null || !expected.contains(family)) {
            log.warn("Rejected upload with mismatched content, name={}, extension={}, family={}",
                    validated.originalName(), validated.extension(), family);
            throw new BusinessException("文件内容与扩展名不符: " + validated.extension());
        }
    }

    /**
     * 按扩展名推导对外 MIME；白名单之外的扩展名统一按二进制流处理。
     *
     * @param fileName  文件名
     * @param extension 扩展名（小写）
     * @return MIME 类型
     */
    public String resolveContentType(String fileName, String extension) {
        String contentType = FileUploadUtils.getContentType(fileName);
        if (!"application/octet-stream".equals(contentType)) {
            return contentType;
        }
        return EXTRA_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    /**
     * 文件名清洗：去掉路径部分与控制字符，并按列宽截断（保留扩展名）。
     *
     * @param originalFilename 原始文件名
     * @return 可安全落库与回显的文件名
     */
    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch >= 0x20 && ch != 0x7f) {
                builder.append(ch);
            }
        }
        name = builder.toString().trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "";
        }
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        String extension = extractExtension(name);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        int keep = Math.max(MAX_NAME_LENGTH - suffix.length(), 1);
        return name.substring(0, keep) + suffix;
    }

    /**
     * 提取小写扩展名（不含点）。
     *
     * @param fileName 文件名
     * @return 扩展名，没有则返回空串
     */
    public static String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedExtension(String extension) {
        return fileProperties.getUpload().getAllowedExtensions().stream()
                .filter(item -> item != null && !item.isBlank())
                .anyMatch(item -> item.trim().toLowerCase(Locale.ROOT).equals(extension));
    }

    private long resolveMaxBytes(String extension) {
        DataSize override = fileProperties.getUpload().getMaxSizePerExtension() == null
                ? null
                : fileProperties.getUpload().getMaxSizePerExtension().get(extension);
        DataSize limit = override != null ? override : fileProperties.getUpload().getMaxSize();
        return limit == null ? Long.MAX_VALUE : limit.toBytes();
    }

    private static String formatMegabytes(long bytes) {
        long megabytes = bytes / (1024 * 1024);
        return megabytes > 0 ? megabytes + "MB" : bytes + "B";
    }

    /**
     * 上传元信息校验结果。
     *
     * @param originalName 清洗后的原始文件名（用于落库与下载时的可读名称）
     * @param extension    小写扩展名
     * @param contentType  由扩展名推导的 MIME
     * @param size         文件字节数
     */
    public record ValidatedUpload(String originalName, String extension, String contentType, long size) {
    }
}
