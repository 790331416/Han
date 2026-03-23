package com.han.common.security.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * XSS 请求包装器
 * <p>对请求参数和 JSON Body 进行 XSS 清洗。
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /** 宽松白名单：允许基础 HTML 标签（富文本场景） */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "style", "class")
            .addProtocols("img", "src", "data");

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return value != null ? clean(value) : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = clean(values[i]);
        }
        return cleaned;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return value != null ? clean(value) : null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        String contentType = super.getContentType();
        // 仅对 JSON 请求体进行清洗
        if (contentType != null && contentType.contains("application/json")) {
            String body = readBody(super.getInputStream());
            String cleaned = clean(body);
            byte[] bytes = cleaned.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    // no-op
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }
        return super.getInputStream();
    }

    /**
     * XSS 清洗：使用 Jsoup 白名单过滤
     */
    private static String clean(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Jsoup.clean(value, SAFELIST);
    }

    private static String readBody(ServletInputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
