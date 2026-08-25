package com.han.api.file;

import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 服务间上传，调用方必须携带已完成数据范围校验的学校与业务归属。 */
    @PostExchange(value = "/inner/file/upload", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<FileDTO> uploadInternal(@RequestPart("file") Resource file,
                              @RequestParam("bizType") String businessType,
                              @RequestParam("visibility") String visibility,
                              @RequestParam(value = "schoolId", required = false) Long schoolId);

    /** 真实对象存储探针：上传、读取校验、精确清理。 */
    @PostExchange("/inner/file/storage/{storageConfigId}/test")
    R<Void> testStorage(@PathVariable("storageConfigId") Long storageConfigId);

    /** 内部服务在完成业务权限校验后申请短时下载地址。 */
    @GetExchange("/inner/file/{fileId}/temporary-url")
    R<String> temporaryUrl(@PathVariable("fileId") Long fileId);

    /** 内部业务服务在解除引用后精确删除文件。 */
    @PostExchange("/inner/file/{fileId}/remove")
    R<Void> removeInternal(@PathVariable("fileId") Long fileId);

    /**
     * 按文件ID读取 Base64 内容（内部接口，多模态图片注入等场景）
     */
    @GetExchange("/inner/file/base64/{fileId}")
    R<FileBase64DTO> loadBase64(@PathVariable("fileId") Long fileId);
}
