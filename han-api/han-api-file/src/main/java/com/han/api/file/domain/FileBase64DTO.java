package com.han.api.file.domain;

import java.io.Serializable;

/**
 * 文件 Base64 内容传输对象（服务间读取文件字节用，如多模态图片注入）。
 */
public class FileBase64DTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 归属租户ID（0 表示平台级/无租户）
     */
    private Long tenantId;

    /**
     * 文件名
     */
    private String name;

    /**
     * MIME 类型（如 image/png）
     */
    private String mimeType;

    /**
     * 公开访问地址
     */
    private String url;

    /**
     * Base64 编码内容（不含 data: 前缀）
     */
    private String base64;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }
}
