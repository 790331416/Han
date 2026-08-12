package com.han.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件服务配置。
 *
 * <p>上传白名单、体积上限等安全参数一律走配置，不在业务代码里硬编码，
 * 便于按环境（三档部署）分别收紧或放宽。
 */
@ConfigurationProperties(prefix = "han.file")
public class FileProperties {

    /**
     * 对外公开访问地址前缀（如 https://han.example.com）。
     *
     * <p>配置后所有 file_url 都按此前缀生成；留空时退回按请求推导，
     * 服务间调用（无 X-Forwarded-* 头）推导出的会是容器内网地址，浏览器访问不到。
     */
    private String publicBaseUrl = "";

    /**
     * 内部 Base64 读取接口的单文件上限；超过该值拒绝并要求改用下载地址，避免整文件入堆。
     */
    private DataSize base64MaxSize = DataSize.ofMegabytes(10);

    private final Upload upload = new Upload();

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public DataSize getBase64MaxSize() {
        return base64MaxSize;
    }

    public void setBase64MaxSize(DataSize base64MaxSize) {
        this.base64MaxSize = base64MaxSize;
    }

    public Upload getUpload() {
        return upload;
    }

    /**
     * 上传校验配置。
     */
    public static class Upload {

        /**
         * 允许上传的扩展名白名单（小写，不含点）。
         *
         * <p>默认覆盖 {@code FileUploadUtils#getContentType} 已登记的类型：
         * 图片、办公文档、平台在用的音视频，以及纯文本类。
         * 可执行文件、脚本、HTML/SVG 等可在浏览器里执行的类型一律不在白名单内。
         */
        private List<String> allowedExtensions = new ArrayList<>(List.of(
                "jpg", "jpeg", "png", "gif", "webp", "bmp",
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "txt", "csv", "md", "json", "zip",
                "mp4", "m4v", "mov", "webm", "mp3", "wav"
        ));

        /**
         * 默认单文件上限。
         */
        private DataSize maxSize = DataSize.ofMegabytes(50);

        /**
         * 按扩展名覆盖的单文件上限（音视频需要更大额度）。
         */
        private Map<String, DataSize> maxSizePerExtension = new LinkedHashMap<>(Map.of(
                "mp4", DataSize.ofMegabytes(300),
                "m4v", DataSize.ofMegabytes(300),
                "mov", DataSize.ofMegabytes(300),
                "webm", DataSize.ofMegabytes(300),
                "mp3", DataSize.ofMegabytes(100),
                "wav", DataSize.ofMegabytes(100)
        ));

        /**
         * 是否按文件真实内容（魔数）校验类型；关闭后只剩扩展名校验，仅供排障临时使用。
         */
        private boolean verifyContent = true;

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public DataSize getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(DataSize maxSize) {
            this.maxSize = maxSize;
        }

        public Map<String, DataSize> getMaxSizePerExtension() {
            return maxSizePerExtension;
        }

        public void setMaxSizePerExtension(Map<String, DataSize> maxSizePerExtension) {
            this.maxSizePerExtension = maxSizePerExtension;
        }

        public boolean isVerifyContent() {
            return verifyContent;
        }

        public void setVerifyContent(boolean verifyContent) {
            this.verifyContent = verifyContent;
        }
    }
}
