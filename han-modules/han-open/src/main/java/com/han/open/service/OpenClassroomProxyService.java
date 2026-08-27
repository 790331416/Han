package com.han.open.service;

import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.InnerAuthSignUtil;
import com.han.open.domain.vo.OpenAccessTokenContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * 开放平台到旧三个课堂的受控转发。
 *
 * <p>这里不解释或重组下游 JSON，避免改变视频平台依赖的旧 Result 信封。
 * 下游 {@code /inner/open-classroom/**} 只接受本服务签发的内部调用签名。</p>
 */
@Service
@RequiredArgsConstructor
public class OpenClassroomProxyService {

    private static final String SERVICE = "http://three-classroom-api-teacher";
    public static final String OPEN_APP_ID = "X-Open-App-Id";
    public static final String OPEN_TENANT_ID = "X-Open-Tenant-Id";
    public static final String OPEN_CLIENT_ID = "X-Open-Client-Id";
    public static final String OPEN_SCHOOL_IDS = "X-Open-School-Ids";

    private final RestClient.Builder loadBalancedRestClientBuilder;
    private final InnerAuthProperties innerAuthProperties;
    private final Environment environment;

    public ResponseEntity<String> forward(HttpMethod method, String path,
                                          MultiValueMap<String, String> params,
                                          String jsonBody,
                                          OpenAccessTokenContext context) {
        RestClient.RequestBodyUriSpec start = loadBalancedRestClientBuilder.baseUrl(SERVICE).build().method(method);
        RestClient.RequestHeadersSpec<?> request = start.uri(uriBuilder -> {
            uriBuilder.path(path);
            if (params != null) {
                params.forEach((key, values) -> {
                    if (values != null) {
                        values.forEach(value -> uriBuilder.queryParam(key, value));
                    }
                });
            }
            return uriBuilder.build();
        }).headers(headers -> applyHeaders(headers, method, path, context));

        if (jsonBody != null) {
            request = ((RestClient.RequestBodySpec) request)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody);
        }

        try {
            return request.retrieve().toEntity(String.class);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new IllegalStateException("三个课堂服务暂时不可用", ex);
        }
    }

    private void applyHeaders(HttpHeaders headers, HttpMethod method, String path,
                              OpenAccessTokenContext context) {
        long timestamp = System.currentTimeMillis();
        String client = environment.getProperty("spring.application.name", "han-open");
        headers.set(Constants.INNER_AUTH_CLIENT_HEADER, client);
        headers.set(Constants.INNER_AUTH_TIMESTAMP_HEADER, String.valueOf(timestamp));
        headers.set(Constants.INNER_AUTH_SIGNATURE_HEADER,
                InnerAuthSignUtil.sign(client, method.name(), path, timestamp, innerAuthProperties.getSecret()));
        headers.set(OPEN_APP_ID, String.valueOf(context.appId()));
        headers.set(OPEN_TENANT_ID, String.valueOf(context.tenantId()));
        headers.set(OPEN_CLIENT_ID, context.clientId());
        headers.set(OPEN_SCHOOL_IDS, join(context.schoolIds()));
    }

    private static String join(List<Long> schoolIds) {
        return schoolIds == null ? "" : schoolIds.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
    }
}

