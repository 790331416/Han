package com.han.gateway.filter;

/**
 * 网关全局过滤器执行顺序
 *
 * <p>集中定义，保证互不相同：Spring 对 order 相同的 Bean 只保证稳定排序、不保证具体先后，
 * 此前 {@code RequestLogFilter} 与 {@code ShareRateLimitFilter} 同为 -200，相对顺序实际未定义。
 */
public final class GatewayFilterOrders {

    /** 全局 IP 限流：最先执行，挡住洪峰 */
    public static final int RATE_LIMIT = -300;

    /** 分享接口按 shareKey 限流 */
    public static final int SHARE_RATE_LIMIT = -250;

    /** 请求日志 */
    public static final int REQUEST_LOG = -200;

    /** 认证与身份头下发 */
    public static final int AUTH = -100;

    private GatewayFilterOrders() {
    }
}
