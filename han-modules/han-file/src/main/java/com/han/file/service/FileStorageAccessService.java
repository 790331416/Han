package com.han.file.service;

import com.han.common.core.util.FileUploadUtils;
import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.config.StorageConfigRecord;
import com.han.starter.storage.config.StorageConfigRepository;
import com.han.starter.storage.config.StorageProperties;
import com.han.starter.storage.config.StorageRuntimeConfig;
import com.han.starter.storage.impl.RustFSStorageProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储访问服务。
 */
@Service
@RequiredArgsConstructor
public class FileStorageAccessService {

    private final StorageProperties storageProperties;
    private final ObjectProvider<StorageConfigRepository> storageConfigRepositoryProvider;
    private final Map<String, StorageProvider> providerCache = new ConcurrentHashMap<>();

    /**
     * 上传文件并生成公开访问地址。
     *
     * @param file    上传文件
     * @param request 当前请求
     * @return 上传结果
     * @throws IOException 上传失败
     */
    public FileAccessResult upload(MultipartFile file, HttpServletRequest request) throws IOException {
        StorageConfigRecord record = resolveActiveRecord();
        String name = FileUploadUtils.extractFilename(file);
        getProvider(record.getRuntimeConfig()).upload(name, file.getInputStream(), file.getContentType());
        String url = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/file/public/{locator}/{name}")
                .replaceQuery(null)
                .buildAndExpand(record.getLocator(), name)
                .toUriString();
        return new FileAccessResult(name, url);
    }

    /**
     * 按存储定位符下载文件。
     *
     * @param locator  存储定位符
     * @param fileName 文件名
     * @return 下载结果
     */
    public DownloadFileResult download(String locator, String fileName) {
        try {
            StorageConfigRecord record = resolveRecord(locator);
            InputStream stream = getProvider(record.getRuntimeConfig()).download(fileName);
            return new DownloadFileResult(fileName, FileUploadUtils.getContentType(fileName), stream);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage locator not found", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found", ex);
        }
    }

    private StorageConfigRecord resolveActiveRecord() {
        StorageConfigRepository repository = storageConfigRepositoryProvider.getIfAvailable();
        if (repository != null) {
            return repository.findActiveRecord().orElseGet(this::fallbackRecord);
        }
        return fallbackRecord();
    }

    private StorageConfigRecord resolveRecord(String locator) {
        StorageConfigRepository repository = storageConfigRepositoryProvider.getIfAvailable();
        if (repository != null) {
            return repository.findRecord(locator).orElseGet(() -> fallbackRecord(locator));
        }
        return fallbackRecord(locator);
    }

    private StorageConfigRecord fallbackRecord() {
        StorageRuntimeConfig runtimeConfig = StorageRuntimeConfig.fromProperties(storageProperties.getRustfs());
        return StorageConfigRecord.fromStatic(runtimeConfig.getConfigKey(), runtimeConfig);
    }

    private StorageConfigRecord fallbackRecord(String locator) {
        StorageConfigRecord record = fallbackRecord();
        if (!record.getLocator().equals(locator)) {
            throw new IllegalArgumentException("Storage locator not found: " + locator);
        }
        return record;
    }

    private StorageProvider getProvider(StorageRuntimeConfig runtimeConfig) {
        return providerCache.computeIfAbsent(runtimeConfig.signature(), key -> new RustFSStorageProvider(runtimeConfig));
    }

    /**
     * 上传结果。
     */
    public static final class FileAccessResult {
        private final String name;
        private final String url;

        public FileAccessResult(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * 下载结果。
     */
    public static final class DownloadFileResult {
        private final String name;
        private final String contentType;
        private final InputStream stream;

        public DownloadFileResult(String name, String contentType, InputStream stream) {
            this.name = name;
            this.contentType = contentType;
            this.stream = stream;
        }

        public String getName() {
            return name;
        }

        public MediaType getMediaType() {
            return MediaType.parseMediaType(contentType);
        }

        public InputStream getStream() {
            return stream;
        }
    }
}
