package com.han.file.service;

import com.han.common.core.util.FileUploadUtils;
import com.han.common.core.util.HanIdUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.config.StorageConfigRecord;
import com.han.starter.storage.config.StorageConfigRepository;
import com.han.starter.storage.config.StorageProperties;
import com.han.starter.storage.config.StorageRuntimeConfig;
import com.han.starter.storage.impl.RustFSStorageProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储访问服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageAccessService {

    private final StorageProperties storageProperties;
    private final ObjectProvider<StorageConfigRepository> storageConfigRepositoryProvider;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorageProvider> providerCache = new ConcurrentHashMap<>();

    /**
     * 上传文件并生成公开访问地址。
     *
     * @param file    上传文件
     * @param request 当前请求
     * @return 上传结果
     * @throws IOException 上传失败
     */
    @Transactional(rollbackFor = Exception.class)
    public FileAccessResult upload(MultipartFile file, HttpServletRequest request) throws IOException {
        StorageConfigRecord record = resolveActiveRecord();
        String name = FileUploadUtils.extractFilename(file);
        getProvider(record.getRuntimeConfig()).upload(name, file.getInputStream(), file.getContentType());
        String url = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/file/public/{locator}/{name}")
                .replaceQuery(null)
                .buildAndExpand(record.getLocator(), name)
                .toUriString();
        Long fileId = insertFileRecord(file, record, name, url);
        return new FileAccessResult(fileId, name, url);
    }

    /**
     * 按文件ID读取文件字节并编码为 Base64（服务间调用，多模态图片注入等场景）。
     *
     * @param fileId 文件ID
     * @return Base64 结果
     */
    public FileBase64Result loadBase64(Long fileId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("""
                            select tenant_id, file_name, file_path, file_url, mime_type, bucket
                            from sys_file
                            where id = ? and del_flag = 0
                            """, fileId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File record not found", ex);
        }
        String locator = (String) row.get("bucket");
        String filePath = (String) row.get("file_path");
        DownloadFileResult download = download(locator, filePath);
        try (InputStream stream = download.getStream()) {
            byte[] bytes = stream.readAllBytes();
            String mimeType = (String) row.get("mime_type");
            Number tenantId = (Number) row.get("tenant_id");
            return new FileBase64Result(
                    fileId,
                    tenantId == null ? 0L : tenantId.longValue(),
                    (String) row.get("file_name"),
                    mimeType == null || mimeType.isBlank() ? FileUploadUtils.getContentType(filePath) : mimeType,
                    (String) row.get("file_url"),
                    java.util.Base64.getEncoder().encodeToString(bytes));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File read failed", ex);
        }
    }

    /**
     * 文件管理分页查询（E-filemanage）：按文件名/类型/时间过滤，非管理员限定本租户。
     */
    public PageQueryResult page(String fileName, String fileType, String beginTime, String endTime,
                                int pageNum, int pageSize, Long tenantId, boolean admin) {
        StringBuilder where = new StringBuilder(" where del_flag = 0");
        List<Object> args = new ArrayList<>();
        if (fileName != null && !fileName.isBlank()) {
            where.append(" and file_name like ?");
            args.add("%" + fileName.trim() + "%");
        }
        if (fileType != null && !fileType.isBlank()) {
            where.append(" and file_type = ?");
            args.add(fileType.trim());
        }
        if (beginTime != null && !beginTime.isBlank()) {
            where.append(" and create_time >= ?::timestamp");
            args.add(beginTime.trim());
        }
        if (endTime != null && !endTime.isBlank()) {
            where.append(" and create_time <= ?::timestamp");
            args.add(endTime.trim());
        }
        if (!admin) {
            if (tenantId != null && tenantId > 0) {
                where.append(" and tenant_id = ?");
                args.add(tenantId);
            } else {
                where.append(" and 1 = 0");
            }
        }
        Long total = jdbcTemplate.queryForObject("select count(*) from sys_file" + where, Long.class, args.toArray());
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(safePageSize);
        pagedArgs.add((long) (safePageNum - 1) * safePageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        select id, tenant_id as "tenantId", file_name as "fileName", file_url as "fileUrl",
                               file_size as "fileSize", file_type as "fileType", mime_type as "mimeType",
                               storage_type as "storageType", bucket, create_by as "createBy", create_time as "createTime"
                        from sys_file
                        """ + where + " order by create_time desc, id desc limit ? offset ?",
                pagedArgs.toArray());
        return new PageQueryResult(rows, total == null ? 0L : total);
    }

    /**
     * 批量删除：软删 sys_file，尽力物理删除对象存储；物理删除失败仅告警不回滚业务
     * （残留对象可由后续对账任务兜底清理）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int removeByIds(List<Long> ids, Long tenantId, boolean admin) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            Map<String, Object> row;
            try {
                row = jdbcTemplate.queryForMap(
                        "select tenant_id, file_path, bucket from sys_file where id = ? and del_flag = 0", id);
            } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
                continue;
            }
            Number rowTenantId = (Number) row.get("tenant_id");
            if (!admin) {
                long ownerTenantId = rowTenantId == null ? 0L : rowTenantId.longValue();
                if (tenantId == null || tenantId <= 0 || ownerTenantId != tenantId) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to delete file " + id);
                }
            }
            removed += jdbcTemplate.update("update sys_file set del_flag = 1 where id = ?", id);
            try {
                StorageConfigRecord record = resolveRecord((String) row.get("bucket"));
                getProvider(record.getRuntimeConfig()).delete((String) row.get("file_path"));
            } catch (RuntimeException ex) {
                log.warn("Physical file delete failed, id={}, path={}", id, row.get("file_path"), ex);
            }
        }
        return removed;
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
            return new DownloadFileResult(fileName, FileUploadUtils.getContentType(fileName), resolveFileSize(locator, fileName), stream);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage locator not found", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found", ex);
        }
    }

    private Long resolveFileSize(String locator, String fileName) {
        return jdbcTemplate.query("""
                        select file_size
                        from sys_file
                        where bucket = ? and file_path = ? and del_flag = 0
                        order by create_time desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getLong("file_size") : null,
                locator,
                fileName);
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

    private Long insertFileRecord(MultipartFile file, StorageConfigRecord record, String name, String url) {
        Long fileId = HanIdUtil.snowflakeId();
        String contentType = file.getContentType();
        String extension = "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < name.length() - 1) {
            extension = name.substring(dotIndex + 1).toLowerCase();
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        Long userId = SecurityContextHolder.getUserId();
        jdbcTemplate.update("""
                        insert into sys_file (
                          id, tenant_id, file_name, file_path, file_url, file_size, file_type,
                          mime_type, storage_type, bucket, md5, create_by, create_time, del_flag
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                fileId,
                tenantId == null ? 0L : tenantId,
                name,
                name,
                url,
                file.getSize(),
                extension,
                contentType == null ? "" : contentType,
                "rustfs",
                record.getLocator(),
                "",
                userId,
                LocalDateTime.now(),
                0);
        return fileId;
    }

    /**
     * 上传结果。
     */
    public static final class FileAccessResult {
        private final Long id;
        private final String name;
        private final String url;

        public FileAccessResult(Long id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * Base64 读取结果。
     */
    public record FileBase64Result(Long id, Long tenantId, String name, String mimeType, String url, String base64) {
    }

    /**
     * 文件管理分页结果。
     */
    public record PageQueryResult(List<Map<String, Object>> rows, long total) {
    }

    /**
     * 下载结果。
     */
    public static final class DownloadFileResult {
        private final String name;
        private final String contentType;
        private final Long contentLength;
        private final InputStream stream;

        public DownloadFileResult(String name, String contentType, Long contentLength, InputStream stream) {
            this.name = name;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.stream = stream;
        }

        public String getName() {
            return name;
        }

        public MediaType getMediaType() {
            return MediaType.parseMediaType(contentType);
        }

        public Long getContentLength() {
            return contentLength;
        }

        public InputStream getStream() {
            return stream;
        }
    }
}
