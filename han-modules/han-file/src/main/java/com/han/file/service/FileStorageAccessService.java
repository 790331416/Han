package com.han.file.service;

import com.han.common.core.util.FileUploadUtils;
import com.han.common.core.util.HanIdUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.config.StorageConfigRecord;
import com.han.starter.storage.config.StorageConfigRepository;
import com.han.starter.storage.config.StorageRuntimeConfig;
import com.han.starter.storage.impl.S3CompatibleStorageProvider;
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
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 统一文件访问服务。
 *
 * <p>新文件按活动存储配置写入；每条文件记录保存自己的存储配置，后续切换天翼云、RustFS 或其他
 * S3 兼容存储时，历史文件仍从原配置读取。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageAccessService {

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    private final ObjectProvider<StorageConfigRepository> storageConfigRepositoryProvider;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorageProvider> providerCache = new ConcurrentHashMap<>();

    /** 保持已有上传入口兼容；历史调用默认公开，新的敏感业务必须显式传 PRIVATE。 */
    @Transactional(rollbackFor = Exception.class)
    public FileAccessResult upload(MultipartFile file, HttpServletRequest request) throws IOException {
        return upload(file, request, "general", VISIBILITY_PUBLIC, null);
    }

    /** 上传文件并记录其所属对象存储配置。 */
    @Transactional(rollbackFor = Exception.class)
    public FileAccessResult upload(MultipartFile file, HttpServletRequest request, String businessType,
                                   String visibility, Long schoolId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("请选择需要上传的文件");
        }
        StorageConfigRecord storage = resolveActiveRecord();
        Long fileId = HanIdUtil.snowflakeId();
        String name = FileUploadUtils.extractFilename(file);
        String normalizedVisibility = VISIBILITY_PUBLIC.equalsIgnoreCase(visibility)
                ? VISIBILITY_PUBLIC : VISIBILITY_PRIVATE;
        String objectKey = buildObjectKey(fileId, name, businessType, schoolId);
        try (InputStream input = file.getInputStream()) {
            getProvider(storage.getRuntimeConfig()).upload(objectKey, input,
                    contentType(file, name), file.getSize());
        } catch (RuntimeException ex) {
            throw new IOException("文件上传到对象存储失败", ex);
        }
        // 始终返回网关相对路径，内部调用不会把 Docker 主机名或对象存储内网地址泄露给三端。
        String url = "/file/public/" + fileId;
        insertFileRecord(fileId, file, storage, name, objectKey, url, normalizedVisibility, schoolId, businessType);
        return new FileAccessResult(fileId, name, url, normalizedVisibility);
    }

    /** 按文件 ID 读取 Base64，供内部多模态等场景使用。 */
    public FileBase64Result loadBase64(Long fileId) {
        FileRow row = findFile(fileId, false);
        try (InputStream stream = read(row)) {
            byte[] bytes = stream.readAllBytes();
            return new FileBase64Result(row.id(), row.tenantId(), row.fileName(),
                    row.mimeType() == null || row.mimeType().isBlank()
                            ? FileUploadUtils.getContentType(row.objectKey()) : row.mimeType(),
                    row.fileUrl(), java.util.Base64.getEncoder().encodeToString(bytes));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败", ex);
        }
    }

    /** 公开文件读取；只允许 visibility=PUBLIC 的文件。 */
    public DownloadFileResult publicDownload(Long fileId) {
        return download(findFile(fileId, true));
    }

    /** 已鉴权文件读取；权限校验由调用方和业务数据范围共同完成。 */
    public DownloadFileResult downloadById(Long fileId) {
        return download(findFile(fileId, false));
    }

    /** 按文件所属存储生成短时下载地址，供已完成业务权限校验的内部服务调用。 */
    public String createTemporaryUrl(Long fileId, Duration duration) {
        FileRow row = findFile(fileId, false);
        StorageConfigRecord record = resolveRecord(row.locator(), row.storageConfigId());
        return getProvider(record.getRuntimeConfig()).createTemporaryUrl(row.objectKey(), duration);
    }

    /** 保持旧 locator/path 下载接口兼容，供旧文件迁移前读取。 */
    public DownloadFileResult download(String locator, String objectKey) {
        try {
            StorageConfigRecord record = resolveRecord(locator, null);
            InputStream stream = getProvider(record.getRuntimeConfig()).download(objectKey);
            return new DownloadFileResult(objectKey, FileUploadUtils.getContentType(objectKey),
                    resolveFileSize(locator, objectKey), stream);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在", ex);
        }
    }

    /** 对指定存储执行真实的上传、读取、校验和清理探针。 */
    public void testStorage(Long storageConfigId) {
        StorageConfigRecord storage = resolveRecord(null, storageConfigId);
        StorageProvider provider = getProvider(storage.getRuntimeConfig());
        String objectKey = "_han_probe/" + UUID.randomUUID() + ".txt";
        byte[] expected = "han-file-storage-probe".getBytes(StandardCharsets.UTF_8);
        try {
            provider.upload(objectKey, new java.io.ByteArrayInputStream(expected), "text/plain", expected.length);
            try (InputStream input = provider.download(objectKey)) {
                if (!Arrays.equals(expected, input.readAllBytes())) {
                    throw new IllegalStateException("对象存储读取校验失败");
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("对象存储读取校验失败", ex);
        } finally {
            try {
                provider.delete(objectKey);
            } catch (RuntimeException ex) {
                log.warn("对象存储探针清理失败，storageConfigId={}", storageConfigId, ex);
            }
        }
    }

    /** 文件管理分页查询。 */
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
            where.append(" and create_time >= ?");
            args.add(beginTime.trim());
        }
        if (endTime != null && !endTime.isBlank()) {
            where.append(" and create_time <= ?");
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
                        select id, tenant_id as "tenantId", school_id as "schoolId", file_name as "fileName",
                               file_url as "fileUrl", file_size as "fileSize", file_type as "fileType",
                               mime_type as "mimeType", storage_config_id as "storageConfigId",
                               object_key as "objectKey", biz_type as "bizType", visibility,
                               create_by as "createBy", create_time as "createTime"
                        from sys_file
                        """ + where + " order by create_time desc, id desc limit ? offset ?", pagedArgs.toArray());
        return new PageQueryResult(rows, total == null ? 0L : total);
    }

    /** 软删文件记录；对象删除失败不回滚业务删除，交由后续清理任务重试。 */
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
            FileRow row;
            try {
                row = findFile(id, false);
            } catch (ResponseStatusException ex) {
                continue;
            }
            if (!admin && (tenantId == null || tenantId <= 0 || !tenantId.equals(row.tenantId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除该文件");
            }
            removed += jdbcTemplate.update("update sys_file set del_flag = 1 where id = ?", id);
            try {
                StorageConfigRecord record = resolveRecord(row.locator(), row.storageConfigId());
                getProvider(record.getRuntimeConfig()).delete(row.objectKey());
            } catch (RuntimeException ex) {
                log.warn("对象删除失败，保留软删除记录，fileId={}", id, ex);
            }
        }
        return removed;
    }

    /** 内部业务服务删除自身已解除引用的文件。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeInternal(Long fileId) {
        if (fileId == null) {
            return;
        }
        FileRow row = findFile(fileId, false);
        jdbcTemplate.update("update sys_file set del_flag = 1 where id = ?", fileId);
        try {
            StorageConfigRecord record = resolveRecord(row.locator(), row.storageConfigId());
            getProvider(record.getRuntimeConfig()).delete(row.objectKey());
        } catch (RuntimeException ex) {
            log.warn("内部文件删除对象失败，保留软删除记录，fileId={}", fileId, ex);
        }
    }

    private FileRow findFile(Long fileId, boolean publicOnly) {
        try {
            return jdbcTemplate.queryForObject("""
                            select id, tenant_id, file_name, file_url, file_size, mime_type,
                                   storage_config_id, object_key, bucket, visibility
                              from sys_file
                             where id = ? and del_flag = 0
                            """ + (publicOnly ? " and visibility = 'PUBLIC'" : ""),
                    (rs, rowNum) -> new FileRow(
                            rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("file_name"),
                            rs.getString("file_url"), rs.getLong("file_size"), rs.getString("mime_type"),
                            nullableLong(rs, "storage_config_id"), rs.getString("object_key"),
                            rs.getString("bucket"), rs.getString("visibility")), fileId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在或无权访问", ex);
        }
    }

    private DownloadFileResult download(FileRow row) {
        try {
            InputStream stream = read(row);
            return new DownloadFileResult(row.fileName(),
                    row.mimeType() == null || row.mimeType().isBlank()
                            ? FileUploadUtils.getContentType(row.objectKey()) : row.mimeType(),
                    row.fileSize(), stream);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在", ex);
        }
    }

    private InputStream read(FileRow row) {
        StorageConfigRecord record = resolveRecord(row.locator(), row.storageConfigId());
        return getProvider(record.getRuntimeConfig()).download(row.objectKey());
    }

    private StorageConfigRecord resolveActiveRecord() {
        StorageConfigRepository repository = storageConfigRepositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件存储配置不可用");
        }
        return repository.findActiveRecord()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "未配置可用的默认文件存储"));
    }

    private StorageConfigRecord resolveRecord(String locator, Long storageConfigId) {
        StorageConfigRepository repository = storageConfigRepositoryProvider.getIfAvailable();
        String resolvedLocator = storageConfigId == null ? locator : "db-" + storageConfigId;
        if (repository == null || resolvedLocator == null || resolvedLocator.isBlank()) {
            throw new IllegalArgumentException("文件存储配置不存在");
        }
        return repository.findRecord(resolvedLocator)
                .orElseThrow(() -> new IllegalArgumentException("文件存储配置不存在"));
    }

    private StorageProvider getProvider(StorageRuntimeConfig runtimeConfig) {
        return providerCache.computeIfAbsent(runtimeConfig.signature(), ignored -> new S3CompatibleStorageProvider(runtimeConfig));
    }

    private void insertFileRecord(Long fileId, MultipartFile file, StorageConfigRecord storage, String name,
                                  String objectKey, String url, String visibility, Long schoolId, String businessType) {
        String extension = extension(name);
        Long tenantId = SecurityContextHolder.getTenantId();
        Long userId = SecurityContextHolder.getUserId();
        jdbcTemplate.update("""
                        insert into sys_file (
                          id, tenant_id, school_id, file_name, file_path, file_url, file_size, file_type,
                          mime_type, storage_type, bucket, storage_config_id, object_key, biz_type, visibility,
                          md5, create_by, create_time, del_flag
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                fileId, tenantId == null ? 0L : tenantId, schoolId, name, objectKey, url, file.getSize(), extension,
                contentType(file, name), "S3", storage.getLocator(), storage.getRuntimeConfig().getOssConfigId(),
                objectKey, normalizeSegment(businessType), visibility, "", userId, LocalDateTime.now(), 0);
    }

    private Long resolveFileSize(String locator, String objectKey) {
        return jdbcTemplate.query("""
                        select file_size from sys_file
                         where bucket = ? and file_path = ? and del_flag = 0
                         order by create_time desc limit 1
                        """, rs -> rs.next() ? rs.getLong("file_size") : null, locator, objectKey);
    }

    private String buildObjectKey(Long fileId, String name, String businessType, Long schoolId) {
        Long tenantId = SecurityContextHolder.getTenantId();
        YearMonth month = YearMonth.now();
        return "tenant/" + (tenantId == null ? 0 : tenantId)
                + "/school/" + (schoolId == null ? 0 : schoolId)
                + "/" + normalizeSegment(businessType)
                + "/" + month.getYear() + "/" + String.format(Locale.ROOT, "%02d", month.getMonthValue())
                + "/" + fileId + "_" + safeName(name);
    }

    private String contentType(MultipartFile file, String name) {
        String contentType = file.getContentType();
        return contentType == null || contentType.isBlank() ? FileUploadUtils.getContentType(name) : contentType;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String safeName(String name) {
        String value = name == null ? "file" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.isBlank() ? "file" : value.substring(0, Math.min(value.length(), 120));
    }

    private String normalizeSegment(String value) {
        String normalized = value == null ? "general" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-");
        return normalized.isBlank() ? "general" : normalized.substring(0, Math.min(normalized.length(), 48));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record FileBase64Result(Long id, Long tenantId, String name, String mimeType, String url, String base64) {
    }

    public record PageQueryResult(List<Map<String, Object>> rows, long total) {
    }

    public record FileAccessResult(Long id, String name, String url, String visibility) {
    }

    private record FileRow(Long id, Long tenantId, String fileName, String fileUrl, Long fileSize,
                           String mimeType, Long storageConfigId, String objectKey, String locator,
                           String visibility) {
    }

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
