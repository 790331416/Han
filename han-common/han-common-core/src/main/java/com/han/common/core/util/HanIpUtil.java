package com.han.common.core.util;

import org.lionsoul.ip2region.xdb.Searcher;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger log = Logger.getLogger(HanIpUtil.class.getName());

    private static Searcher searcher;
    private static boolean initialized = false;

    static {
        try {
            InputStream is = HanIpUtil.class.getClassLoader().getResourceAsStream("ip2region.xdb");
            if (is != null) {
                byte[] cBuff = is.readAllBytes();
                is.close();
                searcher = Searcher.newWithBuffer(cBuff);
                initialized = true;
                log.info("ip2region 离线库加载成功，大小: " + cBuff.length / 1024 + " KB");
            } else {
                log.warning("ip2region.xdb 未找到，IP 归属地解析将降级为简单判断。" +
                        "请将 ip2region.xdb 放置到 classpath 下（如 han-auth/src/main/resources/）");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "ip2region 离线库加载失败", e);
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
                log.fine("IP[" + ip + "]归属地查询失败: " + e.getMessage());
            }
        }

        return "未知";
    }

    /**
     * 判断是否为内网 IP
     */
    public static boolean isInternalIp(String ip) {
        if (ip == null) return false;
        return "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("0:")
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || (ip.startsWith("172.") && isIn172Range(ip));
    }

    private static boolean isIn172Range(String ip) {
        try {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
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
