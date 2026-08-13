package com.han.api.file.domain;

import java.io.Serializable;

/**
 * 文件元信息传输对象（服务间调用：先取元信息再决定按 Base64 还是流式下载取内容）。
 */
public class FileInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 归属租户ID（0 表示平台级/无租户）
     */
    private Long tenantId;

    /**
     * 原始文件名
     */
    private String name;

    /**
     * 文件字节数
     */
    private Long size;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 公开访问地址
     */
    private String url;

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

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
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
}
