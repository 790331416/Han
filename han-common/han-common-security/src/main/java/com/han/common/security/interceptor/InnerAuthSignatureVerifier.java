package com.han.common.security.interceptor;

import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.HanSecureUtil;
import com.han.common.core.util.InnerAuthSignUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 服务间内部调用签名校验
 *
 * <p>把签名校验从 {@link InnerAuthInterceptor} 中抽出来共享，因为
 * {@code HeaderAuthenticationFilter} 需要在 Servlet Filter 阶段就知道「这次调用是不是可信的内部调用」，
 * 而 HandlerInterceptor 要等到路由到 HandlerMethod 之后才执行，来不及。两处必须用同一套判定，否则会漂移。
 *
 * <p><b>签名版本：</b>
 * <ul>
 *   <li><b>v1</b>（无 {@value #SIGN_VERSION_HEADER} 头）：载荷为
 *       {@code client \n METHOD \n path \n timestamp \n secret}，不覆盖身份头。
 *       此时即使签名有效，身份头也<b>不可信</b>——持有一次抓包结果的人可以在时间窗内改写身份重放。</li>
 *   <li><b>v2</b>（{@value #SIGN_VERSION_HEADER} 为 {@value #SIGN_VERSION_V2}）：载荷追加
 *       {@code userId \n username \n tenantId}，身份头被签名绑定，可作为调用方身份采信。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InnerAuthSignatureVerifier {

    /** 内部调用签名版本头。核心签名侧实现后建议上收到 {@code Constants} 与调用方共享。 */
    public static final String SIGN_VERSION_HEADER = "X-Inner-Sign-Version";

    /** 身份头已纳入签名载荷的版本号 */
    public static final String SIGN_VERSION_V2 = "v2";

    private final InnerAuthProperties properties;

    /**
     * 校验请求上的内部调用签名。
     *
     * @return 校验结论，{@link Result#OK} 表示签名有效
     */
    public Result verify(HttpServletRequest request) {
        String client = request.getHeader(Constants.INNER_AUTH_CLIENT_HEADER);
        String timestampHeader = request.getHeader(Constants.INNER_AUTH_TIMESTAMP_HEADER);
        String signature = request.getHeader(Constants.INNER_AUTH_SIGNATURE_HEADER);

        if (isBlank(client) || isBlank(timestampHeader) || isBlank(signature)) {
            return Result.MISSING_HEADER;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException ex) {
            return Result.BAD_TIMESTAMP;
        }

        long maxSkewMillis = properties.getClockSkewSeconds() * 1000L;
        if (Math.abs(System.currentTimeMillis() - timestamp) > maxSkewMillis) {
            return Result.EXPIRED;
        }

        String expected = expectedSignature(request, client, timestamp);
        if (!InnerAuthSignUtil.matches(signature, expected)) {
            log.warn("内部鉴权签名校验失败: client={}, path={}", client, request.getRequestURI());
            return Result.INVALID_SIGNATURE;
        }

        return Result.OK;
    }

    /**
     * 判断本次请求携带的身份头是否已被签名绑定，可作为登录态来源。
     *
     * <p>要求同时满足：内部鉴权处于启用状态、签名版本为 v2、且 v2 签名校验通过。
     * 任何一项不满足都返回 false（fail-closed），调用方不得退化为无条件信任身份头。
     */
    public boolean isIdentityTrusted(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (!isIdentitySigned(request)) {
            return false;
        }
        return verify(request) == Result.OK;
    }

    /** 请求是否声明了「身份头已纳入签名」 */
    public boolean isIdentitySigned(HttpServletRequest request) {
        return SIGN_VERSION_V2.equalsIgnoreCase(request.getHeader(SIGN_VERSION_HEADER));
    }

    /**
     * 计算期望签名。v2 在 v1 载荷之后追加身份三元组，使身份头不可被中途改写。
     */
    private String expectedSignature(HttpServletRequest request, String client, long timestamp) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (!isIdentitySigned(request)) {
            return InnerAuthSignUtil.sign(client, method, path, timestamp, properties.getSecret());
        }

        String payload = String.join("\n",
                safe(client),
                safe(method).toUpperCase(),
                safe(path),
                String.valueOf(timestamp),
                safe(request.getHeader(Constants.USER_ID_HEADER)),
                safe(request.getHeader(Constants.USERNAME_HEADER)),
                safe(request.getHeader(Constants.TENANT_ID_HEADER)),
                safe(properties.getSecret())
        );
        return HanSecureUtil.sha256(payload);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 校验结论
     */
    public enum Result {
        OK,
        MISSING_HEADER,
        BAD_TIMESTAMP,
        EXPIRED,
        INVALID_SIGNATURE
    }
}
