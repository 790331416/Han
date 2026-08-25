package com.han.open.domain.dto;

/**
 * 开放平台对接文档导出内容。
 */
public record OpenApiIntegrationExportDTO(
        byte[] openApiJson,
        byte[] postmanCollectionJson,
        byte[] postmanEnvironmentJson,
        byte[] readme,
        byte[] zip) {
}
