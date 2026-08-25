package com.han.starter.storage;

import java.io.InputStream;
import java.time.Duration;

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
     * 上传已知长度的文件流。默认实现保持旧适配器兼容。
     */
    default String upload(String path, InputStream stream, String contentType, long contentLength) {
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

    /** 生成受限时长的下载地址；不支持的实现必须明确拒绝。 */
    default String createTemporaryUrl(String path, Duration duration) {
        throw new UnsupportedOperationException("当前对象存储不支持临时下载地址");
    }
}
