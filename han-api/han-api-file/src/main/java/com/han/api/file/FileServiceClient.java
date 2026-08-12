package com.han.api.file;

import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.api.file.domain.FileInfoDTO;
import com.han.common.core.domain.R;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 文件服务HTTP接口
 *
 * <p>服务间调用不透传登录上下文，涉及归属的接口都带 {@code tenantId} 参数由调用方显式声明，
 * 文件服务按声明值强制校验；不声明时上传的文件会落成平台级（任何租户可读），
 * 凡是租户私有的内容（知识库原文、业务附件）必须传租户ID。
 */
@HttpExchange
public interface FileServiceClient {

    /**
     * 文件上传（不声明归属租户，落为平台级文件）
     */
    default R<FileDTO> upload(Resource file) {
        return upload(file, null);
    }

    /**
     * 文件上传并声明归属租户
     */
    @PostExchange(value = "/inner/file/upload", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<FileDTO> upload(@RequestPart("file") Resource file, @RequestParam("tenantId") Long tenantId);

    /**
     * 按文件ID读取元信息（内部接口）
     */
    @GetExchange("/inner/file/info/{fileId}")
    R<FileInfoDTO> loadInfo(@PathVariable("fileId") Long fileId, @RequestParam("tenantId") Long tenantId);

    /**
     * 按文件ID读取 Base64 内容（内部接口，多模态图片注入等场景）
     *
     * <p>受服务端 {@code han.file.base64-max-size} 上限约束，超限会直接拒绝；
     * 大文件请改用 {@link #download(Long, Long)}。
     */
    default R<FileBase64DTO> loadBase64(Long fileId) {
        return loadBase64(fileId, null);
    }

    /**
     * 按文件ID读取 Base64 内容并声明归属租户（内部接口）
     */
    @GetExchange("/inner/file/base64/{fileId}")
    R<FileBase64DTO> loadBase64(@PathVariable("fileId") Long fileId, @RequestParam("tenantId") Long tenantId);

    /**
     * 按文件ID取回原文（内部接口，知识库原文取回等大文件场景，不受 Base64 上限约束）
     */
    @GetExchange("/inner/file/download/{fileId}")
    Resource download(@PathVariable("fileId") Long fileId, @RequestParam("tenantId") Long tenantId);
}
