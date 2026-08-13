package com.han.common.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnumFallbackTest {

    @Test
    @DisplayName("ClientType 对未知 code 不再兜底成管理端 PC")
    void clientTypeNoLongerFailsOpen() {
        // code 通常来自客户端可控的 X-Client-Type，兜底成 PC（后台管理端）是 fail-open
        assertNull(ClientType.fromCode("unknown"));
        assertNull(ClientType.fromCode(null));
        assertEquals(ClientType.APP, ClientType.fromCode("app"));
        assertEquals(ClientType.APP, ClientType.fromCode("unknown", ClientType.APP));
    }

    @Test
    @DisplayName("DeployTier 显式匹配 medium，未知值仍可启动但会告警")
    void deployTierMatchesAllTiers() {
        assertEquals(DeployTier.SMALL, DeployTier.from("small"));
        assertEquals(DeployTier.MEDIUM, DeployTier.from("MEDIUM"));
        assertEquals(DeployTier.FULL, DeployTier.from(" full "));
        assertEquals(DeployTier.DEFAULT, DeployTier.from("smal"));
        assertEquals(DeployTier.DEFAULT, DeployTier.from(null));
    }
}
