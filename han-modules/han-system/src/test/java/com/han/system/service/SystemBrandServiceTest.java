package com.han.system.service;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.api.file.FileServiceClient;
import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import com.han.system.domain.dto.SystemBrandDto;
import com.han.system.domain.po.SysConfigPo;
import com.han.system.domain.vo.SystemBrandVo;
import com.han.system.mapper.SysConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemBrandServiceTest {

    @Mock
    private SysConfigMapper configMapper;

    @Mock
    private FileServiceClient fileServiceClient;

    @TempDir
    Path tempDir;

    private SystemBrandService service;

    @BeforeEach
    void setUp() {
        service = new SystemBrandService(configMapper, fileServiceClient, tempDir.toString());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void returnsSafeDefaultsWhenGlobalBrandHasNotBeenConfigured() {
        when(configMapper.selectOne(any())).thenReturn(null);

        SystemBrandVo brand = service.getBrand();

        assertThat(brand.fullName()).isEqualTo("HAN Cloud");
        assertThat(brand.shortName()).isEqualTo("HAN");
        assertThat(brand.displayName()).isEqualTo("HAN Cloud");
        assertThat(brand.loginSubtitle()).isEqualTo("企业级多租户微服务平台");
        assertThat(brand.logoUrl()).isEmpty();
        assertThat(service.getSettings().allowInsecureVendorRegistration()).isFalse();
    }

    @Test
    void keepsAnExplicitlyBlankLoginSubtitleAndWritesOnlyGlobalBrandRecords() {
        when(configMapper.selectOne(any())).thenReturn(null);

        SystemBrandDto form = new SystemBrandDto();
        form.setFullName("巴蜀云校");
        form.setShortName("巴蜀");
        form.setDisplayMode(SystemBrandService.DISPLAY_SHORT_NAME);
        form.setLoginSubtitle("   ");

        SystemBrandVo saved = service.updateBrand(form);

        assertThat(saved.displayName()).isEqualTo("巴蜀");
        assertThat(saved.loginSubtitle()).isEmpty();
        ArgumentCaptor<SysConfigPo> record = ArgumentCaptor.forClass(SysConfigPo.class);
        verify(configMapper, org.mockito.Mockito.times(4)).insert(record.capture());
        assertThat(record.getAllValues())
                .extracting(SysConfigPo::getTenantId)
                .containsOnly(0L);
        assertThat(record.getAllValues())
                .extracting(SysConfigPo::getConfigValue)
                .contains("巴蜀云校", "巴蜀", SystemBrandService.DISPLAY_SHORT_NAME, "");
    }

    @Test
    void storesHttpVendorRegistrationCompatibilityAsProtectedGlobalSetting() {
        when(configMapper.selectOne(any())).thenReturn(null);
        SystemBrandDto form = new SystemBrandDto();
        form.setFullName("巴蜀云校");
        form.setShortName("巴蜀");
        form.setDisplayMode(SystemBrandService.DISPLAY_SHORT_NAME);
        form.setLoginSubtitle("");
        form.setAllowInsecureVendorRegistration(true);

        service.updateBrand(form);

        ArgumentCaptor<SysConfigPo> record = ArgumentCaptor.forClass(SysConfigPo.class);
        verify(configMapper, org.mockito.Mockito.times(5)).insert(record.capture());
        assertThat(record.getAllValues())
                .extracting(SysConfigPo::getConfigKey, SysConfigPo::getConfigValue)
                .contains(org.assertj.core.groups.Tuple.tuple(
                        "sys.open.vendorRegistration.allowInsecureHttp", "true"));
        assertThat(SystemBrandService.isReservedConfigKey("sys.open.vendorRegistration.allowInsecureHttp")).isTrue();
    }

    @Test
    void rejectsReservedBrandKeysWithoutBrandEditPermission() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).build());

        assertThatThrownBy(() -> service.assertGenericConfigMutationAllowed(null, "sys.brand.fullName"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户没有修改系统设置权限");
    }

    @Test
    void permitsReservedBrandKeysWithBrandEditPermission() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L)
                .permissions(Set.of("system:brand:edit"))
                .build());

        service.assertGenericConfigMutationAllowed(null, "sys.brand.fullName");
        assertThat(SystemBrandService.isReservedConfigKey("sys.brand.fullName")).isTrue();
        assertThat(SystemBrandService.isReservedConfigKey("sys.config.example")).isFalse();
    }

    @Test
    void storesAValidLogoAndRejectsAFileWithOnlyAnImageMimeType() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        FileDTO uploaded = new FileDTO(100L, "logo.png", "/file/public/100");
        when(fileServiceClient.uploadInternal(any(), org.mockito.ArgumentMatchers.eq("brand_logo"),
                org.mockito.ArgumentMatchers.eq("PUBLIC"), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(R.ok(uploaded));
        service.updateLogo(new MockMultipartFile("file", "logo.png", "image/png", png));

        SysConfigPo logoConfig = new SysConfigPo();
        logoConfig.setConfigValue("100");
        when(configMapper.selectOne(any())).thenReturn(logoConfig);
        FileBase64DTO logo = new FileBase64DTO();
        logo.setBase64(java.util.Base64.getEncoder().encodeToString(png));
        when(fileServiceClient.loadBase64(100L)).thenReturn(R.ok(logo));

        assertThat(service.getLogo()).isPresent();
        assertThat(service.getLogo().orElseThrow().contentType()).isEqualTo("image/png");

        MockMultipartFile disguised = new MockMultipartFile(
                "file", "logo.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.updateLogo(disguised))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Logo仅支持PNG、JPG或WebP格式");
    }
}
