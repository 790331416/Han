package com.xuman.api.file.domain;

import java.io.Serializable;

public class FileDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 文件名
     */
    private String name;

    /**
     * 文件URL
     */
    private String url;

    public FileDTO() {}

    public FileDTO(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
