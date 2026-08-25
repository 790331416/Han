package com.han.file.controller.inner;

import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.file.service.FileStorageAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;

/**
 * 文件内部接口（服务间调用）。
 */
@InnerAuth
@RestController("innerFileController")
@RequestMapping("/inner/file")
@RequiredArgsConstructor
public class IFileController {

    private final FileStorageAccessService fileStorageAccessService;

    /** 供校端、AI等服务上传；外部浏览器不得直接调用此入口。 */
    @PostMapping("/upload")
    public R<FileDTO> upload(@RequestPart("file") MultipartFile file,
                             @RequestParam("bizType") String businessType,
                             @RequestParam(value = "visibility", defaultValue = "PRIVATE") String visibility,
                             @RequestParam(value = "schoolId", required = false) Long schoolId,
                             HttpServletRequest request) {
        try {
            FileStorageAccessService.FileAccessResult result = fileStorageAccessService.upload(
                    file, request, businessType, visibility, schoolId);
            FileDTO dto = new FileDTO(result.id(), result.name(), result.url());
            dto.setSize(file.getSize());
            dto.setContentType(file.getContentType());
            dto.setVisibility(result.visibility());
            return R.ok(dto);
        } catch (IOException ex) {
            return R.fail(ex.getMessage());
        }
    }

    /** 仅供系统配置管理测试对象存储，不落业务文件记录。 */
    @PostMapping("/storage/{storageConfigId}/test")
    public R<Void> testStorage(@PathVariable Long storageConfigId) {
        try {
            fileStorageAccessService.testStorage(storageConfigId);
            return R.ok();
        } catch (RuntimeException ex) {
            return R.fail(ex.getMessage() == null ? "对象存储连接测试失败" : ex.getMessage());
        }
    }

    /** 短时地址只供内部业务服务取得；外部用户权限必须在业务服务侧先完成校验。 */
    @GetMapping("/{fileId}/temporary-url")
    public R<String> temporaryUrl(@PathVariable Long fileId) {
        try {
            return R.ok(fileStorageAccessService.createTemporaryUrl(fileId, Duration.ofMinutes(10)));
        } catch (RuntimeException ex) {
            return R.fail(ex.getMessage() == null ? "临时下载地址生成失败" : ex.getMessage());
        }
    }

    /** 调用方必须先完成业务解除引用；该接口只接受内部鉴权。 */
    @PostMapping("/{fileId}/remove")
    public R<Void> remove(@PathVariable Long fileId) {
        try {
            fileStorageAccessService.removeInternal(fileId);
            return R.ok();
        } catch (RuntimeException ex) {
            return R.fail(ex.getMessage() == null ? "文件删除失败" : ex.getMessage());
        }
    }

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
