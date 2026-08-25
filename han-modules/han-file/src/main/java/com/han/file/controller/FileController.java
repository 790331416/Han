package com.han.file.controller;

import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.file.service.FileStorageAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件请求处理
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageAccessService fileStorageAccessService;

    /**
     * 文件上传
     */
    @PostMapping("/upload")
    @PermissionExempt("文件上传入口由网关 Token 校验和存储服务策略控制")
    public R<FileDTO> upload(@RequestPart("file") MultipartFile file,
                             @RequestParam(value = "bizType", defaultValue = "general") String businessType,
                             @RequestParam(value = "visibility", defaultValue = "PUBLIC") String visibility,
                             HttpServletRequest request) {
        try {
            FileStorageAccessService.FileAccessResult result = fileStorageAccessService.upload(
                    file, request, businessType, visibility, null);
            FileDTO fileDTO = new FileDTO();
            fileDTO.setId(result.id());
            fileDTO.setName(result.name());
            fileDTO.setUrl(result.url());
            fileDTO.setSize(file.getSize());
            fileDTO.setContentType(file.getContentType());
            fileDTO.setVisibility(result.visibility());
            return R.ok(fileDTO);
        } catch (IOException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 公开文件代理；不泄露对象存储 Endpoint、桶或签名。 */
    @GetMapping("/public/{fileId}")
    @PermissionExempt("仅允许文件元数据明确标记为公开的资源")
    public ResponseEntity<InputStreamResource> publicAccessById(@PathVariable Long fileId) {
        return response(fileStorageAccessService.publicDownload(fileId));
    }

    /**
     * 公开文件代理
     */
    @GetMapping("/public/{locator}/{fileName:.+}")
    @PermissionExempt("公开文件代理入口，仅暴露已生成的公开文件 locator")
    public ResponseEntity<InputStreamResource> publicAccess(@PathVariable String locator, @PathVariable String fileName) {
        return response(fileStorageAccessService.download(locator, fileName));
    }

    private ResponseEntity<InputStreamResource> response(FileStorageAccessService.DownloadFileResult result) {
        String encodedName = URLEncoder.encode(result.getName(), StandardCharsets.UTF_8);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(result.getMediaType());
        if (result.getContentLength() != null && result.getContentLength() > 0) {
            builder.contentLength(result.getContentLength());
        }
        return builder.body(new InputStreamResource(result.getStream()));
    }
}
