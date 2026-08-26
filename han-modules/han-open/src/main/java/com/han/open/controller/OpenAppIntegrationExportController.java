package com.han.open.controller;

import com.han.common.security.annotation.AdminAuth;
import com.han.open.domain.dto.OpenApiIntegrationExportDTO;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.service.IOpenAppService;
import com.han.open.service.OpenApiIntegrationExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** 按当前用户可见应用及有效授权生成应用级对接包。 */
@AdminAuth
@RestController
@RequestMapping("/open/app/integration")
@RequiredArgsConstructor
public class OpenAppIntegrationExportController {

    private final IOpenAppService appService;
    private final OpenApiIntegrationExportService exportService;

    @GetMapping(value = "/package", produces = "application/zip")
    @PreAuthorize("@ss.hasAuthority('open:app:query')")
    public ResponseEntity<byte[]> zip(@RequestParam Long appId, @RequestParam String environment,
                                      @RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = build(appId, environment, baseUrl, request);
        return attachment(export.filenameBase() + ".zip", MediaType.parseMediaType("application/zip"), export.zip());
    }

    @GetMapping(value = "/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasAuthority('open:app:query')")
    public ResponseEntity<byte[]> openApi(@RequestParam Long appId, @RequestParam String environment,
                                          @RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = build(appId, environment, baseUrl, request);
        return attachment(export.filenameBase() + "-OpenAPI.json", MediaType.APPLICATION_JSON, export.openApiJson());
    }

    @GetMapping(value = "/postman", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasAuthority('open:app:query')")
    public ResponseEntity<byte[]> postman(@RequestParam Long appId, @RequestParam String environment,
                                          @RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = build(appId, environment, baseUrl, request);
        return attachment(export.filenameBase() + "-Postman.json", MediaType.APPLICATION_JSON,
                export.postmanCollectionJson());
    }

    @GetMapping(value = "/environment", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasAuthority('open:app:query')")
    public ResponseEntity<byte[]> environment(@RequestParam Long appId, @RequestParam String environment,
                                              @RequestParam(required = false) String baseUrl, HttpServletRequest request) {
        OpenApiIntegrationExportDTO export = build(appId, environment, baseUrl, request);
        return attachment(export.filenameBase() + "-Postman环境.json", MediaType.APPLICATION_JSON,
                export.postmanEnvironmentJson());
    }

    private OpenApiIntegrationExportDTO build(Long appId, String environment, String baseUrl,
                                              HttpServletRequest request) {
        OpenAppVO app = appService.selectVoById(appId);
        return exportService.buildForApp(resolveBaseUrl(baseUrl, request), app, environment);
    }

    private String resolveBaseUrl(String requested, HttpServletRequest request) {
        if (StringUtils.hasText(requested)) return requested;
        URI requestUri = URI.create(request.getRequestURL().toString());
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return requestUri.getScheme() + "://" + requestUri.getRawAuthority() + contextPath;
    }

    private ResponseEntity<byte[]> attachment(String filename, MediaType mediaType, byte[] content) {
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(content);
    }
}
