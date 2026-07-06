package com.han.api.file;

import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 文件服务HTTP接口
 */
@HttpExchange
public interface FileServiceClient {

    /**
     * 文件上传
     */
    @PostExchange(value = "/file/upload", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<FileDTO> upload(@RequestPart("file") Resource file);

    /**
     * 按文件ID读取 Base64 内容（内部接口，多模态图片注入等场景）
     */
    @GetExchange("/inner/file/base64/{fileId}")
    R<FileBase64DTO> loadBase64(@PathVariable("fileId") Long fileId);
}
