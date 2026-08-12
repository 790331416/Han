package com.han.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HanIpUtilTest {

    @Test
    @DisplayName("RFC1918 私网与回环判定为内网")
    void recognizesPrivateRanges() {
        assertTrue(HanIpUtil.isInternalIp("127.0.0.1"));
        assertTrue(HanIpUtil.isInternalIp("10.0.0.1"));
        assertTrue(HanIpUtil.isInternalIp("192.168.1.1"));
        assertTrue(HanIpUtil.isInternalIp("172.16.0.1"));
        assertTrue(HanIpUtil.isInternalIp("172.31.255.255"));
    }

    @Test
    @DisplayName("补齐链路本地与运营商级 NAT 网段")
    void recognizesLinkLocalAndCgnat() {
        assertTrue(HanIpUtil.isInternalIp("169.254.1.1"));
        assertTrue(HanIpUtil.isInternalIp("100.64.0.1"));
        assertTrue(HanIpUtil.isInternalIp("100.127.255.255"));
        assertFalse(HanIpUtil.isInternalIp("100.128.0.1"));
    }

    @Test
    @DisplayName("公网地址与 172 段之外的地址不算内网")
    void rejectsPublicAddresses() {
        assertFalse(HanIpUtil.isInternalIp("8.8.8.8"));
        assertFalse(HanIpUtil.isInternalIp("172.15.0.1"));
        assertFalse(HanIpUtil.isInternalIp("172.32.0.1"));
    }

    @Test
    @DisplayName("IPv6 回环、唯一本地与链路本地判定为内网")
    void recognizesIpv6InternalRanges() {
        assertTrue(HanIpUtil.isInternalIp("::1"));
        assertTrue(HanIpUtil.isInternalIp("0:0:0:0:0:0:0:1"));
        assertTrue(HanIpUtil.isInternalIp("fd00::1"));
        assertTrue(HanIpUtil.isInternalIp("FE80::1"));
        assertFalse(HanIpUtil.isInternalIp("2001:4860:4860::8888"));
    }

    @Test
    @DisplayName("非 IP 字面量直接返回 false，不触发 DNS 解析")
    void doesNotResolveHostNames() {
        // 入参来自 X-Forwarded-For 等客户端可控请求头，走 InetAddress.getByName 会被诱导发起域名解析
        assertFalse(HanIpUtil.isInternalIp("localhost"));
        assertFalse(HanIpUtil.isInternalIp("evil.example.com"));
        assertFalse(HanIpUtil.isInternalIp("999.1.1.1"));
        assertFalse(HanIpUtil.isInternalIp(""));
        assertFalse(HanIpUtil.isInternalIp(null));
    }
}
