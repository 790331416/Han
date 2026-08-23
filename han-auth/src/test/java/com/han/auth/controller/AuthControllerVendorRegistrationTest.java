package com.han.auth.controller;

import com.han.auth.config.SecurityProperties;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import com.han.common.core.constant.Constants;
import com.han.auth.service.CaptchaSettingService;
import com.han.auth.service.IAuthService;
import com.han.auth.service.VendorRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerVendorRegistrationTest {

    @Test
    void vendorPublicKeyAlwaysIncludesEnabledFlagAndKey() {
        SecurityProperties properties = new SecurityProperties();
        properties.init();
        VendorRegistrationService vendorRegistrationService = mock(VendorRegistrationService.class);
        when(vendorRegistrationService.isInsecureHttpRegistrationAllowed()).thenReturn(true);
        AuthController controller = new AuthController(
                mock(IAuthService.class), mock(StringRedisTemplate.class), properties,
                mock(CaptchaSettingService.class), vendorRegistrationService);

        var data = controller.vendorPublicKey().getData();
        assertThat(data).containsEntry("enabled", true);
        assertThat(data.get("publicKey")).isEqualTo(properties.getPublicKey());
        assertThat(data).containsEntry("allowInsecureHttp", true);
    }

    @Test
    void vendorStatusDelegatesWithPhoneOnly() {
        SecurityProperties properties = new SecurityProperties();
        properties.init();
        VendorRegistrationService vendorRegistrationService = mock(VendorRegistrationService.class);
        OpenVendorApplicationStatusVO status = new OpenVendorApplicationStatusVO();
        status.setStatus(1);
        when(vendorRegistrationService.queryStatus("13800000000")).thenReturn(status);
        AuthController controller = new AuthController(
                mock(IAuthService.class), mock(StringRedisTemplate.class), properties,
                mock(CaptchaSettingService.class), vendorRegistrationService);

        var response = controller.vendorStatus("13800000000");

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).isSameAs(status);
    }
}
