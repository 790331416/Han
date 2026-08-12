package com.han.file.controller.inner;

import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.api.file.domain.FileInfoDTO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.file.service.FileStorageAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件内部接口（服务间调用）。
 *
 * <p>服务间调用当前不透传登录上下文，租户归属由调用方通过 {@code tenantId} 显式声明，
 * 服务端按声明值强制校验；上下文透传（S-08）落地后可改为从上下文取值。
 */
@InnerAuth
@RestController("innerFileController")
@RequestMapping("/inner/file")
@RequiredArgsConstructor
public class IFileController {

    private final FileStorageAccessService fileStorageAccessService;

    /**
     * 服务间上传：与 {@code /file/upload} 走同一套类型/体积/内容校验，
     * 但允许调用方显式声明归属租户（否则内部上传的文件会全部落成平台级，任何租户都能读）。
     */
    @PostMapping("/upload")
    public R<FileDTO> upload(@RequestPart("file") MultipartFile file,
                             @RequestParam(value = "tenantId", required = false) Long tenantId,
                             HttpServletRequest request) {
        try {
            FileStorageAccessService.FileAccessResult result =
                    fileStorageAccessService.uploadForTenant(file, tenantId, request);
            FileDTO fileDTO = new FileDTO();
            fileDTO.setId(result.id());
            fileDTO.setName(result.name());
            fileDTO.setUrl(result.url());
            return R.ok(fileDTO);
        } catch (IOException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 按文件ID读取元信息（不含内容，调用方据此决定走 Base64 还是流式下载）。
     */
    @GetMapping("/info/{fileId}")
    public R<FileInfoDTO> loadInfo(@PathVariable Long fileId,
                                   @RequestParam(value = "tenantId", required = false) Long tenantId) {
        try {
            FileStorageAccessService.FileRecord record = fileStorageAccessService.loadInfo(fileId, tenantId);
            FileInfoDTO dto = new FileInfoDTO();
            dto.setId(record.id());
            dto.setTenantId(record.tenantId() == null ? 0L : record.tenantId());
            dto.setName(record.displayName());
            dto.setSize(record.fileSize());
            dto.setMimeType(record.mimeType());
            dto.setUrl(record.fileUrl());
            return R.ok(dto);
        } catch (ResponseStatusException ex) {
            return R.fail(ex.getReason() != null ? ex.getReason() : "文件读取失败");
        }
    }

    /**
     * 按文件ID读取 Base64 内容（多模态图片注入等场景，受 han.file.base64-max-size 上限约束）。
     */
    @GetMapping("/base64/{fileId}")
    public R<FileBase64DTO> loadBase64(@PathVariable Long fileId,
                                       @RequestParam(value = "tenantId", required = false) Long tenantId) {
        try {
            FileStorageAccessService.FileBase64Result result = fileStorageAccessService.loadBase64(fileId, tenantId);
            FileBase64DTO dto = new FileBase64DTO();
            dto.setId(result.id());
            dto.setTenantId(result.tenantId());
            dto.setName(result.name());
            dto.setMimeType(result.mimeType());
            dto.setUrl(result.url());
            dto.setBase64(result.base64());
            return R.ok(dto);
        } catch (ResponseStatusException ex) {
            return R.fail(ex.getReason() != null ? ex.getReason() : "文件读取失败");
        }
    }

    /**
     * 按文件ID流式下载原文（知识库原文取回等大文件场景，不受 Base64 上限约束）。
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long fileId,
                                                        @RequestParam(value = "tenantId", required = false) Long tenantId) {
        FileStorageAccessService.DownloadFileResult result = fileStorageAccessService.downloadById(fileId, tenantId);
        String encodedName = URLEncoder.encode(result.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(result.getMediaType());
        if (result.getContentLength() != null && result.getContentLength() > 0) {
            builder.contentLength(result.getContentLength());
        }
        return builder.body(new InputStreamResource(result.getStream()));
    }
}
