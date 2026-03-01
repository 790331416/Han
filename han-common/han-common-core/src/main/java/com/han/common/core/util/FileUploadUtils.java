package com.han.common.core.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传工具类
 */
public final class FileUploadUtils {

    private FileUploadUtils() {}

    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx"};

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public static String upload(MultipartFile file, String uploadPath) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IOException("文件名不能为空");
        }

        String extension = getFileExtension(originalFilename);
        if (!isAllowedExtension(extension)) {
            throw new IOException("不支持的文件类型: " + extension);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IOException("文件大小超过限制: 10MB");
        }

        String newFilename = generateFilename(originalFilename);
        Path path = Paths.get(uploadPath, newFilename);
        Files.createDirectories(path.getParent());
        file.transferTo(path.toFile());

        return newFilename;
    }

    private static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    private static boolean isAllowedExtension(String extension) {
        return Arrays.asList(ALLOWED_EXTENSIONS).contains(extension);
    }

    private static String generateFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        return UUID.randomUUID().toString() + "." + extension;
    }

    /**
     * 提取文件名（生成唯一文件名）
     */
    public static String extractFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? getFileExtension(originalFilename) : "";
        return UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);
    }

    public static boolean isImage(String filename) {
        String extension = getFileExtension(filename);
        List<String> imageExtensions = Arrays.asList("jpg", "jpeg", "png", "gif");
        return imageExtensions.contains(extension);
    }

    public static String getContentType(String filename) {
        String extension = getFileExtension(filename);
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                return "application/octet-stream";
        }
    }
}
