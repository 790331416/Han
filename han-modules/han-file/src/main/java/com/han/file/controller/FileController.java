package com.han.file.controller;

import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RateLimiter;
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
    @RateLimiter(key = "file:upload", time = 60, count = 120, limitType = RateLimiter.LimitType.USER)
    @PermissionExempt("上传入口由网关 Token 校验 + 扩展名/内容/体积白名单 + 用户级限流控制；服务间上传走 /inner/file/upload")
    public R<FileDTO> upload(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
        try {
            FileStorageAccessService.FileAccessResult result = fileStorageAccessService.upload(file, request);
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
     * 公开文件代理
     */
    @GetMapping("/public/{locator}/{fileName:.+}")
    @PermissionExempt("公开文件代理入口，服务端强制校验 sys_file 归属、租户与删除标记")
    public ResponseEntity<InputStreamResource> publicAccess(@PathVariable String locator, @PathVariable String fileName) {
        FileStorageAccessService.DownloadFileResult result = fileStorageAccessService.download(locator, fileName);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(result))
                // 非图片/PDF/音视频一律 attachment，再叠一层 nosniff，避免公开代理被嗅探成同域可执行内容
                .header("X-Content-Type-Options", "nosniff")
                .contentType(result.getMediaType());
        if (result.getContentLength() != null && result.getContentLength() > 0) {
            builder.contentLength(result.getContentLength());
        }
        return builder.body(new InputStreamResource(result.getStream()));
    }

    private String buildContentDisposition(FileStorageAccessService.DownloadFileResult result) {
        String encodedName = URLEncoder.encode(result.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        String type = result.isInlineSafe() ? "inline" : "attachment";
        return type + "; filename*=UTF-8''" + encodedName;
    }
}
