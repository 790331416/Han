package com.han.gateway.filter;

import com.han.common.core.util.ClassroomTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 智慧校园契约兼容目录的入口鉴权。
 *
 * <p>这条前缀背后挂的是全量教师、学生、组织、设备目录，<b>不是免认证区</b>。
 * 它只是换了一种认证方式：调用方是旧 api 与旧前端，手上没有 Han 登录态，
 * 拿的是 Han 自己签发的三课堂兼容凭证，所以这里校验兼容凭证而不是 Han Token，
 * 校验不过一样拒绝。{@code AuthFilter} 对本前缀跳过 Han Token 校验，正是因为有本过滤器兜底。
 *
 * <p>{@link #ANONYMOUS_PATHS} 是唯一的例外集合，逐条列举、不使用前缀通配，
 * 每条都有必须匿名的理由；新增条目前请先确认它在拿到凭证之前确实会被调用。
 */
@Component
public class LegacyCompatAuthFilter implements GlobalFilter, Ordered {

    /** 兼容目录挂载前缀，旧 api 的 api.url 与 nginx 的 /api 反代都指到这里。 */
    static final String PREFIX = "/sdfz-compat/";

    private static final String TOKEN_HEADER = "access-token";

    /**
     * 图形验证码：登录页在拿到任何凭证之前就要调它取图，路径末尾还带一个时间戳变量。
     *
     * <p>这是唯一需要按前缀匹配的条目，其余都是精确匹配。
     */
    private static final String CAPTCHA_PREFIX = "user/sys/randomImage/";

    /**
     * 必须匿名可达的兼容路径。
     *
     * <p>前两条是登录链路本身：调用发生在凭证存在之前，要求凭证会形成死锁。
     * 后四条本期未启用，只返回一句固定的"未启用"信封、不读任何数据；
     * 它们都长在登录页上，若返回 401，旧前端会弹「登录状态已过期」并跳转到生产域名。
     */
    private static final List<String> ANONYMOUS_PATHS = List.of(
            // 账号密码登录，前端在此之前没有任何凭证
            "user/user/login",
            // 短信验证码登录，本期未启用，返回固定信封
            "user/user/loginByCaptcha",
            // 下发短信验证码，本期未启用，返回固定信封
            "user/public/login/get-sms-code",
            // 忘记密码，附中已在环境变量里关闭，本期未启用，返回固定信封
            "user/user/user-forget-password",
            // 教育云单点登录，属于已冻结的数字校园通路，本期未启用，返回固定信封
            "partner/tPartnerUserLogin/userVoByJyyToken");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String secret;

    public LegacyCompatAuthFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${sdfz.classroom-gateway.token-secret:}") String secret) {
        this.redisTemplate = redisTemplate;
        this.secret = secret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(PREFIX)) {
            return chain.filter(exchange);
        }
        // 未配置签名密钥时无法校验凭证，整条前缀关闭而不是放行。
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            return error(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Classroom compatibility layer is not configured");
        }
        if (isAnonymous(path.substring(PREFIX.length()))) {
            return chain.filter(exchange);
        }

        String token = token(exchange.getRequest());
        if (token == null || token.isBlank()) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Missing classroom token");
        }
        ClassroomTokenCodec.VerifiedToken verified = verify(token);
        if (verified == null) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Invalid classroom token");
        }
        return redisTemplate.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + verified.tokenId())
                .defaultIfEmpty(false)
                .flatMap(active -> Boolean.TRUE.equals(active)
                        ? chain.filter(exchange)
                        : error(exchange, HttpStatus.UNAUTHORIZED, "Classroom session is revoked or expired"));
    }

    static boolean isAnonymous(String relativePath) {
        return relativePath.startsWith(CAPTCHA_PREFIX) || ANONYMOUS_PATHS.contains(relativePath);
    }

    private ClassroomTokenCodec.VerifiedToken verify(String token) {
        try {
            return ClassroomTokenCodec.verify(token, secret, Instant.now().getEpochSecond());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String token(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(TOKEN_HEADER);
        return token != null && !token.isBlank()
                ? token : request.getQueryParams().getFirst(TOKEN_HEADER);
    }

    /** 响应体必须带 {@code code}：旧 api 是 {@code 2000 == code} 对 Integer 拆箱，缺字段会 NPE。 */
    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"code\":" + status.value() + ",\"msg\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -120;
    }
}
