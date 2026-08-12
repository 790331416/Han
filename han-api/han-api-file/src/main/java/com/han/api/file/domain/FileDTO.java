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
     * 文件URL（浏览器可直接访问的公开地址）。
     *
     * <p><b>服务间调用时这个值目前是错的</b>：han-file 用
     * {@code ServletUriComponentsBuilder.fromRequestUri(request)} 从「当前请求的 URI」推导公开
     * 地址。走网关时有 {@code forward-headers-strategy: framework} 兜底还算正确；但
     * {@code @HttpExchange} 调用是底座直接拼 {@code instance.getUri()}（{@code http://<容器IP>:9207}）
     * 且不带任何 {@code X-Forwarded-*} 头，于是 han-ai 上传生成图后落库的 {@code sys_file.file_url}
     * 是浏览器根本访问不到的容器内网地址。
     * 正确形态是由 han-file 引入 {@code han.file.public-base-url} 配置项统一拼接，
     * {@code ServletUriComponentsBuilder} 只在该配置缺省时兜底。
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
