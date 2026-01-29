package com.xuman.api.file;

import com.xuman.api.file.domain.FileDTO;
import com.xuman.common.core.domain.R;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 文件服务HTTP接口
 */
@HttpExchange("/file")
public interface FileServiceClient {

    /**
     * 文件上传
     */
    @PostExchange(value = "/upload", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<FileDTO> upload(@RequestPart("file") MultipartFile file);
}
