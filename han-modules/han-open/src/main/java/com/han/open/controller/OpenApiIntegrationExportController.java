package com.han.open.controller;

import com.han.common.security.annotation.PermissionExempt;
import com.han.common.tenant.annotation.IgnoreTenant;
import com.han.open.domain.dto.OpenApiIntegrationExportDTO;
import com.han.open.service.OpenApiIntegrationExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 公开对接文档：文档本身无需登录，真实调用仍由 OAuth2 应用凭证校验。
 */
@RestController
@RequestMapping("/open/public/integration")
@RequiredArgsConstructor
public class OpenApiIntegrationExportController {

    private final OpenApiIntegrationExportService exportService;

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @PermissionExempt("公开 OpenAPI 文档，不包含应用凭证")
    @IgnoreTenant
    public ResponseEntity<byte[]> openApi(@RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = exportService.build(resolveBaseUrl(baseUrl, request));
        return attachment("lubashu-openapi.json", MediaType.APPLICATION_JSON, export.openApiJson());
    }

    @GetMapping(value = "/postman.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @PermissionExempt("公开 Postman Collection 模板，密钥变量为空")
    @IgnoreTenant
    public ResponseEntity<byte[]> postman(@RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = exportService.build(resolveBaseUrl(baseUrl, request));
        return attachment("lubashu-open-platform.postman_collection.json", MediaType.APPLICATION_JSON,
                export.postmanCollectionJson());
    }

    @GetMapping(value = "/environment.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @PermissionExempt("公开 Postman 环境模板，密钥变量为空")
    @IgnoreTenant
    public ResponseEntity<byte[]> environment(@RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = exportService.build(resolveBaseUrl(baseUrl, request));
        return attachment("lubashu-open-platform.postman_environment.json", MediaType.APPLICATION_JSON,
                export.postmanEnvironmentJson());
    }

    @GetMapping(value = "/package.zip", produces = "application/zip")
    @PermissionExempt("公开对接文档包，不包含应用凭证")
    @IgnoreTenant
    public ResponseEntity<byte[]> zip(@RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = exportService.build(resolveBaseUrl(baseUrl, request));
        return attachment("lubashu-open-platform-integration.zip", MediaType.parseMediaType("application/zip"), export.zip());
    }

    private String resolveBaseUrl(String requested, HttpServletRequest request) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        URI requestUri = URI.create(request.getRequestURL().toString());
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return requestUri.getScheme() + "://" + requestUri.getRawAuthority() + contextPath;
    }

    private ResponseEntity<byte[]> attachment(String filename, MediaType mediaType, byte[] content) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(content);
    }
}
