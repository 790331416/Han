package com.han.ai.security;

import com.han.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 外联 URL 公共安全校验器（SSRF 防护，G1-12）。
 * <p>
 * 面向所有「用户可配置外联地址」的场景统一收口：MCP server URL（保存与连接两条路径）、
 * 后续 P2 的编排 HTTP 节点与「接口变工具」直接复用本组件，避免各处重复实现。
 * <p>
 * 默认策略（fail-closed）：仅允许 http/https；环回、内网（10/8、172.16/12、192.168/16）、
 * 链路本地（169.254/16，含云厂商元数据 169.254.169.254）、运营商级内网（100.64/10）、
 * 通配、组播及 IPv6 等价段（::1、fe80::/10、fc00::/7）一律拒绝；主机名无法解析视为不安全。
 * <p>
 * 配置项（默认无需任何配置即安全）：
 * <ul>
 *   <li>{@code han.ai.ssrf.enabled}：总开关，默认 true；false 时完全跳过校验。</li>
 *   <li>{@code han.ai.ssrf.allow-private-network}：默认 false；true 时保留 URL 格式与协议校验，
 *       但放行内网/环回地址（本地开发、e2e 回归环境使用）。</li>
 *   <li>{@code han.ai.ssrf.allowed-hosts}：白名单，逗号分隔，支持四种条目：
 *       精确主机名（不区分大小写）、通配后缀（{@code *.internal.example.com}）、
 *       IP 字面量、CIDR 网段（{@code 192.168.10.0/24}）。命中白名单的地址直接放行。</li>
 * </ul>
 */
@Slf4j
@Component
public class AiUrlSecurityValidator {

    private final boolean enabled;
    private final boolean allowPrivateNetwork;
    private final List<String> allowedHosts;

    public AiUrlSecurityValidator(
            @Value("${han.ai.ssrf.enabled:true}") boolean enabled,
            @Value("${han.ai.ssrf.allow-private-network:false}") boolean allowPrivateNetwork,
            @Value("${han.ai.ssrf.allowed-hosts:}") String allowedHosts) {
        this.enabled = enabled;
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.allowedHosts = parseAllowedHosts(allowedHosts);
    }

    /**
     * 校验外联 URL 是否允许访问；不允许时抛出带场景与原因的业务异常。
     *
     * @param url   待校验的完整 URL
     * @param scene 场景名（如 "MCP服务"），用于拼接可读错误文案
     */
    public void validate(String url, String scene) {
        if (!enabled) {
            return;
        }
        String label = StringUtils.hasText(scene) ? scene : "外联";
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(label + "URL不能为空");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw new BusinessException(label + "URL格式不合法：" + url.trim());
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException(label + "URL仅支持 http/https 协议");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(label + "URL缺少有效主机名");
        }
        if (matchesAllowedHostName(host)) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new BusinessException(label + "URL主机名无法解析：" + host);
        }
        for (InetAddress address : addresses) {
            if (matchesAllowedAddress(address)) {
                continue;
            }
            String reason = blockedReason(address);
            if (reason == null) {
                continue;
            }
            if (allowPrivateNetwork) {
                log.debug("SSRF check bypassed by allow-private-network, host={}, address={}, reason={}",
                        host, address.getHostAddress(), reason);
                continue;
            }
            throw new BusinessException(label + "URL指向" + reason + "地址（" + host
                    + "），已被安全策略拒绝；如确需放行请配置 han.ai.ssrf.allowed-hosts 白名单");
        }
    }

    /**
     * 判定地址是否属于默认拒绝的网段；安全地址返回 null。
     */
    private String blockedReason(InetAddress address) {
        if (address.isAnyLocalAddress()) {
            return "通配";
        }
        if (address.isLoopbackAddress()) {
            return "环回";
        }
        if (address.isLinkLocalAddress()) {
            return "链路本地（含云元数据）";
        }
        if (address.isSiteLocalAddress()) {
            return "内网";
        }
        if (address.isMulticastAddress()) {
            return "组播";
        }
        byte[] bytes = address.getAddress();
        // IPv6 唯一本地地址 fc00::/7（isSiteLocalAddress 仅覆盖已废弃的 fec0::/10）
        if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
            return "内网（IPv6 ULA）";
        }
        // IPv4 运营商级 NAT 100.64.0.0/10
        if (bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xFF) >= 64 && (bytes[1] & 0xFF) <= 127) {
            return "运营商级内网";
        }
        return null;
    }

    /**
     * 主机名维度白名单匹配（精确 / *.通配后缀），命中则不再解析地址。
     */
    private boolean matchesAllowedHostName(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String entry : allowedHosts) {
            if (entry.startsWith("*.")) {
                String suffix = entry.substring(1);
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                    return true;
                }
            } else if (normalized.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IP 维度白名单匹配（IP 字面量精确匹配 / CIDR 网段包含）。
     */
    private boolean matchesAllowedAddress(InetAddress address) {
        for (String entry : allowedHosts) {
            if (entry.contains("/")) {
                if (matchesCidr(entry, address)) {
                    return true;
                }
            } else if (entry.equals(address.getHostAddress().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCidr(String cidr, InetAddress address) {
        int slash = cidr.indexOf('/');
        String networkPart = cidr.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException ex) {
            return false;
        }
        InetAddress network;
        try {
            network = InetAddress.getByName(networkPart);
        } catch (UnknownHostException ex) {
            log.warn("SSRF allowed-hosts 白名单 CIDR 条目不合法，已忽略：{}", cidr);
            return false;
        }
        byte[] networkBytes = network.getAddress();
        byte[] addressBytes = address.getAddress();
        if (networkBytes.length != addressBytes.length
                || prefix < 0 || prefix > networkBytes.length * 8) {
            return false;
        }
        int fullBytes = prefix / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (networkBytes[i] != addressBytes[i]) {
                return false;
            }
        }
        int remainderBits = prefix % 8;
        if (remainderBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainderBits);
        return (networkBytes[fullBytes] & mask) == (addressBytes[fullBytes] & mask);
    }

    private List<String> parseAllowedHosts(String raw) {
        List<String> entries = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return entries;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(trimmed)) {
                entries.add(trimmed);
            }
        }
        return entries;
    }
}
