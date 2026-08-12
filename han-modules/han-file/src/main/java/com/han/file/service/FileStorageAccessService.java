package com.han.file.service;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.FileUploadUtils;
import com.han.common.core.util.HanIdUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.file.config.FileProperties;
import com.han.file.security.FileSignatureDetector;
import com.han.file.security.FileUploadValidator;
import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.StorageProviderCache;
import com.han.starter.storage.config.StorageConfigRecord;
import com.han.starter.storage.config.StorageConfigRepository;
import com.han.starter.storage.config.StorageProperties;
import com.han.starter.storage.config.StorageRuntimeConfig;
import com.han.starter.storage.impl.RustFSStorageProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文件存储访问服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageAccessService {

    /**
     * 允许以 inline 方式在浏览器里直接渲染的类型；其余一律 attachment，避免同域内容被当页面执行。
     */
    private static final Set<String> INLINE_SAFE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
            "application/pdf",
            "video/mp4", "video/webm", "video/quicktime",
            "audio/mpeg", "audio/wav"
    );

    private static final String SELECT_FILE_COLUMNS =
            "select id, tenant_id, file_name, file_path, file_url, file_size, mime_type, bucket from sys_file";

    private final StorageProperties storageProperties;
    private final FileProperties fileProperties;
    private final FileUploadValidator uploadValidator;
    private final ObjectProvider<StorageConfigRepository> storageConfigRepositoryProvider;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    private StorageProviderCache providerCache;

    @PostConstruct
    void initProviderCache() {
        this.providerCache = new StorageProviderCache(storageProperties.getProviderCacheSize());
    }

    @PreDestroy
    void closeProviders() {
        if (providerCache != null) {
            providerCache.clear();
        }
    }

    /**
     * 上传文件并生成公开访问地址。
     *
     * <p>校验顺序：扩展名白名单与体积上限 → 文件真实内容（魔数）→ 写对象存储 → 落库。
     * 对象存储写入不在数据库事务里（外部副作用不可回滚），落库失败时补偿删除已写对象。
     *
     * @param file    上传文件
     * @param request 当前请求
     * @return 上传结果
     * @throws IOException 上传失败
     */
    public FileAccessResult upload(MultipartFile file, HttpServletRequest request) throws IOException {
        return uploadForTenant(file, null, request);
    }

    /**
     * 上传文件并显式指定归属租户（服务间调用用；当前上下文不透传租户，不显式声明就会全部落成平台级文件）。
     *
     * @param file            上传文件
     * @param declaredTenantId 调用方声明的归属租户ID
     * @param request         当前请求
     * @return 上传结果
     * @throws IOException 上传失败
     */
    public FileAccessResult uploadForTenant(MultipartFile file, Long declaredTenantId, HttpServletRequest request)
            throws IOException {
        if (file == null) {
            throw new BusinessException("上传文件不能为空");
        }
        FileUploadValidator.ValidatedUpload validated =
                uploadValidator.validateMetadata(file.getOriginalFilename(), file.getSize());

        byte[] header;
        try (InputStream probe = file.getInputStream()) {
            header = probe.readNBytes(FileSignatureDetector.PROBE_BYTES);
        }
        uploadValidator.verifyContent(validated, header);

        StorageConfigRecord record = resolveActiveRecord();
        String storageKey = FileUploadUtils.extractFilename(file);
        String md5;
        try (InputStream content = file.getInputStream()) {
            DigestStream digestStream = new DigestStream(content);
            getProvider(record.getRuntimeConfig())
                    .upload(storageKey, digestStream, validated.contentType(), validated.size());
            md5 = digestStream.hex();
        }

        String url = buildPublicUrl(request, record.getLocator(), storageKey);
        try {
            Long fileId = insertFileRecord(validated, record, storageKey, url, md5, declaredTenantId);
            return new FileAccessResult(fileId, validated.originalName(), storageKey, url);
        } catch (RuntimeException ex) {
            deleteObjectQuietly(record.getLocator(), storageKey);
            throw ex;
        }
    }

    /**
     * 按文件ID读取文件字节并编码为 Base64（服务间调用，多模态图片注入等场景）。
     *
     * @param fileId         文件ID
     * @param callerTenantId 调用方声明的租户ID，非空时服务端强制校验归属
     * @return Base64 结果
     */
    public FileBase64Result loadBase64(Long fileId, Long callerTenantId) {
        FileRecord record = requireFileRecord(fileId);
        requireTenantAccess(record.tenantId(), callerTenantId);

        long maxBytes = fileProperties.getBase64MaxSize().toBytes();
        if (record.fileSize() != null && record.fileSize() > maxBytes) {
            throw new BusinessException("文件超过 Base64 读取上限，请改用下载地址: " + record.fileSize() + " > " + maxBytes);
        }
        try (InputStream stream = openStream(record.bucket(), record.filePath())) {
            // file_size 可能与实际对象不一致，按实际读取字节再兜一次上限，避免整文件撑爆堆
            byte[] bytes = stream.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
            if (bytes.length > maxBytes) {
                throw new BusinessException("文件超过 Base64 读取上限，请改用下载地址");
            }
            return new FileBase64Result(
                    record.id(),
                    record.tenantId() == null ? 0L : record.tenantId(),
                    record.displayName(),
                    resolveContentType(record),
                    record.fileUrl(),
                    java.util.Base64.getEncoder().encodeToString(bytes));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File read failed", ex);
        }
    }

    /**
     * 按文件ID读取元信息（服务间调用：知识库等场景需要先拿到大小与类型再决定怎么取内容）。
     *
     * @param fileId         文件ID
     * @param callerTenantId 调用方声明的租户ID，非空时服务端强制校验归属
     * @return 文件元信息
     */
    public FileRecord loadInfo(Long fileId, Long callerTenantId) {
        FileRecord record = requireFileRecord(fileId);
        requireTenantAccess(record.tenantId(), callerTenantId);
        return record;
    }

    /**
     * 按文件ID流式下载（服务间调用：知识库原文取回，不受 Base64 上限约束）。
     *
     * @param fileId         文件ID
     * @param callerTenantId 调用方声明的租户ID，非空时服务端强制校验归属
     * @return 下载结果
     */
    public DownloadFileResult downloadById(Long fileId, Long callerTenantId) {
        FileRecord record = requireFileRecord(fileId);
        requireTenantAccess(record.tenantId(), callerTenantId);
        return toDownloadResult(record);
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
        LocalDateTime begin = parseTimeParam(beginTime, "beginTime");
        if (begin != null) {
            where.append(" and create_time >= ?");
            args.add(java.sql.Timestamp.valueOf(begin));
        }
        LocalDateTime end = parseTimeParam(endTime, "endTime");
        if (end != null) {
            where.append(" and create_time <= ?");
            args.add(java.sql.Timestamp.valueOf(end));
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
        return new PageQueryResult(rows, total == null ? 0L : total, safePageNum, safePageSize);
    }

    /**
     * 批量删除。
     *
     * <p>严格分三段执行：<br>
     * 1) 事务外一次查全并校验**全部** ID 的归属，有一个越权就整批拒绝，此时尚未破坏任何数据；<br>
     * 2) 事务内批量软删 sys_file；<br>
     * 3) 事务提交后再删对象存储，失败只记日志（最坏是留孤儿对象，可由对账任务清理）。<br>
     * 物理删除属于不可回滚的外部副作用，绝不能放在事务里——否则后续 ID 越权触发回滚时，
     * 数据库记录恢复成「未删除」而对象已经永久销毁。
     */
    public int removeByIds(List<Long> ids, Long tenantId, boolean admin) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return 0;
        }

        List<FileRecord> records = selectRecordsByIds(distinctIds);
        if (records.isEmpty()) {
            return 0;
        }
        if (!admin) {
            for (FileRecord record : records) {
                long ownerTenantId = record.tenantId() == null ? 0L : record.tenantId();
                if (tenantId == null || tenantId <= 0 || ownerTenantId != tenantId) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to delete file " + record.id());
                }
            }
        }

        List<Long> deletableIds = records.stream().map(FileRecord::id).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(deletableIds.size(), "?"));
        Integer removed = transactionTemplate.execute(status -> jdbcTemplate.update(
                "update sys_file set del_flag = 1 where del_flag = 0 and id in (" + placeholders + ")",
                deletableIds.toArray()));

        for (FileRecord record : records) {
            deleteObjectQuietly(record.bucket(), record.filePath());
        }
        return removed == null ? 0 : removed;
    }

    /**
     * 按存储定位符下载文件。
     *
     * <p>先在 sys_file 上确认「记录存在、未被删除、调用方有权访问」，再去对象存储取流；
     * 不再允许只凭猜到的对象 key 直接穿透到存储层。
     *
     * @param locator  存储定位符
     * @param fileName 存储对象名
     * @return 下载结果
     */
    public DownloadFileResult download(String locator, String fileName) {
        FileRecord record = requireFileRecord(locator, fileName);
        requireTenantAccess(record.tenantId(), null);
        return toDownloadResult(record);
    }

    private DownloadFileResult toDownloadResult(FileRecord record) {
        String contentType = resolveContentType(record);
        InputStream stream = openStream(record.bucket(), record.filePath());
        return new DownloadFileResult(
                record.displayName(),
                contentType,
                record.fileSize(),
                INLINE_SAFE_TYPES.contains(contentType),
                stream);
    }

    private InputStream openStream(String locator, String filePath) {
        try {
            StorageConfigRecord config = resolveRecord(locator);
            return getProvider(config.getRuntimeConfig()).download(filePath);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage locator not found", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found", ex);
        }
    }

    /**
     * 对外 MIME 一律按对象 key 的扩展名推导。
     *
     * <p>历史行里的 mime_type 是客户端上传时声明的、可以伪造（例如 text/html），
     * 直接回显会把公开下载变成同域脚本执行面，因此不采信库里的值。
     */
    private String resolveContentType(FileRecord record) {
        String extension = FileUploadValidator.extractExtension(record.filePath());
        return uploadValidator.resolveContentType(record.filePath(), extension);
    }

    private FileRecord requireFileRecord(Long fileId) {
        List<FileRecord> records = jdbcTemplate.query(
                SELECT_FILE_COLUMNS + " where id = ? and del_flag = 0", FILE_RECORD_MAPPER, fileId);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File record not found");
        }
        return records.get(0);
    }

    private FileRecord requireFileRecord(String locator, String storagePath) {
        if (!StringUtils.hasText(locator) || !StringUtils.hasText(storagePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File record not found");
        }
        List<FileRecord> records = jdbcTemplate.query(
                SELECT_FILE_COLUMNS + " where bucket = ? and file_path = ? and del_flag = 0"
                        + " order by create_time desc, id desc limit 1",
                FILE_RECORD_MAPPER, locator, storagePath);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File record not found");
        }
        return records.get(0);
    }

    private List<FileRecord> selectRecordsByIds(Set<Long> ids) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query(
                SELECT_FILE_COLUMNS + " where del_flag = 0 and id in (" + placeholders + ")",
                FILE_RECORD_MAPPER, ids.toArray());
    }

    /**
     * 归属校验。
     *
     * <p>两个信息源：调用方显式声明的租户（服务间调用，当前上下文不透传租户），
     * 以及本地登录上下文。任一存在就必须对得上；平台级文件（tenantId=0）对所有租户可见。
     */
    private void requireTenantAccess(Long ownerTenantId, Long callerTenantId) {
        long owner = ownerTenantId == null ? 0L : ownerTenantId;
        if (callerTenantId != null && callerTenantId > 0 && owner > 0 && owner != callerTenantId) {
            log.warn("Cross-tenant file access rejected, owner={}, caller={}", owner, callerTenantId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to access this file");
        }
        if (SecurityContextHolder.getLoginUser() == null || SecurityContextHolder.isAdmin()) {
            return;
        }
        Long currentTenantId = SecurityContextHolder.getTenantId();
        if (owner > 0 && (currentTenantId == null || owner != currentTenantId)) {
            log.warn("Cross-tenant file access rejected, owner={}, current={}", owner, currentTenantId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to access this file");
        }
    }

    /**
     * 生成对外访问地址。
     *
     * <p>配置了 {@code han.file.public-base-url} 就一律用它：服务间调用（@HttpExchange 直连实例、
     * 不带 X-Forwarded-* 头）按请求推导出来的是容器内网地址，浏览器根本访问不到。
     */
    private String buildPublicUrl(HttpServletRequest request, String locator, String storageKey) {
        String base = fileProperties.getPublicBaseUrl();
        if (StringUtils.hasText(base)) {
            return UriComponentsBuilder.fromUriString(base.trim())
                    .replacePath("/file/public/{locator}/{name}")
                    .replaceQuery(null)
                    .buildAndExpand(locator, storageKey)
                    .toUriString();
        }
        if (request == null) {
            return "/file/public/" + locator + "/" + storageKey;
        }
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/file/public/{locator}/{name}")
                .replaceQuery(null)
                .buildAndExpand(locator, storageKey)
                .toUriString();
    }

    private void deleteObjectQuietly(String locator, String storagePath) {
        try {
            StorageConfigRecord config = resolveRecord(locator);
            getProvider(config.getRuntimeConfig()).delete(storagePath);
        } catch (RuntimeException ex) {
            log.warn("Physical file delete failed, locator={}, path={}", locator, storagePath, ex);
        }
    }

    private LocalDateTime parseTimeParam(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() <= 10) {
                return java.time.LocalDate.parse(text).atStartOfDay();
            }
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            throw new BusinessException("时间参数格式错误: " + field);
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
        return providerCache.get(runtimeConfig.signature(), () -> new RustFSStorageProvider(runtimeConfig));
    }

    private Long insertFileRecord(FileUploadValidator.ValidatedUpload validated, StorageConfigRecord record,
                                  String storageKey, String url, String md5, Long declaredTenantId) {
        Long fileId = HanIdUtil.snowflakeId();
        Long tenantId = declaredTenantId != null && declaredTenantId > 0
                ? declaredTenantId
                : SecurityContextHolder.getTenantId();
        Long userId = SecurityContextHolder.getUserId();
        jdbcTemplate.update("""
                        insert into sys_file (
                          id, tenant_id, file_name, file_path, file_url, file_size, file_type,
                          mime_type, storage_type, bucket, md5, create_by, create_time, del_flag
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                fileId,
                tenantId == null ? 0L : tenantId,
                validated.originalName(),
                storageKey,
                url,
                validated.size(),
                validated.extension(),
                validated.contentType(),
                "rustfs",
                record.getLocator(),
                md5 == null ? "" : md5,
                userId,
                LocalDateTime.now(),
                0);
        return fileId;
    }

    private static final org.springframework.jdbc.core.RowMapper<FileRecord> FILE_RECORD_MAPPER = (rs, rowNum) -> {
        long tenantId = rs.getLong("tenant_id");
        Long tenantIdValue = rs.wasNull() ? null : tenantId;
        long fileSize = rs.getLong("file_size");
        Long fileSizeValue = rs.wasNull() ? null : fileSize;
        return new FileRecord(
                rs.getLong("id"),
                tenantIdValue,
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getString("file_url"),
                fileSizeValue,
                rs.getString("mime_type"),
                rs.getString("bucket"));
    };

    /**
     * 流式 MD5 计算包装。
     *
     * <p>S3 SDK 重试时会 reset 流，这里同步重置摘要，避免重试后算出错误的 MD5。
     */
    private static final class DigestStream extends FilterInputStream {

        private final MessageDigest digest;

        private DigestStream(InputStream in) {
            super(in);
            try {
                this.digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("MD5 digest unavailable", ex);
            }
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                digest.update((byte) value);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int read = super.read(buffer, off, len);
            if (read > 0) {
                digest.update(buffer, off, read);
            }
            return read;
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            digest.reset();
        }

        private String hex() {
            return HexFormat.of().formatHex(digest.digest()).toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 上传结果。
     *
     * @param id         文件ID
     * @param name       原始文件名（可读名称）
     * @param storageKey 对象存储中的对象名
     * @param url        对外访问地址
     */
    public record FileAccessResult(Long id, String name, String storageKey, String url) {
    }

    /**
     * sys_file 记录（下载与归属校验的唯一依据）。
     */
    public record FileRecord(Long id, Long tenantId, String fileName, String filePath, String fileUrl,
                             Long fileSize, String mimeType, String bucket) {

        /**
         * 对外可读名称；历史记录里 file_name 存的是对象 key，此时退回对象 key。
         *
         * @return 可读文件名
         */
        public String displayName() {
            return StringUtils.hasText(fileName) ? fileName : filePath;
        }
    }

    /**
     * Base64 读取结果。
     */
    public record FileBase64Result(Long id, Long tenantId, String name, String mimeType, String url, String base64) {
    }

    /**
     * 文件管理分页结果（页码与页长均为服务端夹紧后的实际值）。
     */
    public record PageQueryResult(List<Map<String, Object>> rows, long total, int pageNum, int pageSize) {
    }

    /**
     * 下载结果。
     */
    public static final class DownloadFileResult {
        private final String name;
        private final String contentType;
        private final Long contentLength;
        private final boolean inlineSafe;
        private final InputStream stream;

        public DownloadFileResult(String name, String contentType, Long contentLength,
                                  boolean inlineSafe, InputStream stream) {
            this.name = name;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.inlineSafe = inlineSafe;
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

        /**
         * 是否允许 inline 渲染；非安全类型强制 attachment 下载。
         *
         * @return 是否可 inline
         */
        public boolean isInlineSafe() {
            return inlineSafe;
        }

        public InputStream getStream() {
            return stream;
        }
    }
}
