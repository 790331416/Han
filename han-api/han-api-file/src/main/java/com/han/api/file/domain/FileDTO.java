package com.han.api.file.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件上传结果。
 *
 * <p>注意：本类不只是服务间契约，{@code POST /file/upload} 会把它直接返回给浏览器，
 * 字段增删按对外接口对待。
 */
@Data
public class FileDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件名
     */
    private String name;

    private Long id;

    /**
     * 文件URL
     */
    private String url;

    public FileDTO() {
    }

    public FileDTO(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public FileDTO(Long id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
    }
}
