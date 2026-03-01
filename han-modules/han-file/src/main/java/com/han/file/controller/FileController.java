package com.han.file.controller;

import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import com.han.common.core.util.FileUploadUtils;
import com.han.starter.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件请求处理
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final StorageProvider storageProvider;

    /**
     * 文件上传
     */
    @PostMapping("/upload")
    public R<FileDTO> upload(@RequestPart("file") MultipartFile file) {
        try {
            // 上传并返回访问地址
            String name = FileUploadUtils.extractFilename(file);
            String url = storageProvider.upload(name, file.getInputStream(), file.getContentType());
            
            FileDTO fileDTO = new FileDTO();
            fileDTO.setName(name);
            fileDTO.setUrl(url);
            return R.ok(fileDTO);
        } catch (IOException e) {
            return R.fail(e.getMessage());
        }
    }
}
