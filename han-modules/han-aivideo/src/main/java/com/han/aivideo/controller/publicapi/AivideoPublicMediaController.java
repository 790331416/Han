package com.han.aivideo.controller.publicapi;

import com.han.aivideo.domain.vo.AivideoMediaPreviewResource;
import com.han.aivideo.service.IAivideoSceneImageService;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.PermissionExempt;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController("aivideoPublicMediaController")
@RequestMapping("/aivideo/public")
@RequiredArgsConstructor
public class AivideoPublicMediaController {

    private final IAivideoSceneImageService sceneImageService;

    @GetMapping("/media/{mediaId}/preview")
    @PermissionExempt("AI short-drama public media preview, gated by media access policy")
    public ResponseEntity<InputStreamResource> previewPublicMedia(@PathVariable Long mediaId) {
        try {
            AivideoMediaPreviewResource resource = sceneImageService.previewPublicMedia(mediaId);
            String encodedName = URLEncoder.encode(resource.fileName(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                    .contentType(resource.mediaType())
                    .body(new InputStreamResource(resource.stream()));
        } catch (BusinessException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }
}
