package com.han.aivideo.domain.vo;

import org.springframework.http.MediaType;

import java.io.InputStream;

/**
 * Media preview stream resolved through AIVideo permission checks.
 */
public record AivideoMediaPreviewResource(String fileName, MediaType mediaType, InputStream stream) {
}
