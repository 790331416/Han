package com.han.auth.service;

import com.han.api.open.OpenServiceClient;
import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.OpenVendorAccountCreateDTO;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.VendorPublicRegisterDTO;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanSecureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VendorRegistrationServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final SecurityProperties securityProperties = new SecurityProperties();
    private final CaptchaSettingService captchaSettingService = mock(CaptchaSettingService.class);
    private final SystemServiceClient systemServiceClient = mock(SystemServiceClient.class);
    private final OpenServiceClient openServiceClient = mock(OpenServiceClient.class);
    private VendorRegistrationService service;

    @BeforeEach
    void setUp() {
        securityProperties.init();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(false);
        service = new VendorRegistrationService(redisTemplate, securityProperties,
                captchaSettingService, systemServiceClient, openServiceClient);
    }

    @Test
    void decryptsRegistrationPasswordBeforeCreatingDisabledAccount() {
        when(systemServiceClient.createOpenVendorAccount(any())).thenReturn(R.ok(42L));
        when(openServiceClient.createPortalApplication(any())).thenReturn(R.ok("application-1"));

        VendorPublicRegisterDTO dto = request();
        dto.setEncryptedPassword(HanSecureUtil.rsaEncrypt("Strong@123", securityProperties.getPublicKey()));

        assertThat(service.register(dto)).isEqualTo("application-1");
        var captor = org.mockito.ArgumentCaptor.forClass(OpenVendorAccountCreateDTO.class);
        verify(systemServiceClient).createOpenVendorAccount(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getPassword()).isEqualTo("Strong@123");
    }

    @Test
    void acceptsRegistrationWithoutEnterpriseDetails() {
        when(systemServiceClient.createOpenVendorAccount(any())).thenReturn(R.ok(42L));
        when(openServiceClient.createPortalApplication(any())).thenReturn(R.ok("application-1"));
        VendorPublicRegisterDTO dto = request();
        dto.setName(null);
        dto.setQualificationNo(null);
        dto.setContactName(null);
        dto.setContactPhone(null);
        dto.setNickname(null);
        dto.setEncryptedPassword(HanSecureUtil.rsaEncrypt("Strong@123", securityProperties.getPublicKey()));

        assertThat(service.register(dto)).isEqualTo("application-1");

        var application = org.mockito.ArgumentCaptor.forClass(OpenVendorApplicationCreateDTO.class);
        verify(openServiceClient).createPortalApplication(application.capture());
        assertThat(application.getValue().getName()).isEqualTo("个人开发者-vendor_user");
        assertThat(application.getValue().getQualificationNo()).isNull();
        assertThat(application.getValue().getContactName()).isEqualTo("vendor_user");
        assertThat(application.getValue().getContactPhone()).isEqualTo("13900000000");
    }

    @Test
    void rejectsPlaintextRegistrationPassword() {
        VendorPublicRegisterDTO dto = request();
        dto.setEncryptedPassword("Strong@123");

        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码必须使用注册公钥加密");
        verify(systemServiceClient, never()).createOpenVendorAccount(any());
    }

    @Test
    void allowsPlainPasswordOnlyWhenHttpTestCompatibilityIsEnabled() {
        when(systemServiceClient.getConfigValue(VendorRegistrationService.INSECURE_HTTP_REGISTRATION_KEY))
                .thenReturn(R.ok("true"));
        when(systemServiceClient.createOpenVendorAccount(any())).thenReturn(R.ok(42L));
        when(openServiceClient.createPortalApplication(any())).thenReturn(R.ok("application-1"));
        VendorPublicRegisterDTO dto = request();
        dto.setPlainPassword("Strong@123");

        assertThat(service.register(dto)).isEqualTo("application-1");
        var captor = org.mockito.ArgumentCaptor.forClass(OpenVendorAccountCreateDTO.class);
        verify(systemServiceClient).createOpenVendorAccount(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("Strong@123");
    }

    @Test
    void rejectsPlainPasswordWhenHttpTestCompatibilityIsDisabled() {
        when(systemServiceClient.getConfigValue(VendorRegistrationService.INSECURE_HTTP_REGISTRATION_KEY))
                .thenReturn(R.ok("false"));
        VendorPublicRegisterDTO dto = request();
        dto.setPlainPassword("Strong@123");

        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前环境未开启HTTP测试兼容，密码必须使用注册公钥加密");
        verify(systemServiceClient, never()).createOpenVendorAccount(any());
    }

    @Test
    void rejectsCaptchaBeforeCreatingAccount() {
        when(captchaSettingService.isCaptchaEnabled()).thenReturn(true);
        when(valueOperations.get(any())).thenReturn(null);
        VendorPublicRegisterDTO dto = request();
        dto.setEncryptedPassword(HanSecureUtil.rsaEncrypt("Strong@123", securityProperties.getPublicKey()));

        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误或已过期");
        verify(systemServiceClient, never()).createOpenVendorAccount(any());
    }


    @Test
    void compensatesDisabledAccountWhenOpenApplicationFails() {
        when(systemServiceClient.createOpenVendorAccount(any())).thenReturn(R.ok(42L));
        when(openServiceClient.createPortalApplication(any())).thenReturn(R.<String>fail("open down"));

        VendorPublicRegisterDTO dto = request();
        dto.setEncryptedPassword(HanSecureUtil.rsaEncrypt("Strong@123", securityProperties.getPublicKey()));

        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("厂商申请创建失败，请稍后重试");
        verify(systemServiceClient).compensateOpenVendorAccount(42L);
    }

    @Test
    void doesNotCompensateWhenSystemResponseIsUnknown() {
        when(systemServiceClient.createOpenVendorAccount(any())).thenThrow(new IllegalStateException("timeout"));
        VendorPublicRegisterDTO dto = request();
        dto.setEncryptedPassword(HanSecureUtil.rsaEncrypt("Strong@123", securityProperties.getPublicKey()));

        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("提交状态未确认，请使用原账号重试");
        verify(systemServiceClient, never()).compensateOpenVendorAccount(any());
        verify(openServiceClient, never()).createPortalApplication(any());
    }

    @Test
    void doesNotCompensateWhenOpenResponseIsUnknownAndRetryCanRecover() {
        when(systemServiceClient.createOpenVendorAccount(any())).thenReturn(R.ok(42L));
        when(openServiceClient.createPortalApplication(any()))
                .thenThrow(new IllegalStateException("timeout"))
                .thenReturn(R.ok("application-recovered"));
        VendorPublicRegisterDTO dto = request();
        dto.setEncryptedPassword(HanSecureUtil.rsaEncrypt("Strong@123", securityProperties.getPublicKey()));

        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("提交状态未确认，请使用原账号重试");
        assertThat(service.register(dto)).isEqualTo("application-recovered");
        verify(systemServiceClient, never()).compensateOpenVendorAccount(any());
    }

    @Test
    void sensitiveRegistrationFieldsAreExcludedFromDtoStrings() {
        VendorPublicRegisterDTO request = request();
        request.setEncryptedPassword("ciphertext-marker");
        request.setPlainPassword("plaintext-marker");
        OpenVendorAccountCreateDTO account = new OpenVendorAccountCreateDTO();
        account.setPassword("plaintext-marker");

        assertThat(request.toString()).doesNotContain("ciphertext-marker");
        assertThat(request.toString()).doesNotContain("plaintext-marker");
        assertThat(account.toString()).doesNotContain("plaintext-marker");
    }

    private static VendorPublicRegisterDTO request() {
        VendorPublicRegisterDTO dto = new VendorPublicRegisterDTO();
        dto.setName("测试厂商");
        dto.setQualificationNo("USCC123456789");
        dto.setContactName("张三");
        dto.setContactPhone("13800000000");
        dto.setPhone("13900000000");
        dto.setEmail("vendor@example.com");
        dto.setUsername("vendor_user");
        dto.setCaptchaCode("ABCD");
        dto.setCaptchaUuid("uuid");
        return dto;
    }
}
