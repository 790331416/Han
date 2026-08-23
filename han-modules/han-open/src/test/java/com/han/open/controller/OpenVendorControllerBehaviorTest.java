package com.han.open.controller;

import com.han.common.core.constant.Constants;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.vo.VendorDetailVO;
import com.han.open.service.OpenVendorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenVendorController 行为测试：覆盖 list / getDetail / listMyVendors / updateStatus
 * 四个 handler 的正例与负例（纯单元测试，只 mock 协作的 service）。
 *
 * <p>注意：类上的 {@code @AdminAuth} 与各 handler 上的 {@code @PreAuthorize} 属于 AOP 权限切面，
 * 纯单元测试不会触发这些注解逻辑，此处仅覆盖 controller 方法体内的业务分支。</p>
 */
class OpenVendorControllerBehaviorTest {

    private final OpenVendorService vendorService = mock(OpenVendorService.class);
    private final OpenVendorController controller = new OpenVendorController(vendorService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void listReturnsPagedVendorsAndForwardsQueryParams() {
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(1L);
        vendor.setName("测试厂商");
        vendor.setStatus(4);
        PageResult<OpenVendorPo> page = PageResult.of(List.of(vendor), 1L, 1, 10);
        when(vendorService.listPage("测试", 4, 1, 10)).thenReturn(page);

        R<PageResult<OpenVendorPo>> response = controller.list("测试", 4, 1, 10);

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getMsg()).isEqualTo("操作成功");
        assertThat(response.getData()).isSameAs(page);
        verify(vendorService).listPage("测试", 4, 1, 10);
    }

    @Test
    void getDetailReturnsVendorDetail() {
        VendorDetailVO detail = new VendorDetailVO();
        detail.setId(100L);
        detail.setName("测试厂商");
        when(vendorService.getDetail(100L)).thenReturn(detail);

        R<VendorDetailVO> response = controller.getDetail(100L);

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).isSameAs(detail);
        verify(vendorService).getDetail(100L);
    }

    @Test
    void getDetailPropagatesBusinessExceptionWhenVendorMissing() {
        when(vendorService.getDetail(999L)).thenThrow(new BusinessException("厂商不存在"));

        assertThatThrownBy(() -> controller.getDetail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("厂商不存在");
    }

    @Test
    void listMyVendorsThrowsWhenUserContextMissing() {
        SecurityContextHolder.clear();

        assertThatThrownBy(() -> controller.listMyVendors())
                .isInstanceOf(BusinessException.class)
                .hasMessage("获取当前登录用户信息失败");
    }

    @Test
    void listMyVendorsReturnsCurrentUserVendorList() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(42L).tenantId(99L).build());
        OpenVendorPo vendor = new OpenVendorPo();
        vendor.setId(100L);
        vendor.setName("我的厂商");
        when(vendorService.listByUserId(42L)).thenReturn(List.of(vendor));

        R<List<OpenVendorPo>> response = controller.listMyVendors();

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).containsExactly(vendor);
        verify(vendorService).listByUserId(42L);
    }

    @Test
    void updateStatusDelegatesToService() {
        when(vendorService.updateStatus(100L, 6, "暂停合作")).thenReturn(true);

        R<Void> response = controller.updateStatus(100L, 6, "暂停合作");

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getMsg()).isEqualTo("操作成功");
        assertThat(response.getData()).isNull();
        verify(vendorService).updateStatus(100L, 6, "暂停合作");
    }

    @Test
    void updateStatusPropagatesBusinessExceptionFromService() {
        when(vendorService.updateStatus(100L, 6, null))
                .thenThrow(new BusinessException("厂商状态转换不合法"));

        assertThatThrownBy(() -> controller.updateStatus(100L, 6, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("厂商状态转换不合法");
    }
}
