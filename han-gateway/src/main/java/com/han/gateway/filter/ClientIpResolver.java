package com.han.gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 客户端 IP 解析
 *
 * <p>限流与访问日志此前各有一份实现，且都取 {@code X-Forwarded-For} 的<b>最左</b>值。
 * nginx 的 {@code proxy_add_x_forwarded_for} 是追加语义——客户端自带的 XFF 会留在最前面，
 * 真实来源 IP 被拼在末尾。取最左等于直接采信客户端写入的值：每个请求换一个随机 XFF
 * 就能让限流 key 全部落空，或把 XFF 固定为受害者 IP 定向嫁祸。
 *
 * <p>这里改取<b>最右</b>一跳，即离网关最近的那个转发方写入的地址。在「客户端 → nginx → 网关」
 * 这一部署形态下即为真实客户端 IP。
 *
 * <p><b>残留前提</b>：本方法只能做到「不采信客户端注入的左侧值」。要完全可信还需要
 * 收敛 {@code spring.cloud.gateway.server.webflux.trusted-proxies}（当前为 {@code .*}）
 * 并把 nginx 改为 {@code proxy_set_header X-Forwarded-For $remote_addr}（覆盖而非追加），
 * 这两处分别属于部署配置与 han-ui/nginx.conf，不在本模块内。
 */
final class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    static String resolve(ServerHttpRequest request) {
        String forwarded = lastHop(request.getHeaders().getFirst("X-Forwarded-For"));
        if (isUsable(forwarded)) {
            return forwarded;
        }

        String realIp = trimToNull(request.getHeaders().getFirst("X-Real-IP"));
        if (isUsable(realIp)) {
            return realIp;
        }

        return request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : UNKNOWN;
    }

    /**
     * 取 XFF 链路中最靠近本网关的一跳。
     */
    private static String lastHop(String forwardedFor) {
        String value = trimToNull(forwardedFor);
        if (value == null) {
            return null;
        }
        int lastComma = value.lastIndexOf(',');
        return lastComma >= 0 ? trimToNull(value.substring(lastComma + 1)) : value;
    }

    private static boolean isUsable(String ip) {
        return ip != null && !UNKNOWN.equalsIgnoreCase(ip);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
