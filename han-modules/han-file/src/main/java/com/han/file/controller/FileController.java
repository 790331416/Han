package com.han.file.controller;

import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
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
    public R<FileDTO> upload(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
        try {
            FileStorageAccessService.FileAccessResult result = fileStorageAccessService.upload(file, request);
            FileDTO fileDTO = new FileDTO();
            fileDTO.setName(result.getName());
            fileDTO.setUrl(result.getUrl());
            return R.ok(fileDTO);
        } catch (IOException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 公开文件代理
     */
    @GetMapping("/public/{locator}/{fileName:.+}")
    public ResponseEntity<InputStreamResource> publicAccess(@PathVariable String locator, @PathVariable String fileName) {
        FileStorageAccessService.DownloadFileResult result = fileStorageAccessService.download(locator, fileName);
        String encodedName = URLEncoder.encode(result.getName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(result.getMediaType())
                .body(new InputStreamResource(result.getStream()));
    }
}
