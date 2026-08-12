package com.han.starter.storage;

import java.io.InputStream;

/**
 * 存储提供者接口（可插拔）
 */
public interface StorageProvider {

    /**
     * 上传文件
     *
     * @param path   存储路径
     * @param stream 文件流
     * @return 访问URL
     */
    String upload(String path, InputStream stream);

    /**
     * 上传文件
     *
     * @param path        存储路径
     * @param stream      文件流
     * @param contentType 内容类型
     * @return 访问URL
     */
    String upload(String path, InputStream stream, String contentType);

    /**
     * 上传文件（显式声明内容长度）。
     *
     * <p>S3 协议要求写入前给出准确的 Content-Length；调用方能拿到确切长度时必须走本方法，
     * 传 {@code null} 或非正数表示长度未知，由实现改用分段上传，禁止用
     * {@code InputStream#available()} 猜长度（会静默截断）。
     *
     * @param path          存储路径
     * @param stream        文件流
     * @param contentType   内容类型
     * @param contentLength 内容长度（字节），未知传 null
     * @return 访问URL
     */
    default String upload(String path, InputStream stream, String contentType, Long contentLength) {
        return upload(path, stream, contentType);
    }

    /**
     * 下载文件
     *
     * @param path 存储路径
     * @return 文件流
     */
    InputStream download(String path);

    /**
     * 删除文件
     *
     * @param path 存储路径
     */
    void delete(String path);

    /**
     * 获取文件访问URL
     *
     * @param path 存储路径
     * @return 访问URL
     */
    String getUrl(String path);

    /**
     * 判断文件是否存在
     *
     * @param path 存储路径
     * @return 是否存在
     */
    boolean exists(String path);
}
