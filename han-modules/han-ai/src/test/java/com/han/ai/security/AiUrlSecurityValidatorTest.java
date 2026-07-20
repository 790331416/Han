package com.han.ai.security;

import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSRF 公共校验器测试（G1-12）：内网/环回/链路本地/元数据地址默认拒绝，
 * 白名单（精确主机、通配后缀、IP、CIDR）可放行，开关行为可控。
 * <p>
 * 用例全部使用 IP 字面量或本机可解析主机名（localhost），不依赖外部 DNS。
 */
class AiUrlSecurityValidatorTest {

    private AiUrlSecurityValidator defaultValidator() {
        return new AiUrlSecurityValidator(true, false, "");
    }

    // ---------- 默认拒绝面 ----------

    @Test
    void rejectsLoopbackIpv4() {
        assertThatThrownBy(() -> defaultValidator().validate("http://127.0.0.1:65535/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("环回")
                .hasMessageContaining("MCP服务");
    }

    @Test
    void rejectsLocalhostHostname() {
        assertThatThrownBy(() -> defaultValidator().validate("http://localhost:9000/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被安全策略拒绝");
    }

    @Test
    void rejectsPrivateNetworks() {
        assertThatThrownBy(() -> defaultValidator().validate("http://10.0.0.8/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("内网");
        assertThatThrownBy(() -> defaultValidator().validate("http://172.16.5.4:8080/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("内网");
        assertThatThrownBy(() -> defaultValidator().validate("http://192.168.1.10/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("内网");
    }

    @Test
    void rejectsLinkLocalAndCloudMetadata() {
        assertThatThrownBy(() -> defaultValidator().validate("http://169.254.169.254/latest/meta-data", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("链路本地");
    }

    @Test
    void rejectsCarrierGradeNat() {
        assertThatThrownBy(() -> defaultValidator().validate("http://100.64.0.1/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("运营商级内网");
    }

    @Test
    void rejectsAnyLocalAddress() {
        assertThatThrownBy(() -> defaultValidator().validate("http://0.0.0.0:8080/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("通配");
    }

    @Test
    void rejectsIpv6LoopbackAndUniqueLocal() {
        assertThatThrownBy(() -> defaultValidator().validate("http://[::1]:9000/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> defaultValidator().validate("http://[fd00::1]/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IPv6 ULA");
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> defaultValidator().validate("ftp://8.8.8.8/file", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("http/https");
    }

    @Test
    void rejectsMalformedOrBlankUrl() {
        assertThatThrownBy(() -> defaultValidator().validate("http://[bad-url", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不合法");
        assertThatThrownBy(() -> defaultValidator().validate("   ", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> defaultValidator().validate("not-a-url", "MCP服务"))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 默认放行面 ----------

    @Test
    void allowsPublicAddress() {
        assertThatCode(() -> defaultValidator().validate("https://8.8.8.8/mcp", "MCP服务"))
                .doesNotThrowAnyException();
    }

    // ---------- 白名单 ----------

    @Test
    void allowsExactHostInAllowlistWithoutResolving() {
        AiUrlSecurityValidator validator =
                new AiUrlSecurityValidator(true, false, "Internal.Mcp.Corp");
        assertThatCode(() -> validator.validate("http://internal.mcp.corp:3000/mcp", "MCP服务"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsWildcardHostSuffixInAllowlist() {
        AiUrlSecurityValidator validator =
                new AiUrlSecurityValidator(true, false, "*.trusted.corp");
        assertThatCode(() -> validator.validate("http://mcp.trusted.corp/mcp", "MCP服务"))
                .doesNotThrowAnyException();
        // 通配后缀不放行裸域之外的其他主机
        assertThatThrownBy(() -> validator.validate("http://192.168.1.10/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsIpLiteralInAllowlist() {
        AiUrlSecurityValidator validator =
                new AiUrlSecurityValidator(true, false, "127.0.0.1");
        assertThatCode(() -> validator.validate("http://127.0.0.1:65535/mcp", "MCP服务"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsCidrRangeInAllowlistAndKeepsRejectingOutside() {
        AiUrlSecurityValidator validator =
                new AiUrlSecurityValidator(true, false, "192.168.10.0/24");
        assertThatCode(() -> validator.validate("http://192.168.10.5:9000/mcp", "MCP服务"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("http://192.168.11.5:9000/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void supportsMultipleAllowlistEntries() {
        AiUrlSecurityValidator validator =
                new AiUrlSecurityValidator(true, false, " 10.10.0.0/16 , *.trusted.corp , 127.0.0.1 ");
        assertThatCode(() -> {
            validator.validate("http://10.10.3.7/mcp", "MCP服务");
            validator.validate("http://tool.trusted.corp/mcp", "MCP服务");
            validator.validate("http://127.0.0.1:8000/mcp", "MCP服务");
        }).doesNotThrowAnyException();
    }

    // ---------- 开关 ----------

    @Test
    void allowPrivateNetworkSwitchBypassesAddressChecksButKeepsFormatChecks() {
        AiUrlSecurityValidator validator = new AiUrlSecurityValidator(true, true, "");
        assertThatCode(() -> {
            validator.validate("http://127.0.0.1:65535/mcp", "MCP服务");
            validator.validate("http://192.168.1.10/mcp", "MCP服务");
        }).doesNotThrowAnyException();
        // 协议与格式校验仍然生效
        assertThatThrownBy(() -> validator.validate("ftp://127.0.0.1/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void disabledSwitchSkipsAllValidation() {
        AiUrlSecurityValidator validator = new AiUrlSecurityValidator(false, false, "");
        assertThatCode(() -> validator.validate("ftp://127.0.0.1/whatever", "MCP服务"))
                .doesNotThrowAnyException();
    }

    @Test
    void errorMessageMentionsAllowlistConfigKey() {
        assertThatThrownBy(() -> defaultValidator().validate("http://10.0.0.8/mcp", "MCP服务"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("han.ai.ssrf.allowed-hosts"));
    }
}
