package com.han.file.controller.inner;

import com.han.api.file.domain.FileBase64DTO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.file.service.FileStorageAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 文件内部接口（服务间调用）。
 */
@InnerAuth
@RestController("innerFileController")
@RequestMapping("/inner/file")
@RequiredArgsConstructor
public class IFileController {

    private final FileStorageAccessService fileStorageAccessService;

    /**
     * 按文件ID读取 Base64 内容（多模态图片注入等场景）。
     */
    @GetMapping("/base64/{fileId}")
    public R<FileBase64DTO> loadBase64(@PathVariable Long fileId) {
        try {
            FileStorageAccessService.FileBase64Result result = fileStorageAccessService.loadBase64(fileId);
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
}
