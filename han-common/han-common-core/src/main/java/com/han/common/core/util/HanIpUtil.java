package com.han.common.core.util;

import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Locale;

/**
 * IP 归属地工具类 — 基于 ip2region 离线库
 *
 * <p>使用全内存缓存模式（content-based），启动时一次性加载 xdb 到内存，
 * 后续查询无磁盘 IO，线程安全，单次查询 ~10μs。
 *
 * <p>xdb 文件放置位置：classpath:ip2region.xdb
 * 如文件不存在，自动降级为内网/未知判断。
 */
public final class HanIpUtil {

    private static final Logger log = LoggerFactory.getLogger(HanIpUtil.class);

    private static Searcher searcher;
    private static boolean initialized = false;

    static {
        try (InputStream is = HanIpUtil.class.getClassLoader().getResourceAsStream("ip2region.xdb")) {
            if (is != null) {
                byte[] cBuff = is.readAllBytes();
                searcher = Searcher.newWithBuffer(cBuff);
                initialized = true;
                log.info("ip2region 离线库加载成功，大小: {} KB", cBuff.length / 1024);
            } else {
                log.warn("ip2region.xdb 未找到，IP 归属地解析将降级为简单判断。"
                        + "请将 ip2region.xdb 放置到 classpath 下（如 han-auth/src/main/resources/）");
            }
        } catch (Exception e) {
            log.error("ip2region 离线库加载失败", e);
        }
    }

    private HanIpUtil() {}

    /**
     * 解析 IP 归属地
     *
     * @param ip IPv4 地址
     * @return 格式化的归属地，如 "中国|北京|北京市" → "北京市"，内网 IP → "内网IP"
     */
    public static String getLocation(String ip) {
        if (ip == null || ip.isBlank()) return "未知";

        // 内网 IP 快速判断
        if (isInternalIp(ip)) return "内网IP";

        // 使用 ip2region 查询
        if (initialized && searcher != null) {
            try {
                String region = searcher.search(ip);
                return formatRegion(region);
            } catch (Exception e) {
                log.debug("IP[{}]归属地查询失败: {}", ip, e.getMessage());
            }
        }

        return "未知";
    }

    /**
     * 判断是否为内网 IP
     * <p>覆盖 RFC1918 私网、回环、链路本地（169.254/16）以及运营商级 NAT（100.64/10，云环境常见）。
     */
    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String value = ip.trim();
        int[] v4 = parseIpv4(value);
        if (v4 != null) {
            return v4[0] == 0
                    || v4[0] == 10
                    || v4[0] == 127
                    || (v4[0] == 100 && v4[1] >= 64 && v4[1] <= 127)
                    || (v4[0] == 169 && v4[1] == 254)
                    || (v4[0] == 172 && v4[1] >= 16 && v4[1] <= 31)
                    || (v4[0] == 192 && v4[1] == 168);
        }
        return isInternalIpv6(value);
    }

    /**
     * 只解析点分十进制字面量，不做任何 DNS 解析 —— 入参来自 {@code X-Forwarded-For}
     * 等客户端可控请求头，走 {@code InetAddress.getByName} 会被诱导发起域名解析。
     *
     * @return 四段无符号值；不是合法 IPv4 字面量时返回 {@code null}
     */
    private static int[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] segments = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int segment = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                segment = segment * 10 + (c - '0');
            }
            if (segment > 255) {
                return null;
            }
            segments[i] = segment;
        }
        return segments;
    }

    /**
     * IPv6 内网判断：回环 {@code ::1}、唯一本地地址 {@code fc00::/7}、链路本地 {@code fe80::/10}。
     */
    private static boolean isInternalIpv6(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.indexOf(':') < 0) {
            return false;
        }
        if ("::1".equals(lower) || "0:0:0:0:0:0:0:1".equals(lower) || "::".equals(lower)) {
            return true;
        }
        if (lower.startsWith("fc") || lower.startsWith("fd")) {
            return true;
        }
        return lower.startsWith("fe8") || lower.startsWith("fe9")
                || lower.startsWith("fea") || lower.startsWith("feb");
    }

    /**
     * 格式化 ip2region 返回的区域字符串
     * <p>原始格式: "国家|区域|省份|城市|ISP"，字段为 "0" 表示无数据
     * <p>转换为人类可读格式：优先显示 城市，其次 省份，最后 国家
     */
    private static String formatRegion(String region) {
        if (region == null || region.isBlank()) return "未知";

        String[] parts = region.split("\\|");
        if (parts.length < 5) return region;

        String country = "0".equals(parts[0]) ? "" : parts[0];
        String province = "0".equals(parts[2]) ? "" : parts[2];
        String city = "0".equals(parts[3]) ? "" : parts[3];
        String isp = "0".equals(parts[4]) ? "" : parts[4];

        StringBuilder sb = new StringBuilder();
        if (!city.isEmpty() && !city.equals(province)) {
            sb.append(province).append(city);
        } else if (!province.isEmpty()) {
            sb.append(province);
        } else if (!country.isEmpty()) {
            sb.append(country);
        }

        if (!isp.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(isp);
        }

        return sb.isEmpty() ? "未知" : sb.toString();
    }
}
