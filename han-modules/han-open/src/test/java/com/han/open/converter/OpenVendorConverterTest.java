package com.han.open.converter;

import com.han.open.domain.po.OpenVendorApplicationPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.vo.OpenVendorApplicationAdminVO;
import com.han.open.domain.vo.VendorApplicationVO;
import com.han.open.domain.vo.VendorDetailVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenVendorConverterTest {

    @Test
    void mapsApplicationAndVendorFieldsExplicitly() {
        VendorApplicationVO application = new VendorApplicationVO();
        application.setName("测试厂商");
        application.setQualificationNo("USCC-001");
        application.setContactName("联系人");
        application.setContactPhone("13800000000");

        OpenVendorPo vendor = OpenVendorConverter.toVendorPo(application);
        assertThat(vendor.getName()).isEqualTo("测试厂商");
        assertThat(vendor.getQualificationNo()).isEqualTo("USCC-001");
        assertThat(vendor.getContactPhone()).isEqualTo("13800000000");
        assertThat(vendor.getTenantId()).isNull();
        assertThat(vendor.getStatus()).isNull();

        OpenVendorApplicationPo source = new OpenVendorApplicationPo();
        source.setId(7L);
        source.setVendorId(8L);
        source.setApplicationNo("VA-001");
        source.setStatus(1);
        OpenVendorApplicationAdminVO target = OpenVendorConverter.toApplicationAdminVO(source);
        assertThat(target.getId()).isEqualTo(7L);
        assertThat(target.getApplicationId()).isEqualTo(7L);
        assertThat(target.getVendorId()).isEqualTo(8L);
        assertThat(target.getApplicationNo()).isEqualTo("VA-001");
    }

    @Test
    void mapsDetailWithoutExposingPersistenceFields() {
        OpenVendorPo source = new OpenVendorPo();
        source.setId(9L);
        source.setName("测试厂商");
        source.setTenantId(99L);
        source.setCreateBy(42L);

        VendorDetailVO target = OpenVendorConverter.toDetailVO(source, java.util.List.of(), java.util.List.of());

        assertThat(target.getId()).isEqualTo(9L);
        assertThat(target.getName()).isEqualTo("测试厂商");
        assertThat(target.getUsers()).isEmpty();
        assertThat(target.getApps()).isEmpty();
        assertThat(target.getClass().getDeclaredFields()).extracting("name")
                .doesNotContain("tenantId", "createBy", "delFlag");
    }
}
